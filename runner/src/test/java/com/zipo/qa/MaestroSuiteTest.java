package com.zipo.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.zipo.qa.ai.FailureTriage;
import com.zipo.qa.ai.TriageInput;
import com.zipo.qa.diagnostics.CrashDetector;
import com.zipo.qa.diagnostics.Logcat;
import com.zipo.qa.diagnostics.Screenshot;
import com.zipo.qa.diagnostics.TestArtifacts;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * One JUnit test per Maestro flow listed in flows/config.yaml.
 * Adding a test = adding a YAML file; no Java changes needed.
 *
 * A test fails if a Maestro step fails OR the app crashed / stopped responding (ANR)
 * while it ran, even when every UI step passed. On failure the test's folder gets a
 * screenshot, the full device logcat for that test, and Maestro's debug output.
 */
class MaestroSuiteTest {

    /** How long a FAILED test waits for a pending ANR report (Android reports ~5 s after input). */
    private static final Duration ANR_GRACE = Duration.ofSeconds(10);

    @BeforeAll
    static void prepareDevice() {
        MaestroRunner.ensureAvailable();
        Device.ensureReady();
        Device.waitUntilIdle(Duration.ofMinutes(5));
        Device.install(Config.apk());
        Device.warmUp(Config.appId(), Duration.ofSeconds(60));
    }

    @TestFactory
    Stream<DynamicTest> flows() {
        List<Flow> flows = Flow.discover(Config.flowsDir()).stream()
                .filter(flow -> flow.matches(Config.tags()))
                .toList();
        assertFalse(flows.isEmpty(), "No flows found for tags " + Config.tags());

        return flows.stream().map(flow -> DynamicTest.dynamicTest(flow.name(), flow.file().toUri(), () -> run(flow)));
    }

    private static void run(Flow flow) {
        TestArtifacts artifacts = new TestArtifacts(Config.reportsDir(), flow.id());
        String start = Logcat.markStart();

        MaestroRunner.Result result = MaestroRunner.run(flow, artifacts);

        String logcat = Logcat.since(start);
        Optional<CrashDetector.Problem> problem = CrashDetector.find(logcat, Config.appId());
        if (result.passed() && problem.isEmpty()) {
            artifacts.keepOnlyLog();
            return;
        }

        // Screenshot first: it should show the screen as close to the failure as possible.
        boolean screenshot = Screenshot.save(artifacts.screenshot());

        // A frozen app often fails the UI step BEFORE Android reports the ANR (~5 s after the
        // unanswered input). Only on failure, keep checking briefly so the real cause is named.
        long deadline = System.nanoTime() + ANR_GRACE.toNanos();
        while (problem.isEmpty() && System.nanoTime() < deadline) {
            pause(Duration.ofMillis(500)); // polling interval, not a fixed wait
            logcat = Logcat.since(start);
            problem = CrashDetector.find(logcat, Config.appId());
        }

        artifacts.write(artifacts.logcat(), logcat);
        artifacts.dropMaestroLogcatCopy();

        // Optional, advisory: never changes the result below.
        FailureTriage.Outcome triage = FailureTriage.analyse(new TriageInput(
                flow.name(), Config.appId(), readQuietly(flow.file()), tail(result.output(), 60),
                problem.map(CrashDetector.Problem::headline).orElse(null), logcat,
                screenshot ? artifacts.screenshot() : null));
        if (triage.status() != FailureTriage.Status.SKIPPED) {
            String md = FailureTriage.markdown(flow.name(), triage);
            artifacts.write(artifacts.aiTriage(), md);
            CiSummary.append(md);
        }

        fail(failureMessage(flow, result, problem, artifacts, screenshot) + "\n" + triage.oneLine());
    }

    private static String readQuietly(java.nio.file.Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            return "(could not read " + file + ")";
        }
    }

    /** Cause first (crash/ANR), then the failing UI step, then where to look. */
    private static String failureMessage(Flow flow, MaestroRunner.Result result,
                                         Optional<CrashDetector.Problem> problem,
                                         TestArtifacts artifacts, boolean screenshot) {
        StringBuilder msg = new StringBuilder(flow.name()).append(" failed.\n");
        problem.ifPresent(p -> msg.append(p.headline()).append('\n')
                .append(indent(p.excerpt())).append('\n'));
        msg.append(result.passed()
                ? "UI steps: all passed (failed only because of the problem above).\n"
                : "UI steps:\n" + indent(tail(result.output(), 20)) + '\n');
        msg.append("Artifacts: ").append(artifacts.dir()).append('\n')
                .append("  logcat.txt, maestro.log, maestro-debug/")
                .append(screenshot ? ", failure.png" : " (screenshot could not be taken)");
        return msg.toString();
    }

    /** Last lines of Maestro's output: enough to see the failing step without opening the log. */
    private static String tail(String text, int lines) {
        // Drop JVM warnings printed by the Maestro CLI itself; they're not test output.
        String[] all = text.strip().lines().filter(l -> !l.startsWith("WARNING:")).toArray(String[]::new);
        int from = Math.max(0, all.length - lines);
        return String.join("\n", List.of(all).subList(from, all.length));
    }

    private static void pause(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String indent(String text) {
        return text.lines().map(l -> "  " + l).reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
