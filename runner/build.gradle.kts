plugins {
    java
}

// Compile for Java 17 with whatever JDK (17+) runs Gradle; no toolchain download needed.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.yaml:snakeyaml:2.3")
    // Optional AI failure triage (only used when ANTHROPIC_API_KEY is set).
    testImplementation("com.anthropic:anthropic-java:3.3.0")
}

// ./gradlew unitTest : the framework's own unit tests (no device needed, runs in seconds).
val unitTest by tasks.registering(Test::class) {
    description = "Runs the test framework's own unit tests (no device needed)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("framework") }
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ./gradlew test : the app's UI test suite on a connected device or emulator.
tasks.test {
    useJUnitPlatform { excludeTags("framework") }
    // UI tests talk to a real device: never skip them as "up to date".
    outputs.upToDateWhen { false }

    val root = rootProject.layout.projectDirectory
    systemProperty("qa.flowsDir", root.dir("flows").asFile.absolutePath)
    systemProperty("qa.apk", (findProperty("apk") ?: root.file("app/app-debug.apk").asFile.absolutePath).toString())
    systemProperty("qa.appId", findProperty("appId") ?: "com.zipo.interviewproject")
    systemProperty("qa.reportsDir", layout.buildDirectory.dir("reports/maestro").get().asFile.absolutePath)
    findProperty("tags")?.let { systemProperty("qa.tags", it) }
    findProperty("device")?.let { systemProperty("qa.device", it) }

    testLogging {
        events("passed", "failed", "skipped")
        // Show the failure message (failing step + log path) in the console, without stack traces.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
        showCauses = false
        // Show the runner's own "[runner] ..." lines (device idle wait etc.).
        showStandardStreams = true
    }
}
