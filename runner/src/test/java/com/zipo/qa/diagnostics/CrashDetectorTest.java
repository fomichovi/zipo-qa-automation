package com.zipo.qa.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zipo.qa.diagnostics.CrashDetector.Kind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the crash/ANR detector. Log lines use real logcat formats; no device needed.
 * Tagged "framework": run with ./gradlew unitTest, kept out of the app's UI suite (./gradlew test).
 */
@Tag("framework")
class CrashDetectorTest {

    private static final String APP = "com.zipo.interviewproject";

    @Test
    void detectsJavaCrashOfTheApp() {
        String log = """
                11-20 16:20:01.100  5556  5556 E AndroidRuntime: FATAL EXCEPTION: main
                11-20 16:20:01.100  5556  5556 E AndroidRuntime: Process: com.zipo.interviewproject, PID: 5556
                11-20 16:20:01.100  5556  5556 E AndroidRuntime: java.lang.IllegalStateException: boom
                11-20 16:20:01.100  5556  5556 E AndroidRuntime: \tat com.zipo.interviewproject.MainActivity.onCreate(MainActivity.kt:30)
                """;
        var problem = CrashDetector.find(log, APP).orElseThrow();
        assertEquals(Kind.CRASH, problem.kind());
        assertEquals("App crashed: java.lang.IllegalStateException: boom", problem.headline());
        assertTrue(problem.excerpt().contains("MainActivity.kt:30"));
    }

    @Test
    void detectsAnrFromActivityManager() {
        String log = """
                11-20 16:21:00.000   612   640 E ActivityManager: ANR in com.zipo.interviewproject (com.zipo.interviewproject/.MainActivity)
                11-20 16:21:00.000   612   640 E ActivityManager: PID: 5556
                11-20 16:21:00.000   612   640 E ActivityManager: Reason: Input dispatching timed out
                """;
        var problem = CrashDetector.find(log, APP).orElseThrow();
        assertEquals(Kind.ANR, problem.kind());
        assertEquals("App not responding (ANR): Input dispatching timed out", problem.headline());
    }

    @Test
    void detectsAnrFromEventsBuffer() {
        String log = "11-20 14:11:02.123   612 23355 I am_anr  : [0,23285,com.zipo.interviewproject,818527814,"
                + "Input dispatching timed out (SplashActivity is not responding. Waited 5001ms)]\n";
        var problem = CrashDetector.find(log, APP).orElseThrow();
        assertEquals(Kind.ANR, problem.kind());
        assertTrue(problem.summary().startsWith("Input dispatching timed out"));
    }

    @Test
    void detectsNativeCrash() {
        String log = "11-20 16:22:00.000  5556  5556 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), "
                + "fault addr 0x0 in tid 5556 (main), pid 5556 (com.zipo.interviewproject)\n";
        assertEquals(Kind.NATIVE_CRASH, CrashDetector.find(log, APP).orElseThrow().kind());
    }

    @Test
    void ignoresOtherAppsProblems() {
        // Real lines from this project's emulator: system apps froze, our app was fine.
        String log = """
                11-20 14:11:39.950   612 23355 I am_anr  : [0,5269,com.google.android.gms,-1597325627,executing service com.google.android.gms/.wearable.service.WearableControlService, waited 31573ms]
                11-20 14:11:40.009   612 23355 E ActivityManager: ANR in com.google.android.apps.wellbeing
                11-20 14:11:40.009   612 23355 E ActivityManager: Reason: Broadcast of Intent { act=android.intent.action.PACKAGE_FULLY_REMOVED }
                11-20 16:20:01.100  7000  7000 E AndroidRuntime: FATAL EXCEPTION: main
                11-20 16:20:01.100  7000  7000 E AndroidRuntime: Process: com.android.phone, PID: 7000
                11-20 13:32:49.879  1437  1437 D MediaGrants: Removed 0 media_grants for [com.zipo.interviewproject]. Reason: Package orphaned
                """;
        assertTrue(CrashDetector.find(log, APP).isEmpty());
    }

    @Test
    void doesNotMatchPackagesThatOnlyStartWithTheAppId() {
        String log = """
                11-20 16:20:01.100  7000  7000 E AndroidRuntime: FATAL EXCEPTION: main
                11-20 16:20:01.100  7000  7000 E AndroidRuntime: Process: com.zipo.interviewproject.helper, PID: 7000
                11-20 16:21:00.000   612   640 I am_anr  : [0,7001,com.zipo.interviewprojectx,0,Input dispatching timed out]
                """;
        assertTrue(CrashDetector.find(log, APP).isEmpty());
    }
}
