package jp.masatonasu.shufflewake.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import jp.masatonasu.shufflewake.player.PlaybackService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM) return

        // Start playback
        val svc = Intent(context, PlaybackService::class.java)
        svc.action = PlaybackService.ACTION_PLAY_RANDOM
        ContextCompat.startForegroundService(context, svc)

        // Show ringing screen
        val ring = Intent(context, AlarmRingingActivity::class.java)
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(ring)
    }

    companion object {
        const val ACTION_ALARM = "jp.masatonasu.shufflewake.ACTION_ALARM"
    }
}
