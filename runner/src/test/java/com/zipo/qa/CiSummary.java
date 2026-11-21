package com.zipo.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Adds markdown to the GitHub Actions run summary page. Does nothing outside GitHub Actions. */
public final class CiSummary {

    private CiSummary() {}

    public static void append(String markdown) {
        String file = System.getenv("GITHUB_STEP_SUMMARY");
        if (file == null || file.isBlank()) {
            return;
        }
        try {
            Files.writeString(Path.of(file), markdown + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // the summary is a convenience; the same note is in the test's artifact folder
        }
    }
}
