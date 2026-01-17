# Shuffle Alarm (Android) — Folder Random Music Alarm (EN/JA)

## What this kit contains
This kit contains the **core source files** for an Android (Jetpack Compose) alarm app that:

- Lets the user pick a **music folder** (Storage Access Framework: directory picker)
- Scans audio files in that folder (including subfolders)
- Schedules a daily alarm with `AlarmManager.setExactAndAllowWhileIdle()`
- Rings by starting a **foreground media playback service** (Media3 ExoPlayer)
- Selects tracks with a **shuffle-bag** algorithm + optional recent-exclusion
- Provides **English (default) + Japanese** UI via `strings.xml`

Important: This kit is designed to be copied into a fresh Android Studio project (Compose template). The kit is not a fully generated Gradle project by itself.

---

## Quick start (Android Studio)

1. Create a new project
   - Android Studio → New Project → **Empty Activity (Jetpack Compose)**
   - Minimum SDK: **26** (Android 8.0) or higher

2. Copy files
   - Copy everything under this kit’s `app/src/main/` into your project’s `app/src/main/` (overwrite).

3. Add dependencies
   In your project’s `app/build.gradle.kts`, add these dependencies (adjust versions if needed):

```kotlin
dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // Optional but recommended
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
}
```

4. Sync + Run
   - Grant **Notifications** permission (Android 13+)
   - Choose a folder and tap **Rescan** to build the track list
   - Set the alarm time and verify **Exact alarms** are allowed on your device

---

## How it works (high level)

- **Folder selection:** SAF directory picker stores a persisted URI permission.
- **Scanning:** `DocumentFile` recursively scans audio documents and stores a compact track list in DataStore.
- **Scheduling:** the next occurrence of the selected time is scheduled using exact alarms.
- **Ringing:** `AlarmReceiver` triggers and starts `PlaybackService` (foreground service).
- **Playback:** Media3 ExoPlayer plays a randomly chosen track with fade-in.
- **Stop/Snooze:** notification actions call the service; snooze schedules a one-shot alarm.

---

## Files included

- `MainActivity.kt` — main UI (alarm time, folder selection, settings)
- `AlarmRingingActivity.kt` — full-screen “Stop / Snooze” screen
- `alarm/*` — scheduling + receivers
- `player/*` — foreground playback service (Media3)
- `data/*` — DataStore settings + track store
- `res/values*/strings.xml` — EN/JA strings

---

## Notes

- This is designed for **local files** (owned by the user). It does not download/convert YouTube/streaming audio.
- Exact alarms are subject to OS policy; on some devices/users must allow exact alarms in Settings.

