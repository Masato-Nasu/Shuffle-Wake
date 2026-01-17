package com.example.shufflealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.shufflealarm.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SettingsRepository(context)
                val s = repo.settingsFlow.first()
                AlarmScheduler.scheduleDaily(context, s.alarmHour, s.alarmMinute)
            } finally {
                pending.finish()
            }
        }
    }
}
