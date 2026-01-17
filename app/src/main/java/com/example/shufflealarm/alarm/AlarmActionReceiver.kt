package com.example.shufflealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.shufflealarm.player.PlaybackService

class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> PlaybackService.stopAlarm(context)
            ACTION_SNOOZE -> PlaybackService.snoozeAlarm(context)
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.shufflealarm.action.STOP"
        const val ACTION_SNOOZE = "com.example.shufflealarm.action.SNOOZE"
    }
}
