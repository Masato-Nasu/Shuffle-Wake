package jp.masatonasu.shufflewake.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import jp.masatonasu.shufflewake.R
import jp.masatonasu.shufflewake.player.PlaybackService

class AlarmRingingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lockscreen
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_ringing)

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            val i = Intent(this, PlaybackService::class.java)
            i.action = PlaybackService.ACTION_STOP
            startService(i)
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            val i = Intent(this, PlaybackService::class.java)
            i.action = PlaybackService.ACTION_SNOOZE_10
            startService(i)
            finish()
        }
    }
}
