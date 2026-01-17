package com.example.shufflealarm.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shufflealarm.alarm.AlarmScheduler
import com.example.shufflealarm.data.MusicScanner
import com.example.shufflealarm.data.SettingsRepository
import com.example.shufflealarm.data.Track
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val settings: StateFlow<com.example.shufflealarm.data.AppSettings> = repo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.example.shufflealarm.data.AppSettings(
            treeUri = null,
            alarmHour = 7,
            alarmMinute = 0,
            fadeSeconds = 2,
            snoozeMinutes = 10,
            excludeRecent = true,
            tracks = emptyList(),
            lastScanMillis = 0L,
            shuffleBag = emptyList(),
            recent = emptyList()
        )
    )

    fun setAlarmTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            repo.setAlarmTime(hour, minute)
            AlarmScheduler.scheduleDaily(getApplication(), hour, minute)
        }
    }

    fun setFadeSeconds(seconds: Int) {
        viewModelScope.launch { repo.setFadeSeconds(seconds) }
    }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { repo.setSnoozeMinutes(minutes) }
    }

    fun setExcludeRecent(exclude: Boolean) {
        viewModelScope.launch { repo.setExcludeRecent(exclude) }
    }

    fun setFolderUri(uri: Uri?) {
        viewModelScope.launch {
            repo.setTreeUri(uri?.toString())
        }
    }

    fun persistTreePermission(uri: Uri) {
        val cr: ContentResolver = getApplication<Application>().contentResolver
        try {
            cr.takePersistableUriPermission(
                uri,
                IntentFlags.READ_WRITE
            )
        } catch (_: SecurityException) {
            // ignore
        }
    }

    fun rescan() {
        viewModelScope.launch {
            val uriStr = settings.value.treeUri ?: return@launch
            val treeUri = Uri.parse(uriStr)
            val scanner = MusicScanner(getApplication())
            val tracks: List<Track> = scanner.scanTree(treeUri)
            repo.setTracks(tracks)
        }
    }

    object IntentFlags {
        const val READ_WRITE: Int =
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
