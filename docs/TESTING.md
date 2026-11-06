# Testing Lunera Lite

The release verification results are recorded in `RELEASE-TEST-RESULTS.md` alongside this file.

## Core checks

With JDK 17 on PATH:

```sh
sh scripts/test-core.sh
```

The independent JVM checks cover timestamp precision, LRC offsets and multiple timestamps, malformed offsets, highlighting lookup, timing shifts, countdown decisions, repeat/queue boundaries and excluded-folder boundaries.

## Android integration checks

Use a disposable emulator. **The test runner clears Lite's preferences and writes test data.** It generates its own private audio and images. A separate public audio fixture exercises Android's real media scanner.

Build both APKs using the same debug signing certificate:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python3 scripts/make-test-audio.py
adb shell mkdir -p /sdcard/Music/LuneraLiteTest
adb push app/build/test-long.wav /sdcard/Music/LuneraLiteTest/test-long.wav
```

On Android 6–12, grant audio access:

```sh
adb shell pm grant org.grimmoir.lunera.lite android.permission.READ_EXTERNAL_STORAGE
```

On Android 13+, grant the newer audio and notification permissions instead:

```sh
adb shell pm grant org.grimmoir.lunera.lite android.permission.READ_MEDIA_AUDIO
adb shell pm grant org.grimmoir.lunera.lite android.permission.POST_NOTIFICATIONS
```

Unlock the emulator, then run:

```sh
adb shell input keyevent 82
adb shell am instrument -w org.grimmoir.lunera.lite.test/org.grimmoir.lunera.lite.LiteInstrumentation
```

The suite reports named PASS/FAIL checks and a final total. It uses actual framework audio playback and a real one-minute countdown while the Activity is in the background. Task removal uses Android's `AppTask.finishAndRemoveTask()` and checks audio, timer and notification shutdown. Headphone-disconnect handling is tested by invoking the registered receiver with the system event; ordinary applications cannot send that protected system broadcast.

The `-e only fixtures` argument runs just the embedded-lyrics/import-override and simulated headphone-disconnect checks for focused diagnosis.

## Physical-device checks still needed

- Real Bluetooth and wired-headset accessories, interruptions from calls and accessory-specific media keys.
- Battery drain, thermal behaviour, RAM pressure, large personal music libraries and removable SD cards.
- Manufacturer-specific Recents handling, background restrictions and media-card behaviour.
- Audio quality and format coverage beyond the generated PCM test fixture.
- Other Android releases and physical ARM devices. Emulator checks do not establish universal device compatibility.
