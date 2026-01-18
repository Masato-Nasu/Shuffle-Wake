# Shuffle Wake (Android only)

Android alarm app that:
- Lets you select a **music folder** (SAF / DocumentTree)
- Scans **100+ tracks** (tested design up to thousands)
- Lets you set **multiple alarm times** for the same morning
- Plays a **random track** from the selected folder when the alarm rings

## Build without Android Studio (recommended)

1) Create a new GitHub repository (e.g. `shufflewake`).
2) Upload this project as-is (or `git push`).
3) Open **Actions** → **Build Debug APK** → **Run workflow**.
4) After it finishes, download the artifact **shufflewake-debug-apk** and install `app-debug.apk` on your phone.

## Install APK on your phone
- Copy the APK to your phone and open it.
- If blocked: Settings → Security → allow “Install unknown apps” for your file manager / browser.

## First-run steps in the app
1) Tap **フォルダ選択 / Choose folder** and select your music folder.
2) Tap **再スキャン / Rescan** to count tracks.
3) Add one or more alarm times.
4) Tap **有効化 / Enable**.

### Exact alarm permission (Android 12+)
If alarms don’t trigger exactly, tap **正確なアラームの許可画面を開く** and allow exact alarms.
