WakeMusic Reliable Sound + Preset Volume Patch

What it fixes:
- Music playback becomes intermittent (especially on Android 8+ / 12+).
- Preset volume seems not applied (because later patches overwrote AlarmPlayerService).

This patch merges:
1) startForeground() immediately (sound-stable)
2) playlist scan in background thread
3) preset ALARM volume applied BEFORE playback (0..100% in AlarmStore)
4) robust fallback ringtone URIs (if folder empty / URI fails)
5) onPlayerError: skip to next or fallback

Apply (IMPORTANT):
1) Check your namespace in app/build.gradle(.kts):
   namespace = "com.masatonasu.wakemusic" OR "com.example.wakemusic"

2) Overwrite matching package files:
   - AlarmStore.kt
   - MainActivity.kt
   - AlarmPlayerService.kt

3) Update AndroidManifest.xml (recommended):
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
   <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

   In <application> add/ensure:
   <service
       android:name=".AlarmPlayerService"
       android:exported="false"
       android:foregroundServiceType="mediaPlayback" />

4) Build > Clean Project, Build > Rebuild Project.

How to verify:
- Open app -> set "起床音量（事前設定）" to e.g. 30%
- Press "テスト再生（今すぐ鳴らす）"
- Logcat filter: WakeMusic -> see "Preset volume..." and "Playlist size=..."
