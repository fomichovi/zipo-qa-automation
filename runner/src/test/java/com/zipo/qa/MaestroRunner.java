package com.zipo.qa;

import com.zipo.qa.diagnostics.TestArtifacts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Runs one Maestro flow; its output and debug files go into the test's artifact folder. */
public final class MaestroRunner {

    private static final Duration FLOW_TIMEOUT = Duration.ofMinutes(5);

    public record Result(boolean passed, String output) {}

    private MaestroRunner() {}

    /** Fails fast with one clear message if the Maestro CLI can't be started. */
    public static void ensureAvailable() {
        Shell.Result version = Shell.run(List.of(Config.maestroBin(), "--version"), Duration.ofMinutes(1));
        if (!version.ok()) {
            throw new IllegalStateException("Maestro CLI not available (" + Config.maestroBin() + "). "
                    + "Install it: https://maestro.mobile.dev, or set MAESTRO_BIN.\n" + version.output());
        }
    }

    public static Result run(Flow flow, TestArtifacts artifacts) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Config.maestroBin());
        Config.device().ifPresent(serial -> {
            cmd.add("--device");
            cmd.add(serial);
        });
        cmd.add("test");
        cmd.add("--debug-output");
        cmd.add(artifacts.maestroDebug().toString());
        cmd.add("--flatten-debug-output");
        cmd.add(flow.file().toString());

        Shell.Result result = Shell.run(cmd, FLOW_TIMEOUT);
        artifacts.write(artifacts.maestroLog(), result.output());
        return new Result(result.ok(), result.output());
    }
}
