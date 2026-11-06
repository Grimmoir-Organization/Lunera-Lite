# Lunera Lite 1.0.0 — build and test report

Date: 6 November 2026 (India time).

## Artifact

- Package: `org.grimmoir.lunera.lite` (installs alongside regular Lunera).
- Version: 1.0.0 / version code 10000.
- Minimum: Android 6.0, API 23. Target/compile API 35.
- APK: 58,166 bytes; no native ABI-specific libraries.
- APK SHA-256: `40d6b20c66db943ddefdb833c42fd864736bd9a3fc6f0323a2526ed0729b2f33`.
- Release signature verified with APK Signature Schemes v1, v2 and v3.
- Signing certificate SHA-256: `583e9802ac8f8a46ea8c37512ba28a43350cddcaa72dba416daac02f20c509b8` (existing Lunera release key; credentials excluded).

## Results

| Verification | Result |
| --- | --- |
| JDK core regression checks | 33 passed |
| Android 6 / API 23, x86_64 software emulator | 18 distinct integration checks verified |
| Android 15 / API 35, x86_64 software emulator | 18 distinct integration checks verified |
| Gradle debug APK, instrumentation APK and Android lint | Build successful; 0 lint errors, 18 non-blocking warnings |
| APK signature and min/target SDK metadata | Verified |

The warnings concern hardcoded English UI strings and Android's newer backup configuration recommendation. The public app source has no external runtime dependencies. The signed release was built with the supplied dependency-free SDK build script from the same Java sources and resources checked by Gradle; it is not the Gradle debug APK.

The checks exercise actual framework playback, pause/seek/resume, saved positions, queue changes, media-session controls, background playback, a real one-minute background sleep timer, finish-song timer behaviour, timer cancellation, favourites, playlists, scanner filters, local lyrics and image handling, themes, and task dismissal. On both Android versions, task dismissal stopped playback, cancelled the timer and removed the app notification. Headphone-disconnect handling uses a simulated receiver invocation; no physical accessory was connected.

API 23 completed a single full 18-check passing run. API 35 completed 16 passing checks in the broad run, followed by passing focused reruns of library scanning, folder exclusion and task/notification dismissal. The initial API 35 scanner fixture had not been created because raw external-storage access failed in the emulator; the rerun seeded audio through MediaStore. The notification check was changed to wait for Android's asynchronous cancellation to complete. The app APK did not change between those runs. Earlier fixture issues (an invalid ID3 header and an attempted protected system broadcast) were corrected in the test runner.

The Android 15 software emulator also experienced a System UI timeout during setup. These results establish the named application behaviours in the final runs, not the emulator's reliability or physical-device performance. Named passing checks are retained in `docs/verification/`.

## Scope and limitations

- Physical ARM phones, real Bluetooth/headset hardware, OEM battery restrictions, real battery drain and thermal behaviour were not measured.
- Android versions other than API 23 and API 35 were not run. Do not interpret Android 6+ as every phone/version tested.
- The audio integration fixture is generated PCM WAV; codec and embedded-tag coverage varies by format/device. Embedded ID3 USLT and local lyric override were exercised; other documented tag readers were not tested against a full real-world music corpus.
- The update checker is implemented for the public Lunera-Lite latest-release endpoint; an end-to-end update to a newer published Lite version was not available to test.
- Visual inspection of the library was performed on the Android 6 emulator. Generated test files shown in screenshots are not included in the APK.

## Approved behaviour

Custom backgrounds remain, with bounded image sizes. No playback speed, app-provided notification X action, decorative animations, scheduled rescans, background update polling, online lyrics or online artwork fetching are included. Removing Lite from Recents stops playback. See `docs/FEATURES.md` for the complete approved scope.
