package com.zipo.qa.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for AI failure triage. No device and no API call: they check everything around
 * the call (when it runs, what is sent, how the answer is shown). Run with ./gradlew unitTest.
 */
@Tag("framework")
class FailureTriageTest {

    private static final String APP = "com.zipo.interviewproject";

    private static TriageInput input(String crash, String logcat, Path screenshot) {
        return new TriageInput("TC-01 Paywall content", APP,
                "appId: com.zipo.interviewproject\nname: \"TC-01 Paywall content\"\n---\n- assertVisible: \"Premium\"",
                "Assert that \"Premium Plus\", id: paywall_title is visible... FAILED",
                crash, logcat, screenshot);
    }

    // --- when triage runs ---

    @Test
    void enabledOnlyWithACredential() {
        assertFalse(FailureTriage.enabled(Map.of()));
        assertFalse(FailureTriage.enabled(Map.of("ANTHROPIC_API_KEY", "  ")));
        assertTrue(FailureTriage.enabled(Map.of("ANTHROPIC_API_KEY", "sk-ant-test")));
        assertTrue(FailureTriage.enabled(Map.of("ANTHROPIC_AUTH_TOKEN", "token")));
    }

    @Test
    void skippedCleanlyWithoutAKey() {
        assumeFalse(FailureTriage.enabled(System.getenv()), "a real key is set; this test checks the no-key path");
        FailureTriage.Outcome outcome = FailureTriage.analyse(input(null, "", null));
        assertEquals(FailureTriage.Status.SKIPPED, outcome.status());
        assertEquals("AI triage: skipped (no ANTHROPIC_API_KEY)", outcome.oneLine());
    }

    // --- what is sent ---

    @Test
    void promptContainsTheEvidence() {
        String text = TriagePrompt.userText(input("App crashed: java.lang.IllegalStateException: boom", "", null));
        assertTrue(text.contains("Failed test: TC-01 Paywall content"));
        assertTrue(text.contains("- assertVisible: \"Premium\""), "flow definition");
        assertTrue(text.contains("paywall_title is visible... FAILED"), "failing step");
        assertTrue(text.contains("App crashed: java.lang.IllegalStateException: boom"), "detector result");
        assertTrue(text.contains("No screenshot is available"));
    }

    @Test
    void promptSaysWhenTheDetectorFoundNothing() {
        assertTrue(TriagePrompt.userText(input(null, "", null)).contains("No crash or ANR of the app detected."));
    }

    @Test
    void logcatExcerptKeepsProblemsAndAppLinesOnly() {
        String logcat = """
                11-20 16:21:00.100   612   640 I ActivityManager: Start proc 5556:com.zipo.interviewproject/u0a232
                11-20 16:21:00.200  1913  1950 I Finsky  : routine info from another app
                11-20 16:21:00.300  1913  1950 D Finsky  : debug noise
                11-20 16:21:00.400   612   771 W WindowManager: system warning
                11-20 16:21:00.500  5556  5556 E AndroidRuntime: FATAL EXCEPTION: main
                """;
        String excerpt = TriagePrompt.logcatExcerpt(logcat, APP);
        assertTrue(excerpt.contains("Start proc 5556:com.zipo.interviewproject"), "app line kept");
        assertTrue(excerpt.contains("system warning"), "warning kept");
        assertTrue(excerpt.contains("FATAL EXCEPTION"), "error kept");
        assertFalse(excerpt.contains("routine info"), "other app's info dropped");
        assertFalse(excerpt.contains("debug noise"), "debug dropped");
    }

    @Test
    void logcatExcerptKeepsOnlyTheLastLines() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            log.append("11-20 16:21:00.000   612   640 E Tag: line ").append(i).append('\n');
        }
        String excerpt = TriagePrompt.logcatExcerpt(log.toString(), APP);
        assertEquals(TriagePrompt.MAX_LOGCAT_LINES, excerpt.lines().count());
        assertTrue(excerpt.endsWith("line 499"), "the end of the log is where the failure is");
    }

    @Test
    void longSectionsAreCutFromTheStart() {
        String hugeYaml = "x".repeat(TriagePrompt.MAX_SECTION_CHARS * 2) + "THE END";
        TriageInput in = new TriageInput("T", APP, hugeYaml, "tail", null, "", null);
        String text = TriagePrompt.userText(in);
        assertTrue(text.contains("[... earlier lines cut ...]"));
        assertTrue(text.contains("THE END"));
        assertTrue(text.length() < TriagePrompt.MAX_SECTION_CHARS * 5, "request stays bounded");
    }

    @Test
    void requestUsesTheAgreedModelSettings(@TempDir Path tmp) throws IOException {
        Path png = tmp.resolve("failure.png");
        Files.write(png, new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        MessageCreateParams params = FailureTriage.request(input(null, "", png)).rawParams();

        assertEquals("claude-opus-5-5", params.model().asString());
        assertEquals(BetaOutputConfig.Effort.LOW, params.outputConfig().flatMap(BetaOutputConfig::effort).orElseThrow());
        assertTrue(params.outputConfig().orElseThrow().format().isPresent(), "structured output schema set");
        assertTrue(params.fallbacks().isPresent(), "refusal fallback enabled");
        assertTrue(params.betas().orElseThrow().contains(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01));
        assertTrue(params.system().isPresent());

        List<BetaContentBlockParam> blocks = params.messages().get(0).content().orElseThrow().asContentBlockParams();
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).isText());
        assertTrue(blocks.get(1).isImage(), "screenshot attached");
    }

    @Test
    void requestWorksWithoutAScreenshot() {
        MessageCreateParams params = FailureTriage.request(input(null, "", null)).rawParams();
        List<BetaContentBlockParam> blocks = params.messages().get(0).content().orElseThrow().asContentBlockParams();
        assertEquals(1, blocks.size());
    }

    // --- how the answer is shown ---

    @Test
    void markdownShowsTheResultAsAdvisory() {
        TriageResult r = new TriageResult(TriageResult.Category.TEST_ISSUE, TriageResult.Confidence.MEDIUM,
                "The screen shows 'Premium' but the test expects 'Premium Plus'.",
                List.of("step 'Premium Plus is visible' FAILED", "screenshot title reads 'Premium'"),
                "Check whether the expected title in tc01_content.yaml is outdated.");
        FailureTriage.Outcome outcome = new FailureTriage.Outcome(FailureTriage.Status.DONE, r, "model claude-opus-5-5");

        String md = FailureTriage.markdown("TC-01 Paywall content", outcome);
        assertTrue(md.contains("**TEST_ISSUE** (medium confidence)"));
        assertTrue(md.contains("- screenshot title reads 'Premium'"));
        assertTrue(md.contains("Advisory only"));
        assertTrue(outcome.oneLine().startsWith("AI triage (advisory): TEST_ISSUE (medium confidence)"));
    }

    @Test
    void markdownForUnavailableTriage() {
        FailureTriage.Outcome outcome = FailureTriage.Outcome.unavailable("RateLimitException: slow down");
        assertEquals("### AI triage: T\n\n_AI triage: unavailable (RateLimitException: slow down)_\n",
                FailureTriage.markdown("T", outcome));
    }
}
