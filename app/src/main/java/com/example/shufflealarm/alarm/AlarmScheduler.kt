package com.example.shufflealarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.shufflealarm.util.TimeUtil

object AlarmScheduler {

    private const val REQ_ALARM = 1001

    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val triggerAt = TimeUtil.nextOccurrenceMillis(hour, minute)
        scheduleAt(context, triggerAt)
    }

    fun scheduleSnooze(context: Context, minutes: Int) {
        val triggerAt = TimeUtil.addMinutesMillis(minutes)
        scheduleAt(context, triggerAt)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmPendingIntent(context))
    }

    private fun scheduleAt(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_RING)
        return PendingIntent.getBroadcast(
            context,
            REQ_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun immutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
