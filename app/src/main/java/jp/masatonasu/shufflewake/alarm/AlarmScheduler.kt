package jp.masatonasu.shufflewake.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import jp.masatonasu.shufflewake.data.SettingsRepository
import java.util.Calendar

class AlarmScheduler(
    private val context: Context,
    private val repo: SettingsRepository
) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAllEnabledDaily() {
        val times = repo.getTimes().filter { it.enabled }
        times.forEachIndexed { index, t ->
            scheduleDaily(t.id.hashCode(), t.hour, t.minute)
        }
    }

    fun cancelAll() {
        repo.getTimes().forEach { t ->
            cancel(t.id.hashCode())
        }
    }

    private fun scheduleDaily(requestCode: Int, hour: Int, minute: Int) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)

        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val pi = pendingIntent(requestCode)

        // Prefer exact
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun cancel(requestCode: Int) {
        alarmManager.cancel(pendingIntent(requestCode))
    }

    private fun pendingIntent(requestCode: Int): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
        i.action = AlarmReceiver.ACTION_ALARM
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }
}
