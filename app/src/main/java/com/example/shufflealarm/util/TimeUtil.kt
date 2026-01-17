package com.example.shufflealarm.util

import java.util.Calendar

object TimeUtil {

    fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun addMinutesMillis(minutes: Int): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutes)
        }
        return cal.timeInMillis
    }
}
