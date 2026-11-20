package com.zipo.qa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs an external command and captures its combined output. */
public final class Shell {

    public record Result(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    private Shell() {}

    public static Result run(List<String> command, Duration timeout) {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().put("MAESTRO_CLI_NO_ANALYTICS", "1");
        pb.environment().put("MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED", "true");
        try {
            Process process = pb.start();
            // Read output on a separate thread so a chatty process can't block on a full pipe.
            StringBuilder out = new StringBuilder();
            Thread reader = new Thread(() -> {
                try {
                    out.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // process was killed; keep what we have
                }
            });
            reader.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                reader.join(5_000);
                return new Result(-1, out + "\n[runner] Timed out after " + timeout.toSeconds() + " s: " + command);
            }
            reader.join();
            return new Result(process.exitValue(), out.toString());
        } catch (IOException e) {
            return new Result(-1, "[runner] Could not start " + command.get(0) + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "[runner] Interrupted: " + command);
        }
    }
}
