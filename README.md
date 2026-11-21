# Interview Project: mobile UI test automation

[![Android UI tests](https://github.com/fomichovi/zipo-qa-automation/actions/workflows/android-tests.yml/badge.svg)](https://github.com/fomichovi/zipo-qa-automation/actions/workflows/android-tests.yml)

Automated UI tests for the 3-screen Android app in `app/app-debug.apk` (Splash → Main → Paywall), built with **Maestro** flows and a small **Java/JUnit** runner that adds crash/ANR detection, per-test diagnostics and optional AI failure triage. Runs locally and on GitHub Actions.

**Documents:** [Test plan](TEST_PLAN.md) · [Bugs found](BUGS.md) · [AI prompts](AI_PROMPTS.md)

---

## Quick start

**Prerequisites**
- JDK 17 or newer
- Android SDK platform tools (`adb`); found via `ANDROID_HOME` / `ANDROID_SDK_ROOT`, the default macOS SDK folder, or `PATH`
- [Maestro CLI](https://maestro.mobile.dev) **2.12.1**: `curl -fsSL https://get.maestro.mobile.dev | MAESTRO_VERSION=2.12.1 bash`
- A running Android emulator (or device) with a **Google Play** image; API 34+ recommended, 4 CPU cores for stable timing

**Run the suite (one command)**
```bash
./gradlew test
```
The runner waits until the device is idle, installs the APK, warms the app up once, then runs every flow. About 3 minutes locally.

| Option | Example | Effect |
|---|---|---|
| `-Ptags` | `./gradlew test -Ptags=P0` | Run only flows with these tags (comma-separated) |
| `-Pdevice` | `./gradlew test -Pdevice=emulator-5554` | Pick a device when several are connected |
| `-Papk` | `./gradlew test -Papk=path/to/other.apk` | Test another build of the app |

`./gradlew unitTest` runs the framework's own unit tests (crash detector, AI triage); no device needed, a few seconds.

**Results**
- Console: one line per test, with the failure message for failed ones.
- `runner/build/reports/maestro/<test>/`: per-test artifacts (see [Failure diagnostics](#failure-diagnostics)).
- `runner/build/reports/tests/test/index.html`: HTML report; `runner/build/test-results/test/`: JUnit XML.

---

## What's tested

Every test starts with two shared steps, so launch, the list and opening the paywall are checked in every run:
- `launch_to_main`: fresh launch with cleared data → Splash shown → Main within 10 s → first row has a non-blank name and a Download button.
- `open_paywall`: tap Download on the **first** row → paywall shown.

| ID | Priority | Test | Expected |
|---|---|---|---|
| TC-01 | P0 | Paywall content | Title is exactly "Premium"; a non-blank message below it |
| TC-02 | P0 | Subscribe | Toast "Test paywall: subscribe (no-op)"; paywall closes; Main shown |
| TC-03 | P0 | Purchase | Toast "Test paywall: purchase (no-op)"; paywall closes |
| TC-04 | P0 | Restore purchases | Toast "Test paywall: restore (no-op)"; paywall closes |
| TC-05 | P0 | Not now | Paywall closes; **no** toast |
| TC-06 | P2 | Background and return with paywall open | Paywall still shown; no crash |
| TC-07 | P2 | Dismiss with Back and outside tap | Paywall closes each time; no toast |

Plus, for every test: **it fails if the app crashes or stops responding (ANR)**, even when all UI steps passed. Risks, priorities and what's left to manual testing are in the [test plan](TEST_PLAN.md).

## Project layout

```
app/app-debug.apk              build under test
flows/                         WHAT is tested (Maestro YAML)
  config.yaml                  which folders contain tests
  subflows/                    shared steps (launch_to_main, open_paywall, choose_paywall_option, ...)
  paywall/  lifecycle/         one file per test
runner/                        HOW it runs (Java 17, JUnit 5, Gradle)
  .../MaestroSuiteTest.java    one JUnit test per flow, found automatically
  .../diagnostics/             logcat capture, crash/ANR detection, screenshots, artifact folders
  .../ai/                      optional AI failure triage
.github/workflows/             CI on an Android emulator
```
**Adding a test = adding a YAML file** to `flows/paywall/` or `flows/lifecycle/`; no Java changes.

---

## Key decisions

**Maestro + a thin Java runner.**
- Espresso was ruled out: it needs the app's source code and runs inside the app, and we only have the APK.
- Appium would work but means more code and an Appium server for a 3-screen app.
- Maestro gives short, readable flows with built-in waiting, and reads toasts. It can't save per-test logcat or detect crashes and ANRs, so the Java layer adds exactly that. The trade-off is two layers; the Java one is kept small and generic.

**Test design**
- **One row only:** all Download buttons share one click handler (seen in the decompiled code), so more rows add run time, not coverage.
- **The list is treated as dynamic:** tests use the first row and check its name is non-blank, never "Earth 1".
- **Exact text only where text is the requirement** (the "Premium" title, the toast messages); the paywall message is checked for presence and position.
- **"No toast" is checked immediately after the tap.** Maestro's `assertNotVisible` waits for an element to disappear, and a toast disappears by itself, so it always passed. This was proven with a deliberately broken test and fixed.
- **Known bugs aren't kept as permanently failing tests.** The rotation bug is in [BUGS.md](BUGS.md), so a red build always means something new.

**Stability, with no fixed sleeps**
- All waits are conditions with timeouts.
- **Wait until the device is idle** before testing: after boot, Google system apps keep a Play Store emulator busy (load 10–18 for about 2 minutes locally, 155 s on CI), which caused flaky failures.
- **Warm-up launch** after install: the first start after installing is much slower and made the first test flaky.

---

## Failure diagnostics

For each **failed** test, `runner/build/reports/maestro/<test>/` contains:

| File | Content |
|---|---|
| `failure.png` | The screen when the test ended |
| `logcat.txt` | Full device log (all apps) for **this test only**: device time marked at start, read afterwards; Maestro's own noisy tag removed (3.3 MB → about 0.3 MB) |
| `maestro.log` | Every step with COMPLETED / FAILED (kept for passing tests too) |
| `maestro-debug/` | Maestro's step screenshots and UI tree |
| `ai-triage.md` | Optional AI triage note (see [AI in the framework](#ai-in-the-framework)) |

**Crash and ANR detection.** After every test, the runner searches that test's log for **this app's** `FATAL EXCEPTION`, `ANR in` / `am_anr` and native `Fatal signal` lines. Other apps' problems are ignored, since they're common on Play Store emulators. If a test has already failed, the runner keeps checking for up to 10 s, because Android reports an ANR about 5 s after the unanswered input. Verified with a real crash (`am crash`) and a real ANR (app process frozen), and with 6 unit tests on real log lines.

**Example: what a failure looks like** (TC-01 with the expected title deliberately changed to "Premium Plus"):
```
TC-01 Paywall content FAILED
  TC-01 Paywall content failed.
  UI steps:
      …
      Run ../subflows/open_paywall.yaml... COMPLETED
      Assert that "Premium Plus", id: paywall_title is visible... FAILED
      Assertion is false: "Premium Plus", id: paywall_title is visible
      …
  Artifacts: runner/build/reports/maestro/tc01_content
    logcat.txt, maestro.log, maestro-debug/, failure.png
  AI triage: skipped (no ANTHROPIC_API_KEY)
```
When the app crashes or freezes, the cause comes first, e.g. `App crashed: java.lang.IllegalStateException …` with the stack trace, or `App not responding (ANR): Input dispatching timed out …`.

---

## CI

[`.github/workflows/android-tests.yml`](.github/workflows/android-tests.yml) runs on every push to `main` and on demand (Actions → Android UI tests → Run workflow):
1. Set up Java 17, Gradle (cached) and hardware acceleration for the emulator.
2. Install Maestro **2.12.1** and fail if the installed version differs.
3. **Framework unit tests first** (`./gradlew unitTest`, no device). If the framework is broken, the run stops in seconds instead of after a 7-minute emulator run with untrustworthy results.
4. Start an **API 34 Google Play** emulator (x86_64, 4 cores, 4 GB, animations off) and run `./gradlew test`, the same command as locally.
5. Always, even on failure: a per-test result table on the run page, and the `test-reports` artifact (per-test folders, HTML report, JUnit XML).

The whole job takes about 7.5 minutes. The `ANTHROPIC_API_KEY` repository secret is optional; without it, AI triage is skipped.

---

## AI in the framework

**AI failure triage (optional).** When a test **fails** and `ANTHROPIC_API_KEY` is set, the runner sends the evidence it already collected (the flow, the failing steps, the crash/ANR result, a filtered logcat excerpt, the screenshot) to **Claude Opus 5.5** at low effort. It gets back a structured note: a category (`APP_BUG` / `TEST_ISSUE` / `ENVIRONMENT` / `UNKNOWN`), confidence, a one-sentence summary, evidence and a next step. The note goes into `ai-triage.md`, one line in the failure message, and the CI run summary.

**Where I chose not to use AI**
| Area | Why not |
|---|---|
| Finding elements | Stable resource IDs exist; exact selectors are faster, free and deterministic |
| Assertions and pass/fail | Must be repeatable; an AI may answer differently on the same input |
| Crash/ANR detection | Exact pattern matching on logcat, unit tested |

**Trade-offs**
- **Advisory only:** the note never changes a test's result, and it says so.
- **Can be wrong:** it must quote its evidence and state its confidence, so a reader can check it quickly.
- **Cost and time:** only on failures; roughly a few cents and seconds per failed test; green runs make no calls.
- **Privacy:** logs and screenshots are sent to Anthropic. That's fine for a test emulator with no personal data, but worth reviewing before using it on real accounts.
- **Robustness:** a timeout, API error or refusal is caught and reported as "unavailable"; it can never break a run. A server-side refusal fallback is enabled.

**Status:** built and covered by 11 unit tests (when it runs, what is sent, size limits, output format); the no-key and API-unreachable paths were checked on a real failure. **It hasn't been run against the real API yet**, because no API key was available during development. Setting the key locally or as the CI secret enables it.

---

## Paywall and Google Play

The APK contains **no billing library**: the paywall is a static test dialog ("Test paywall only. Billing is disabled…"), so it behaves the same on a signed-in or signed-out device and on CI. The suite tests what the app shows in this state. On a **signed-in device with real billing** I would also check:
- real prices and currency, loaded from Google Play;
- Subscribe / Purchase opening the Google Play purchase sheet, and cancelling it returning to the paywall;
- restoring an existing purchase, and the app's state after a successful purchase;
- the error state when billing is unavailable or the network drops mid-purchase.

---

## Scaling to 20 apps and several devices / OS versions

- **One config per app:** app ID, where to get the build (artifact store or CI output), its flows folder and its tags. The runner already takes the app ID and APK as parameters (`-PappId`, `-Papk`), so the Java layer stays shared and unchanged.
- **A shared flow library:** common steps (launch, login, paywall, permissions) as parameterised subflows, used by every app; app-specific flows stay small.
- **Suites by tag:** `smoke` (P0, on every commit, minutes) and `full` (everything, nightly). Tags already exist (`P0`, `paywall`, `lifecycle`).
- **Devices and OS versions as a matrix:** a CI matrix of API levels and screen sizes (e.g. API 29 / 34 / latest; phone and tablet). For speed, shard tests across emulators (`maestro --shard-split`) or move to a device farm (Firebase Test Lab, Maestro Cloud) once there are more than a few combinations.
- **Results in one place:** JUnit XML from every app and device sent to one dashboard; track flaky tests over time and quarantine them instead of retrying silently.
- **Ownership and stability:** each app's flows owned by that app's team, the shared runner and library by a platform/QA team; pinned tool versions (Maestro, emulator images) upgraded deliberately.
- **AI triage scales cheaply:** it only runs on failures, and at 20 apps it saves the most time sorting "app bug vs. test vs. environment".

---

## Known limitations and next steps

- **Machine load matters:** on an 8 GB Mac running Android Studio, a 1-core API 37 emulator was too slow (missed the 3 s Splash, system-app ANRs). Giving the emulator 4 cores fixed it, and the idle wait protects CI.
- **Per-test overhead:** each test starts Maestro (a few seconds) and does a fresh launch with cleared data. That's fine for 7 tests; at scale, use sharding or a persistent Maestro session.
- **Not done in the timebox:** a multi-API / screen-size matrix, an offline/poor-network test (no current flow uses the network), visual regression checks, a check that pauses tests when a system dialog from another app covers the screen, and a live run of the AI triage.
- **Bugs found** (6, incl. paywall lost on rotation and a 4.3–4.5 s start-up) are in [BUGS.md](BUGS.md).
