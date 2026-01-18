package jp.masatonasu.shufflewake.data

import java.util.UUID

data class TimeEntry(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
) {
    fun format(): String = String.format("%02d:%02d", hour, minute)

    companion object {
        fun create(hour: Int, minute: Int): TimeEntry {
            return TimeEntry(UUID.randomUUID().toString(), hour, minute, true)
        }
    }
}
