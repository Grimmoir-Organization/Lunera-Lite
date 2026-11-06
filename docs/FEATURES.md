# Approved Lunera Lite scope

Minimum Android version: **6.0, API 23**. Separate application ID: `org.grimmoir.lunera.lite`.

## Library

- All songs, folders and subfolders.
- Search by title, artist or path.
- Sort by title ascending/descending, artist, modification date or duration.
- Favourites and playlists: create, rename, add songs, remove songs, delete playlists.
- Focus locates the current or last-played song in All songs.
- Exclude a folder and its descendants; exclude audio shorter than a chosen duration.
- Manual rescan; the first empty-library launch also performs the initial scan.

## Playback

- Local audio playback with play/pause, seeking, previous and next.
- Queue: add, remove, move up/down and select a song.
- Shuffle and repeat off/all/one.
- Save queue, selected song and playback position. Reopening restores the selection without automatically playing.
- Standard headset, Bluetooth, notification and lock-screen transport controls.
- Pause on headphone disconnection and cooperate with audio focus.
- Music continues when switching apps or turning off the screen.
- Removing the app's task from Recents stops audio, clears the timer, releases the media session, removes the notification and stops the started service. The service is not sticky.
- No app-provided X/close action in the notification.

## Sleep timer

- A countdown of 1–1440 minutes.
- Default: pause immediately when the countdown expires.
- Optional “Finish the song playing when time expires”: continue to that song's end, then pause without advancing, even if repeat is enabled.
- “Turn off” is shown only while the timer or its finish-current-song phase is active.

## Lyrics and artwork

- Embedded ID3 USLT lyrics in MP3; LYRICS/UNSYNCEDLYRICS Vorbis comments in FLAC/Ogg/Opus.
- Import local UTF-8/UTF-16 LRC or TXT lyrics. Imported copies override embedded lyrics without modifying audio files.
- Synchronized LRC highlighting with timestamps, offsets and multiple timestamps per line.
- Local lyrics editor: stamp the current line at the playback position; shift all timestamps earlier/later or by a custom offset.
- Embedded artwork supported by Android's metadata reader, or choose a local image per song.

## Appearance and resource use

- Light, dark and system themes; five accent colour choices.
- Custom background image retained; imported images are bounded to 1280 px and copied into app storage.
- No decorative app animations, animated artwork, blur, fading page transitions or animation libraries.
- One visible-screen progress callback; it is removed when the Activity stops.
- Playback checkpoints at 15-second intervals only while music is playing; queue writes occur off the UI thread.
- A playback wake lock exists only while the framework player is playing.
- Manual in-app update check against public `Grimmoir-Organization/Lunera-Lite` GitHub releases; opens the release page to download.
- No scheduled scans, update polling, boot receiver, analytics, ads, online artwork fetching or online lyrics fetching.

## Excluded by the user's selection

Playback speed, pitch adjustment, equalizer, gapless playback, crossfade, album/artist library tabs, standalone end-of-song timer without a countdown, grid view, startup-page selection, playlist/settings export/import, online lyrics and online artwork fetching.

## Practical limits

Android supplies the audio codecs, so supported formats vary by device. This build reads the embedded lyric tag formats listed above; other tag layouts and formats can use local LRC/TXT import. Physical Bluetooth accessories, manufacturer battery policies, actual battery consumption and every Android release require separate physical-device validation.
