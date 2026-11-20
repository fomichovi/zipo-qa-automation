package com.zipo.qa.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds crashes and ANRs of ONE app in a logcat dump. Other apps' problems are ignored
 * (they stay visible in logcat.txt for context, but don't fail our tests).
 */
public final class CrashDetector {

    public enum Kind { CRASH, NATIVE_CRASH, ANR }

    public record Problem(Kind kind, String summary, String excerpt) {
        public String headline() {
            return switch (kind) {
                case CRASH -> "App crashed: " + summary;
                case NATIVE_CRASH -> "App crashed (native): " + summary;
                case ANR -> "App not responding (ANR): " + summary;
            };
        }
    }

    private static final int EXCERPT_LINES = 12;

    private CrashDetector() {}

    public static Optional<Problem> find(String logcat, String appId) {
        String[] lines = logcat.split("\\R");
        String pkg = Pattern.quote(appId);

        // Java/Kotlin crash: "FATAL EXCEPTION: main" followed by "Process: <appId>, PID: ..."
        Pattern process = Pattern.compile("AndroidRuntime.*Process: " + pkg + "(,|\\s|$)");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("FATAL EXCEPTION") && i + 1 < lines.length && process.matcher(lines[i + 1]).find()) {
                // The exception line usually follows "Process:"; fall back to the header if it's missing.
                String summary = i + 2 < lines.length ? message(lines[i + 2]) : message(lines[i]);
                return Optional.of(new Problem(Kind.CRASH, summary, excerpt(lines, i)));
            }
        }

        // ANR: "ANR in <appId>" (ActivityManager) with "Reason: ..." a few lines later,
        // or the event "am_anr: [user,pid,<appId>,flags,reason]".
        Pattern anrIn = Pattern.compile("ANR in " + pkg + "(\\s|$|\\()");
        Pattern amAnr = Pattern.compile("am_anr\\s*: \\[\\d+,\\d+," + pkg + ",[^,]*,(.*)\\]");
        for (int i = 0; i < lines.length; i++) {
            // ANR excerpts quote only the ANR lines: what follows is CPU/stack-dump noise.
            if (anrIn.matcher(lines[i]).find()) {
                String reason = "see logcat";
                int last = i;
                for (int j = i + 1; j < Math.min(lines.length, i + 6); j++) {
                    int at = lines[j].indexOf("Reason: ");
                    if (at >= 0) {
                        reason = lines[j].substring(at + "Reason: ".length()).trim();
                        last = j;
                        break;
                    }
                }
                return Optional.of(new Problem(Kind.ANR, reason, String.join("\n", List.of(lines).subList(i, last + 1))));
            }
            var m = amAnr.matcher(lines[i]);
            if (m.find()) {
                return Optional.of(new Problem(Kind.ANR, m.group(1).trim(), lines[i]));
            }
        }

        // Native crash: "Fatal signal 11 (SIGSEGV) ... in tid N (thread), pid N (<appId>)"
        Pattern nativeCrash = Pattern.compile("Fatal signal .*\\(" + pkg + "\\)");
        for (int i = 0; i < lines.length; i++) {
            if (nativeCrash.matcher(lines[i]).find()) {
                return Optional.of(new Problem(Kind.NATIVE_CRASH, message(lines[i]), excerpt(lines, i)));
            }
        }
        return Optional.empty();
    }

    /** Text after the "TAG: " part of a threadtime line. */
    private static String message(String line) {
        int at = line.indexOf(": ");
        return (at >= 0 ? line.substring(at + 2) : line).trim();
    }

    private static String excerpt(String[] lines, int from) {
        List<String> out = new ArrayList<>();
        for (int i = from; i < Math.min(lines.length, from + EXCERPT_LINES); i++) {
            out.add(lines[i]);
        }
        return String.join("\n", out);
    }
}
