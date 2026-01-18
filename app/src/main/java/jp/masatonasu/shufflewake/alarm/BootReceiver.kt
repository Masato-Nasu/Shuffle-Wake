package jp.masatonasu.shufflewake.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.masatonasu.shufflewake.data.SettingsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val repo = SettingsRepository(context)
            AlarmScheduler(context, repo).scheduleAllEnabledDaily()
        }
    }
}
