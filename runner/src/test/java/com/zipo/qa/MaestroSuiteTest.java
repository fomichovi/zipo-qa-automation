package com.zipo.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * One JUnit test per Maestro flow listed in flows/config.yaml.
 * Adding a test = adding a YAML file; no Java changes needed.
 */
class MaestroSuiteTest {

    @BeforeAll
    static void prepareDevice() {
        MaestroRunner.ensureAvailable();
        Device.ensureReady();
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
        MaestroRunner.Result result = MaestroRunner.run(flow);
        if (!result.passed()) {
            fail(flow.name() + " failed. Log: " + result.reportDir().resolve("maestro.log")
                    + "\n" + tail(result.output(), 25));
        }
    }

    /** Last lines of Maestro's output: enough to see the failing step without opening the log. */
    private static String tail(String text, int lines) {
        String[] all = text.strip().split("\\R");
        int from = Math.max(0, all.length - lines);
        return String.join("\n", List.of(all).subList(from, all.length));
    }
}
