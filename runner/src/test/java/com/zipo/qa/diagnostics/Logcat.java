package com.zipo.qa.diagnostics;

import com.zipo.qa.Device;
import com.zipo.qa.Shell;
import java.time.Duration;

/**
 * Captures the device log for one test only: mark the device time when the test starts,
 * then read everything logged since then. Logcat is never cleared, so no context is lost
 * and there's no race with the device.
 */
public final class Logcat {

    /** All buffers, so crashes ("crash"), ANRs ("events", "system") and app logs ("main") are included. */
    private static final String BUFFERS = "main,system,crash,events";

    private Logcat() {}

    /** Device time now, as epoch seconds with milliseconds (the format "logcat -T" accepts). */
    public static String markStart() {
        Shell.Result r = Device.adb(Duration.ofSeconds(15), "shell", "echo $EPOCHREALTIME");
        String epoch = r.output().trim();
        if (!r.ok() || !epoch.matches("\\d+\\.\\d+")) {
            throw new IllegalStateException("Cannot read device time: " + r.output());
        }
        int dot = epoch.indexOf('.');
        return epoch.substring(0, Math.min(epoch.length(), dot + 4)); // keep milliseconds
    }

    /**
     * Full device log (all apps and the system) since {@code start}. Only the test tool's own
     * "Maestro" tag is silenced: its accessibility-tree dumps were ~90% of the file (3 MB for a
     * 30 s test) and say nothing about the app.
     */
    public static String since(String start) {
        Shell.Result r = Device.adb(Duration.ofMinutes(1),
                "logcat", "-d", "-b", BUFFERS, "-v", "threadtime", "-T", start, "*:V", "Maestro:S");
        return r.output();
    }
}
