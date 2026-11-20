# Test Plan TP-ZIPO-001: Interview Project (Android)

**Version:** 1.0 · **Status:** Draft, written before any test code · **Date:** 2026-11-20

## 1. Scope and context

**Test item:** `app-debug.apk`, package `com.zipo.interviewproject`, v1.0 (minSdk 21, targetSdk 32). Black-box testing; no source code.

What I learned from exploring the app and inspecting the APK:
- **Splash** waits a fixed ~3 s, then opens Main.
- **Main** shows a list of rows (name + Download). Every Download button calls the **same handler**.
- **Paywall** is a dialog: title "Premium", a message, and 4 buttons: Subscribe, Purchase, Restore purchases, Not now.
- **There is no billing library in the APK.** The paywall is a static test stub, so it behaves the same with or without a signed-in Google account.
- **Download code exists but is switched off** and can't be reached from the UI.

## 2. What the app must get right

1. Launch without crashing or freezing, show the Splash, and reach Main.
2. Show a usable list: at least one row with a name and a Download button.
3. Open the paywall from Download, with the correct title and message.
4. Make each paywall option do what it says and return the user to Main.
5. Stay stable when the app goes to the background and comes back.

## 3. Risks: what could break, and the impact

Likelihood (L) and Impact (I): 1 low · 2 medium · 3 high.

| ID | Risk | L | I | Priority | Covered by |
|---|---|---|---|---|---|
| R1 | Crash/ANR at launch, or Splash never reaches Main | 1 | 3 | P0 | Every test (shared launch step + crash/ANR check) |
| R2 | List empty or no usable Download button: no path to the paywall | 1 | 3 | P0 | Every test (shared launch step) |
| R3 | Download does not open the paywall: monetisation entry lost | 1 | 3 | P0 | Every paywall test |
| R4 | Wrong or empty paywall title / message | 2 | 3 | P0 | TC-01 |
| R5 | A paywall option does the wrong thing, stays open or crashes | 2 | 3 | P0 | TC-02 … TC-05 |
| R6 | Paywall lost or crash after background → foreground | 2 | 2 | P2 | TC-06 |
| R7 | Back / outside tap does not close the paywall | 1 | 2 | P2 | TC-07 (optional) |
| R8 | Switched-off download code would crash if enabled (UI update off the main thread, unhandled network errors, possible OOM) | 3 | 3 | n/a | Not reachable from UI: reported as code findings |

## 4. Approach

- **Tool:** Maestro flows for the UI, driven by a thin Java (JUnit 5) layer for crash/ANR detection, logcat and screenshots on failure. Reasons in the README.
- **Shared steps, run by every test:**
  - `launch_to_main`: fresh launch with cleared data; Splash shown; Main reached within **10 s**; first row has a non-blank name and a Download button.
  - `open_paywall`: tap Download on the **first row** and wait for the paywall.
- **Dynamic list:** tests never depend on row names, order or count.
- **One row only:** all rows share one handler, so other rows add run time, not coverage.
- **Locators:** resource IDs first; exact text only where the text is the requirement (the "Premium" title and the toasts).
- **No fixed sleeps:** only condition-based waits.
- **Crash/ANR rule:** a test fails if the app crashes or stops responding, even if every UI step passed.
- **Open question:** toasts weren't visible in the UI tree during exploration. If Maestro can't read them, the toast tests check that the paywall closed and Main is shown, and the toast is verified from a screenshot.

## 5. Test cases

Every test starts with `launch_to_main`, then `open_paywall`.

| ID | P | Action | Expected result |
|---|---|---|---|
| TC-01 | P0 | Check paywall content | Title is exactly "Premium"; a non-blank message is shown below it |
| TC-02 | P0 | Tap Subscribe | Toast "Test paywall: subscribe (no-op)"; paywall closes; Main shown |
| TC-03 | P0 | Tap Purchase | Toast "Test paywall: purchase (no-op)"; paywall closes; Main shown |
| TC-04 | P0 | Tap Restore purchases | Toast "Test paywall: restore (no-op)"; paywall closes; Main shown |
| TC-05 | P0 | Tap Not now | Paywall closes; **no** toast; Main shown |
| TC-06 | P2 | Press Home, then return to the app | Paywall still shown; no crash |
| TC-07 | P2 | Press Back; reopen and tap outside (optional) | Paywall closes each time; no toast; Main shown |

## 6. Not automated, and why

| Area | Reason |
|---|---|
| Visual design, dark mode, screen sizes | Needs human judgement; screenshot diffs would be brittle |
| Accessibility (no content descriptions, TalkBack) | Manual check; noted as a finding |
| Real billing (prices, purchase sheet, restore) | No billing in the APK; see section 8 |
| Other rows' Download buttons | Same handler as the first row |
| List data (names, order, count) | Belongs to a backend/API contract test |
| Offline / poor network | No current flow uses the network |

## 7. When coverage is enough

- Every P0 and P1 risk has a test, or a written reason why not.
- Every screen and every paywall option is exercised at least once.
- Crash/ANR detection runs on every test.
- **3 green runs in a row**, locally and on CI, with **no retries**.
- Every failure comes with a screenshot, logcat and the name of the failing step.

## 8. Environment

| | Device | API | Image |
|---|---|---|---|
| Local | Android Emulator, Medium Phone | 37 | Google Play, arm64 |
| CI | Android Emulator on GitHub Actions | 34 | Google Play, x86_64, not signed in |

**On a signed-in device with real billing**, I would also check: real prices and currency, the Google Play purchase sheet opening, cancelling it, restoring an existing purchase, and the error shown when billing is unavailable.
