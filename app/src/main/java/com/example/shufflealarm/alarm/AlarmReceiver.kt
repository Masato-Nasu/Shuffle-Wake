package com.example.shufflealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.shufflealarm.player.PlaybackService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RING) return
        PlaybackService.startAlarm(context)
    }

    companion object {
        const val ACTION_RING = "com.example.shufflealarm.action.RING"
    }
}
