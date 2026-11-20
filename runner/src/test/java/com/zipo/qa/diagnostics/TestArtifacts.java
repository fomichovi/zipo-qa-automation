package com.zipo.qa.diagnostics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Folder layout for one test's artifacts:
 * <pre>
 *   &lt;reports&gt;/&lt;flow id&gt;/
 *     maestro.log     always
 *     failure.png     on failure: the screen when the test ended
 *     logcat.txt      on failure: full device log (all apps) for this test
 *     maestro-debug/  on failure: Maestro's step screenshots and UI tree (its duplicate logcat removed)
 * </pre>
 * Passing tests keep only maestro.log, so CI artifacts stay small.
 */
public final class TestArtifacts {

    private final Path dir;

    public TestArtifacts(Path reportsDir, String flowId) {
        this.dir = reportsDir.resolve(flowId);
        deleteRecursively(dir); // never mix artifacts from a previous run
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + dir, e);
        }
    }

    public Path dir() { return dir; }
    public Path maestroLog() { return dir.resolve("maestro.log"); }
    public Path screenshot() { return dir.resolve("failure.png"); }
    public Path logcat() { return dir.resolve("logcat.txt"); }
    public Path maestroDebug() { return dir.resolve("maestro-debug"); }

    /** Called when the test passed: drop everything except maestro.log. */
    public void keepOnlyLog() {
        deleteRecursively(maestroDebug());
    }

    /**
     * Called when the test failed: Maestro saves its own device-logcat.txt in maestro-debug/.
     * It duplicates logcat.txt (~3 MB) and lacks the "events" buffer where ANRs are logged,
     * so keep only ours.
     */
    public void dropMaestroLogcatCopy() {
        if (!Files.exists(maestroDebug())) {
            return;
        }
        try (Stream<Path> walk = Files.walk(maestroDebug())) {
            walk.filter(p -> p.getFileName().toString().equals("device-logcat.txt"))
                    .forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // not critical: only a duplicate log stays behind
        }
    }

    public void write(Path file, String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            // diagnostics must never hide the real test result
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot clean " + path, e);
        }
    }
}
