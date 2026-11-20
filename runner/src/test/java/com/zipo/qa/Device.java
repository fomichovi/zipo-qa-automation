package com.zipo.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** adb helpers: make sure a device is ready and the build under test is installed. */
public final class Device {

    private Device() {}

    public static void ensureReady() {
        Shell.Result state = adb(Duration.ofSeconds(15), "get-state");
        if (!state.ok() || !state.output().trim().equals("device")) {
            throw new IllegalStateException(
                    "No Android device ready (adb get-state: " + state.output().trim() + "). "
                            + "Start an emulator, or pass -Pdevice=<serial> when several are connected.");
        }
        Shell.Result boot = adb(Duration.ofSeconds(15), "shell", "getprop", "sys.boot_completed");
        if (!boot.output().trim().equals("1")) {
            throw new IllegalStateException("Device is connected but has not finished booting.");
        }
    }

    /**
     * Waits until the device's 1-minute load average is below its CPU core count.
     * Right after boot, Google system apps keep a Play Store emulator busy (load 10-18 for ~2 min),
     * which made the first tests flaky. Never fails: after {@code max} it logs and continues.
     */
    public static void waitUntilIdle(Duration max) {
        int cores = Math.max(1, parseInt(adb(Duration.ofSeconds(15), "shell", "nproc").output(), 2));
        long started = System.nanoTime();
        long deadline = started + max.toNanos();
        double load = load();
        while (load >= cores && System.nanoTime() < deadline) {
            try {
                Thread.sleep(5_000); // polling interval, not a fixed wait
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            load = load();
        }
        long waited = Duration.ofNanos(System.nanoTime() - started).toSeconds();
        if (load >= cores) {
            System.out.printf("[runner] WARNING: device still busy after %d s (load %.2f, %d cores); running anyway%n",
                    waited, load, cores);
        } else {
            System.out.printf("[runner] Device idle after %d s (load %.2f, %d cores)%n", waited, load, cores);
        }
    }

    private static double load() {
        String out = adb(Duration.ofSeconds(15), "shell", "cat", "/proc/loadavg").output().trim();
        try {
            return Double.parseDouble(out.split("\\s+")[0]);
        } catch (RuntimeException e) {
            return 0; // unknown: don't block the run
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void install(Path apk) {
        if (!Files.isRegularFile(apk)) {
            throw new IllegalStateException("APK not found: " + apk);
        }
        Shell.Result result = adb(Duration.ofMinutes(3), "install", "-r", "-t", apk.toString());
        if (!result.ok() || !result.output().contains("Success")) {
            throw new IllegalStateException("APK install failed:\n" + result.output());
        }
    }

    /**
     * Launches the app once after install and waits until it leaves its launcher activity
     * (Splash -> next screen), then stops it. The first start after an install is much slower
     * (code not yet optimised): it made the first test flaky, with Maestro reading an empty UI
     * tree while the app was already on screen. Warming up keeps that cost out of the tests.
     */
    public static void warmUp(String appId, Duration timeout) {
        Shell.Result resolved = adb(Duration.ofSeconds(15), "shell", "cmd", "package", "resolve-activity",
                "--brief", "-c", "android.intent.category.LAUNCHER", appId);
        String[] lines = resolved.output().strip().split("\\R");
        String launcher = lines[lines.length - 1].trim();
        if (!resolved.ok() || !launcher.startsWith(appId + "/")) {
            throw new IllegalStateException("Cannot find the launcher activity of " + appId + ":\n" + resolved.output());
        }

        adb(Duration.ofSeconds(30), "shell", "am", "start", "-W", "-n", launcher);
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (System.nanoTime() < deadline) {
                String resumed = resumedActivity();
                if (resumed.contains(appId + "/") && !resumed.contains(launcher)) {
                    return; // app reached its first real screen
                }
                Thread.sleep(250); // polling interval, not a fixed wait
            }
            throw new IllegalStateException("Warm-up: " + appId + " did not leave " + launcher + " within "
                    + timeout.toSeconds() + " s (resumed: " + resumedActivity() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            adb(Duration.ofSeconds(15), "shell", "am", "force-stop", appId);
        }
    }

    private static String resumedActivity() {
        Shell.Result r = adb(Duration.ofSeconds(15), "shell", "dumpsys", "activity", "activities");
        return r.output().lines()
                .filter(l -> l.contains("topResumedActivity") || l.contains("mResumedActivity"))
                .findFirst().orElse("").trim();
    }

    public static Shell.Result adb(Duration timeout, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Config.adbBin());
        Config.device().ifPresent(serial -> {
            cmd.add("-s");
            cmd.add(serial);
        });
        cmd.addAll(List.of(args));
        return Shell.run(cmd, timeout);
    }
}
