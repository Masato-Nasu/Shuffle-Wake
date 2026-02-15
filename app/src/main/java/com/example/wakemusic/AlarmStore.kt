package com.example.wakemusic

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.util.Calendar

/**
 * AlarmStore
 * - Music folder (SAF tree URI)
 * - Preset alarm volume (percent 0..100)
 * - Alarms list for BootReceiver/Scheduler (getAlarms)
 *
 * Alarms persistence supports both formats (to avoid breaking existing installs):
 * A) "id|hour|minute|enabled" (preferred)
 * B) "id:triggerAtMillis"    (legacy)
 */
object AlarmStore {
    private const val PREFS = "wakemusic_prefs"

    private const val KEY_MUSIC_TREE_URI = "music_tree_uri"
    private const val KEY_ALARM_VOLUME_PERCENT = "alarm_volume_percent"
    private const val KEY_ALARMS = "alarms"

    private const val DEFAULT_ALARM_VOLUME_PERCENT = 85

    fun setMusicTreeUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_MUSIC_TREE_URI, uri?.toString())
        }
    }

    fun getMusicTreeUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MUSIC_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }

    fun setAlarmVolumePercent(context: Context, percent: Int) {
        val p = percent.coerceIn(0, 100)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putInt(KEY_ALARM_VOLUME_PERCENT, p)
        }
    }

    fun getAlarmVolumePercent(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ALARM_VOLUME_PERCENT, DEFAULT_ALARM_VOLUME_PERCENT)
            .coerceIn(0, 100)
    }

    fun getAlarms(context: Context): List<AlarmItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ALARMS, "")
            .orEmpty()
            .trim()
        if (raw.isEmpty()) return emptyList()

        val result = mutableListOf<AlarmItem>()

        raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { part ->
                // A) id|hour|minute|enabled
                if (part.contains("|")) {
                    val p = part.split("|")
                    if (p.size >= 4) {
                        val id = p[0].toIntOrNull() ?: return@forEach
                        val hour = p[1].toIntOrNull() ?: return@forEach
                        val minute = p[2].toIntOrNull() ?: return@forEach
                        val enabled = p[3].toBooleanStrictOrNull() ?: true
                        result.add(
                            AlarmItem(
                                id = id,
                                hour = hour.coerceIn(0, 23),
                                minute = minute.coerceIn(0, 59),
                                enabled = enabled
                            )
                        )
                        return@forEach
                    }
                }
                // B) legacy id:triggerAtMillis
                if (part.contains(":")) {
                    val p = part.split(":")
                    if (p.size >= 2) {
                        val id = p[0].toIntOrNull() ?: return@forEach
                        val ms = p[1].toLongOrNull() ?: return@forEach
                        val cal = Calendar.getInstance().apply { timeInMillis = ms }
                        result.add(
                            AlarmItem(
                                id = id,
                                hour = cal.get(Calendar.HOUR_OF_DAY),
                                minute = cal.get(Calendar.MINUTE),
                                enabled = true
                            )
                        )
                        return@forEach
                    }
                }
            }

        return result.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
    }

    fun saveAlarms(context: Context, alarms: List<AlarmItem>) {
        val encoded = alarms.joinToString(",") { a ->
            "${a.id}|${a.hour}|${a.minute}|${a.enabled}"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_ALARMS, encoded)
        }
    }
}
