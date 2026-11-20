package com.zipo.qa.diagnostics;

import com.zipo.qa.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Saves the current device screen as a PNG, whatever state the test ended in. */
public final class Screenshot {

    private Screenshot() {}

    /** Returns true if the file was written. Never throws: diagnostics must not hide the real failure. */
    public static boolean save(Path file) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Config.adbBin());
        Config.device().ifPresent(serial -> {
            cmd.add("-s");
            cmd.add(serial);
        });
        cmd.addAll(List.of("exec-out", "screencap", "-p"));
        try {
            // Binary output: write stdout straight to the file instead of reading it as text.
            Process p = new ProcessBuilder(cmd).redirectOutput(file.toFile()).redirectErrorStream(false).start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0 && Files.size(file) > 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
