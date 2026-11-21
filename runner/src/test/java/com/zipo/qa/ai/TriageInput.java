package com.zipo.qa.ai;

import java.nio.file.Files;
import java.nio.file.Path;

/** Everything the runner already collected about one failed test. */
public record TriageInput(
        String testName,
        String appId,
        String flowYaml,
        String maestroTail,
        String crashHeadline, // null when the detector found nothing
        String logcat,
        Path screenshot) { // may be missing

    public boolean hasScreenshot() {
        return screenshot != null && Files.isRegularFile(screenshot);
    }
}
