package com.zipo.qa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** All run settings in one place. Values come from Gradle (-P...) or environment variables. */
public final class Config {

    private Config() {}

    public static Path flowsDir() {
        return Path.of(required("qa.flowsDir"));
    }

    public static Path apk() {
        return Path.of(required("qa.apk"));
    }

    public static String appId() {
        return required("qa.appId");
    }

    public static Path reportsDir() {
        return Path.of(required("qa.reportsDir"));
    }

    /** Optional adb serial; empty means "the only connected device". */
    public static Optional<String> device() {
        return Optional.ofNullable(System.getProperty("qa.device")).filter(s -> !s.isBlank());
    }

    /** Optional tag filter, e.g. -Ptags=P0 or -Ptags=paywall,lifecycle. Empty means all tests. */
    public static Set<String> tags() {
        String raw = System.getProperty("qa.tags", "");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /** Maestro CLI: $MAESTRO_BIN, then ~/.maestro/bin/maestro, then "maestro" on PATH. */
    public static String maestroBin() {
        String fromEnv = System.getenv("MAESTRO_BIN");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        Path home = Path.of(System.getProperty("user.home"), ".maestro", "bin", "maestro");
        return Files.isExecutable(home) ? home.toString() : "maestro";
    }

    /** adb: $ANDROID_HOME / $ANDROID_SDK_ROOT, then the default macOS SDK path, then "adb" on PATH. */
    public static String adbBin() {
        for (String var : new String[] {"ANDROID_HOME", "ANDROID_SDK_ROOT"}) {
            String sdk = System.getenv(var);
            if (sdk != null && !sdk.isBlank()) {
                Path adb = Path.of(sdk, "platform-tools", "adb");
                if (Files.isExecutable(adb)) {
                    return adb.toString();
                }
            }
        }
        Path mac = Path.of(System.getProperty("user.home"), "Library", "Android", "sdk", "platform-tools", "adb");
        return Files.isExecutable(mac) ? mac.toString() : "adb";
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing system property " + key + " (run the suite through ./gradlew test)");
        }
        return value;
    }
}
