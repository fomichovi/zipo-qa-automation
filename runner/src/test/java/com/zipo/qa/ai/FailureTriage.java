package com.zipo.qa.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Optional AI failure triage: for a FAILED test, asks Claude to classify the likely cause
 * (app bug / test issue / environment) from the evidence the runner already collected.
 *
 * Rules: runs only on failures, only when ANTHROPIC_API_KEY is set, never changes pass/fail,
 * and never throws: any problem becomes an "unavailable" outcome.
 */
public final class FailureTriage {

    static final String MODEL = "claude-opus-5-5";
    private static final long MAX_TOKENS = 4_000; // short structured answer (thinking included)
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    public enum Status { DONE, SKIPPED, UNAVAILABLE }

    public record Outcome(Status status, TriageResult result, String note) {
        static Outcome skipped(String why) { return new Outcome(Status.SKIPPED, null, why); }
        static Outcome unavailable(String why) { return new Outcome(Status.UNAVAILABLE, null, why); }

        /** One line for the JUnit failure message. */
        public String oneLine() {
            return switch (status) {
                case DONE -> "AI triage (advisory): " + result.category() + " (" + result.confidence().name().toLowerCase()
                        + " confidence) - " + result.summary();
                case SKIPPED -> "AI triage: skipped (" + note + ")";
                case UNAVAILABLE -> "AI triage: unavailable (" + note + ")";
            };
        }
    }

    private FailureTriage() {}

    /** Triage is on only when an API credential is present. */
    public static boolean enabled(Map<String, String> env) {
        return !env.getOrDefault("ANTHROPIC_API_KEY", "").isBlank()
                || !env.getOrDefault("ANTHROPIC_AUTH_TOKEN", "").isBlank();
    }

    public static Outcome analyse(TriageInput input) {
        if (!enabled(System.getenv())) {
            return Outcome.skipped("no ANTHROPIC_API_KEY");
        }
        try {
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .fromEnv()
                    .timeout(TIMEOUT)
                    .maxRetries(1)
                    .build();
            StructuredMessage<TriageResult> response = client.beta().messages().create(request(input));

            if (response.stopReason().map(BetaStopReason.REFUSAL::equals).orElse(false)) {
                return Outcome.unavailable("model declined the request");
            }
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> new Outcome(Status.DONE, text.text(), "model " + response.model().asString()))
                    .findFirst()
                    .orElseGet(() -> Outcome.unavailable("empty response"));
        } catch (RuntimeException e) {
            // Network, auth, rate limit, timeout, schema mismatch: triage is best-effort.
            return Outcome.unavailable(e.getClass().getSimpleName() + ": " + firstLine(e.getMessage()));
        }
    }

    static StructuredMessageCreateParams<TriageResult> request(TriageInput input) {
        List<BetaContentBlockParam> content = new ArrayList<>();
        content.add(BetaContentBlockParam.ofText(TriagePrompt.userText(input)));
        if (input.hasScreenshot()) {
            try {
                String png = Base64.getEncoder().encodeToString(Files.readAllBytes(input.screenshot()));
                content.add(BetaContentBlockParam.ofImage(BetaBase64ImageSource.builder()
                        .mediaType(BetaBase64ImageSource.MediaType.IMAGE_PNG)
                        .data(png)
                        .build()));
            } catch (IOException e) {
                // triage still works from the text evidence
            }
        }
        return MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(TriagePrompt.SYSTEM)
                // Structured output: the answer must match TriageResult. Low effort: short classification task.
                .outputConfig(TriageResult.class, BetaOutputConfig.Effort.LOW, JsonSchemaLocalValidation.YES)
                .addUserMessageOfContentBlockParams(content)
                // If a safety classifier declines, retry on the model Anthropic recommends instead of failing.
                .fallbacksDefault()
                .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
                .build();
    }

    /** Markdown note saved as ai-triage.md (and added to the CI run summary). */
    public static String markdown(String testName, Outcome outcome) {
        StringBuilder md = new StringBuilder("### AI triage: ").append(testName).append("\n\n");
        if (outcome.status() != Status.DONE) {
            return md.append("_").append(outcome.oneLine()).append("_\n").toString();
        }
        TriageResult r = outcome.result();
        md.append("**").append(r.category()).append("** (").append(r.confidence().name().toLowerCase())
                .append(" confidence): ").append(r.summary()).append("\n\n");
        if (r.evidence() != null && !r.evidence().isEmpty()) {
            md.append("Evidence:\n");
            r.evidence().forEach(e -> md.append("- ").append(e).append('\n'));
            md.append('\n');
        }
        md.append("Next step: ").append(r.nextStep()).append("\n\n")
                .append("_Advisory only, generated by ").append(outcome.note())
                .append(". The test result above comes from the test itself, not from this note._\n");
        return md.toString();
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "no details";
        }
        String line = s.strip().lines().findFirst().orElse("");
        return line.length() > 200 ? line.substring(0, 200) + "..." : line;
    }
}
