# SoundGroove

SoundGroove is a single, offline **native Android** local music player (package `com.credo.soundgroove`), built with Kotlin + Jetpack Compose (MVVM), Media3/ExoPlayer, Room, and DataStore. It scans on-device audio (`MediaStore`) and plays local files. There is no backend/web service and no secrets. See `ai_handoff.md` for architecture notes (in French).

## Cursor Cloud specific instructions

### Toolchain & standard commands
- JDK 21 is installed; the project builds with the Gradle wrapper (Gradle 8.13, AGP 8.13.2, Kotlin 2.0.21).
- The Android SDK lives at `$HOME/android-sdk` (`ANDROID_HOME` is exported in `~/.bashrc`). `local.properties` (`sdk.dir=$HOME/android-sdk`) is git-ignored; the startup update script recreates it if missing.
- Build (dev): `./gradlew assembleDebug`  (debug APK at `app/build/outputs/apk/debug/app-debug.apk`).
- Lint: `./gradlew lint`  (report: `app/build/reports/lint-results-debug.html`).
- Unit tests: `./gradlew testDebugUnitTest` (only boilerplate `ExampleUnitTest` exists).
- `kapt` prints a harmless warning: "Kapt currently doesn't support language version 2.0+. Falling back to 1.9." — expected, not an error.

### Running the app (emulator) — important caveats
There is **no `/dev/kvm`** in the cloud VM, so the emulator runs in slow software mode (TCG + swiftshader). It works but is sluggish; budget several minutes for boot and first frames.
- Two AVDs are pre-created: **`sg_aosp`** (AOSP `default` image — lighter, far more stable, **use this**) and `sg_test` (`google_apis` — heavier; its bundled Google apps starve the CPU and cause frequent ANRs).
- Launch headless: `emulator -avd sg_aosp -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-accel -memory 4096 -cores 4` (run it in a persistent tmux session).
- First boot triggers heavy dexopt that fires system "isn't responding" (ANR) dialogs. Suppress them with `adb shell settings put global hide_error_dialogs 1`, then let the system quiesce before interacting.
- Drive/inspect the UI over adb (headless): `adb shell input tap X Y`, `adb exec-out screencap -p > shot.png`, `adb shell screenrecord --time-limit N /sdcard/demo.mp4`. The app's bottom nav overlaps the system nav bar — tap tab icons slightly higher to avoid triggering system Home.

### App behavior gotchas
- Songs are loaded **once** at `SoundGrooveViewModel` init from `MediaStore`. If you add audio after the app is running, **cold-restart** it (`adb shell am force-stop com.credo.soundgroove` then relaunch) so it re-queries; otherwise the library shows 0 songs.
- `MusicRepository` filters on `IS_MUSIC != 0`. Put test audio in `/sdcard/Music/`. `adb push` directly into `/sdcard/Music` can be denied — push to `/data/local/tmp` then `cp` into place, then broadcast `android.intent.action.MEDIA_SCANNER_SCAN_FILE`. The scanner often needs a **second scan (~30s later)** to parse ID3 tags (title/artist); `is_music` is set immediately. Grant `READ_MEDIA_AUDIO` (and `POST_NOTIFICATIONS`).
- Theme onboarding shows only when SharedPreferences key `selected_theme` is absent. To skip it, write `shared_prefs/soundgroove_prefs.xml` with `<string name="selected_theme">CLASSIC_DARK</string>` via `run-as com.credo.soundgroove` (debug build).
- Verify playback via `adb shell dumpsys media_session` (look for `package=com.credo.soundgroove` → `state=PLAYING`, advancing `position`, `speed=1.0`). The in-app progress timestamps lag badly under software emulation, so trust `dumpsys` over the on-screen timer.
