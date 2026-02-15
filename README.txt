WakeMusic UI Patch (Stop / Volume During Playback / Visible Time)

目的:
- 再生を止められるようにする（MainActivity / AlarmActivity に停止ボタン）
- 再生中に音量を変えられるようにする（STREAM_ALARM を直接変更するスライダー + 音量キー対応）
- 白背景でステータスバー時計が見えない問題を解消（ステータスバーアイコンを黒に + 画面内に現在時刻表示）

適用:
1) app/build.gradle(.kts) の namespace を確認して、合う方のパスを上書き:
   - com.masatonasu.wakemusic
   - com.example.wakemusic

上書きファイル:
- MainActivity.kt
- AlarmActivity.kt

その後:
- Build > Clean Project
- Build > Rebuild Project

補足:
- AlarmActivity が端末によって自動表示されない場合は、AlarmPlayerService.startAlarm() の
  startForeground() 直後に、best-effort で startActivity(AlarmActivity) を追加すると改善することがあります。
