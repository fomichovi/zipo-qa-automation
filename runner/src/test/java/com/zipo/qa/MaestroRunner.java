package com.zipo.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Runs one Maestro flow and keeps its output in the flow's report folder. */
public final class MaestroRunner {

    private static final Duration FLOW_TIMEOUT = Duration.ofMinutes(5);

    public record Result(boolean passed, String output, Path reportDir) {}

    private MaestroRunner() {}

    /** Fails fast with one clear message if the Maestro CLI can't be started. */
    public static void ensureAvailable() {
        Shell.Result version = Shell.run(List.of(Config.maestroBin(), "--version"), Duration.ofMinutes(1));
        if (!version.ok()) {
            throw new IllegalStateException("Maestro CLI not available (" + Config.maestroBin() + "). "
                    + "Install it: https://maestro.mobile.dev, or set MAESTRO_BIN.\n" + version.output());
        }
    }

    public static Result run(Flow flow) {
        Path reportDir = Config.reportsDir().resolve(flow.id());
        try {
            Files.createDirectories(reportDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + reportDir, e);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(Config.maestroBin());
        Config.device().ifPresent(serial -> {
            cmd.add("--device");
            cmd.add(serial);
        });
        cmd.add("test");
        cmd.add(flow.file().toString());

        Shell.Result result = Shell.run(cmd, FLOW_TIMEOUT);
        writeQuietly(reportDir.resolve("maestro.log"), result.output());
        return new Result(result.ok(), result.output(), reportDir);
    }

    private static void writeQuietly(Path file, String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException ignored) {
            // the output is also attached to the test failure message
        }
    }
}
