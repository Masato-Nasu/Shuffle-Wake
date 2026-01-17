package com.example.shufflealarm.data

data class Track(
    val uri: String,
    val name: String
)

data class AppSettings(
    val treeUri: String?,
    val alarmHour: Int,
    val alarmMinute: Int,
    val fadeSeconds: Int,
    val snoozeMinutes: Int,
    val excludeRecent: Boolean,
    val tracks: List<Track>,
    val lastScanMillis: Long,
    val shuffleBag: List<String>,
    val recent: List<String>
)
