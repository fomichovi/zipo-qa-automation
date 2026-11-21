# Bugs found

App: `app-debug.apk`, package `com.zipo.interviewproject`, version 1.0 (versionCode 1), debug build.
Device for all reproductions: Android Emulator "Medium Phone", API 37, Google Play image, 1080×2400 px at 420 dpi.

| ID | Title | Severity | Type | Reproduced |
|---|---|---|---|---|
| [BUG-01](#bug-01-paywall-disappears-when-the-device-is-rotated) | Paywall disappears when the device is rotated | Medium | UI | Yes |
| [BUG-02](#bug-02-download-buttons-are-not-aligned-across-rows) | Download buttons are not aligned across rows | Low | UI | Yes |
| [BUG-03](#bug-03-slow-start-two-splash-screens-and-a-fixed-3-second-wait) | Slow start: two splash screens and a fixed 3-second wait | Medium | UI / performance | Yes |
| [BUG-04](#bug-04-download-buttons-cant-be-told-apart-with-a-screen-reader) | Download buttons can't be told apart with a screen reader | Low | Accessibility | Yes |
| [BUG-05](#bug-05-switched-off-download-feature-would-crash-and-leak-if-enabled) | Switched-off download feature would crash and leak if enabled | High (latent) | Code | From code |
| [BUG-06](#bug-06-target-sdk-32-is-too-old-for-google-play) | Target SDK 32 is too old for Google Play | Medium | Build / release | From APK |

**Severity scale**
- **Critical:** app unusable or data loss for most users; no workaround.
- **High:** a main flow is broken or the app crashes in a realistic situation.
- **Medium:** a flow works but is degraded, confusing or slow; or a release risk.
- **Low:** cosmetic or minor; doesn't stop the user.

"From code" means found by inspecting the APK (decompiled classes and manifest), not reachable from the UI in this build.

---

## BUG-01: Paywall disappears when the device is rotated
**Severity:** Medium: the user loses the offer in the middle of deciding, and has to find the Download button again.

**Steps to reproduce**
1. Launch the app and wait for the Main screen.
2. Tap **Download** on any row. The paywall ("Premium") opens.
3. Rotate the device to landscape.
4. Rotate back to portrait.

**Expected:** the paywall stays open after each rotation, as dialogs do in standard Android apps.
**Actual:** the paywall disappears after step 3 and doesn't come back. The app stays on Main; no crash.

**Evidence:** logcat at the moment of rotation:
```
E WindowManager: android.view.WindowLeaked: Activity com.zipo.interviewproject.MainActivity has leaked window com.android.internal.policy.DecorView{...} that was originally added here
    at com.zipo.interviewproject.MainActivity$setupRv$1$1.invoke(MainActivity.kt:42)
```
**Suspected cause:** the paywall is a plain `AlertDialog` created in `MainActivity.showTestPaywall()`. When rotation recreates the activity, the dialog is destroyed with the old activity and nothing re-creates it.
**Suggested fix:** show the paywall from a `DialogFragment` (or keep "paywall open" in a ViewModel / saved state and re-show it).
**Also affects (not tested):** any configuration change, such as dark mode toggle, language change, or resizing in split screen.

---

## BUG-02: Download buttons are not aligned across rows
**Severity:** Low: cosmetic, but with longer names the button moves further and could be pushed off screen.

**Steps to reproduce**
1. Launch the app and wait for the Main screen.
2. Compare the left edge of the **Download** buttons on rows "Earth 1"–"Earth 9" with "Earth 10" and "Earth 11".

**Expected:** all Download buttons start at the same horizontal position.
**Actual:** rows 10 and 11 start 21 px (8 dp) further right, because their names are longer.

**Evidence:** element positions from the UI hierarchy:

| Row | Name bounds | Button bounds |
|---|---|---|
| Earth 1 … Earth 9 | `[32,…][149,…]` | starts at x = **317** |
| Earth 10, Earth 11 | `[32,…][170,…]` | starts at x = **338** |

**Suspected cause:** in the row layout (`my_adapter_item.xml`) the button is placed right after the name, so its position depends on the name's width.
**Suggested fix:** give the name a fixed or weighted width (e.g. `layout_weight="1"`), or align the button to the row's end in a `ConstraintLayout`.

---

## BUG-03: Slow start: two splash screens and a fixed 3-second wait
**Severity:** Medium: every launch makes the user wait over 4 seconds before any content, most of it artificial.

**Steps to reproduce**
1. Force-stop the app.
2. Launch it from the launcher and watch until the list appears.

**Expected:** the app shows content as soon as it's ready, with one splash screen.
**Actual:** two different splash screens appear one after the other (the Android 12+ system splash with the icon, then the app's own splash with the app name and a progress bar), and the app waits a fixed ~3 s before opening Main, regardless of device speed.

**Evidence:** screen captures during launch show the system splash (app icon on purple) at ~0.6 s, then the app's own splash ("Interview Project" and a spinner, with the app's title bar still visible on top) at ~2 s. Cold starts, measured with `am start -W` and ActivityManager's "Displayed" log:

| Run | Splash drawn | Fixed wait | Main drawn | Total to content |
|---|---|---|---|---|
| 1 | 1.2 s | 2.8 s | 0.4 s | **4.4 s** |
| 2 | 1.1 s | 2.8 s | 0.3 s | **4.3 s** |
| 3 | 1.1 s | 2.9 s | 0.5 s | **4.5 s** |

**Suspected cause:** `SplashActivity.onCreate()` calls `installSplashScreen()` (system splash) **and** sets its own `activity_splash` layout; then `SplashActivity.fakeInit()` runs `delay(3000)` before starting `MainActivity`.
**Suggested fix:** keep only the system splash (`installSplashScreen()` with `setKeepOnScreenCondition { !ready }`) and finish as soon as the real initialisation is done; drop the fixed delay.

---

## BUG-04: Download buttons can't be told apart with a screen reader
**Severity:** Low: accessibility; a TalkBack user can't tell which item a button belongs to.

**Steps to reproduce**
1. Turn on TalkBack (Settings → Accessibility → TalkBack).
2. Launch the app and move focus through the list.

**Expected:** each button is announced with its row, e.g. "Download Earth 3, button".
**Actual:** all 11 buttons are announced the same way, "Download, button", with no row context. The image view on Main (`main_activity_iv`) also has no description.

**Evidence:** the UI hierarchy shows all 11 `my_adapter_item_btn` elements with `text="DOWNLOAD"` and `content-desc=""`, and `main_activity_iv` with `content-desc=""`.
**Suggested fix:** set a content description per row in `MyListAdapter.onBindViewHolder()` (e.g. "Download " + name), and mark decorative images as `importantForAccessibility="no"`.
**Note:** found from the UI hierarchy; a full manual TalkBack pass is still recommended.

---

## BUG-05: Switched-off download feature would crash and leak if enabled
**Severity:** High (latent): not reachable today because `Constants.ENABLE_TEST_PAYWALL = true` sends every Download tap to the paywall. If the flag is turned off, the download path has several defects, two of which crash the app.

**How to see it:** inspect the decompiled `MainActivity` and `DownloadService` in the APK (no UI steps in this build).

| # | Defect | Where | Effect if enabled |
|---|---|---|---|
| a | Progress text is updated from a background thread | `MainActivity.downloadFile()` runs on `Dispatchers.IO` and calls `progressUpdated()`, which sets a `TextView` | **Crash** (`CalledFromWrongThreadException`) on the first progress update |
| b | No error handling around network and file I/O | `MainActivity.downloadFile()` / `startDownload()`: no try/catch | **Crash** when offline, on timeouts or server errors |
| c | Progress divides by the file size from the server | `downloadFile()`: `bytes * 100 / contentLength` | Crash (division by zero) if the size is 0; negative % if unknown (-1) |
| d | Streams closed only on the success path | `downloadFile()` | File/connection leaks after any error |
| e | Every download saves a new `<timestamp>.highres.jpg` that is never deleted | `downloadFile()`, app data folder | Storage grows with every download |
| f | Full-resolution satellite image decoded into memory before scaling | `processFileAndSetImage()`: `BitmapFactory.decodeStream()` then `createScaledBitmap()` | **Out-of-memory** risk on smaller devices |
| g | `DownloadService` downloads on the main thread and is never started | `DownloadService.onStartCommand()` calls `downloadFile()` directly; nothing starts the service | `NetworkOnMainThreadException` if started; dead code today |

**Suggested fix:** post progress to the main thread (or expose it as state), wrap I/O in try/catch with a user-visible error, guard unknown sizes, use `use {}` for streams, clean up old files, decode with `inSampleSize`, and either fix or remove `DownloadService`.

---

## BUG-06: Target SDK 32 is too old for Google Play
**Severity:** Medium: release risk; Google Play rejects new apps and app updates that target an API level this old.

**How to see it:** `aapt dump badging app-debug.apk` shows `targetSdkVersion:'32'` (and `compileSdkVersion='32'`).

**Expected:** the target SDK meets Google Play's current target API level requirement.
**Actual:** target SDK 32 (Android 12L), several versions behind current Android. The app also runs in compatibility mode on newer devices, so newer platform behaviours aren't tested.
**Suggested fix:** raise `targetSdk` and `compileSdk` to a current level, then re-test runtime behaviour changes (notification permission, foreground service types, edge-to-edge display, predictive back).
