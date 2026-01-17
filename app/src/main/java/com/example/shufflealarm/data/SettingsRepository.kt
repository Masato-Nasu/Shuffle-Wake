package com.example.shufflealarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.shufflealarm.util.JsonUtil

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shuffle_alarm")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TREE_URI = stringPreferencesKey("tree_uri")
        val ALARM_HOUR = intPreferencesKey("alarm_hour")
        val ALARM_MINUTE = intPreferencesKey("alarm_minute")
        val FADE_SECONDS = intPreferencesKey("fade_seconds")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val EXCLUDE_RECENT = booleanPreferencesKey("exclude_recent")

        val TRACKS_JSON = stringPreferencesKey("tracks_json")
        val LAST_SCAN_MILLIS = longPreferencesKey("last_scan_millis")

        val SHUFFLE_BAG_JSON = stringPreferencesKey("shuffle_bag_json")
        val RECENT_JSON = stringPreferencesKey("recent_json")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        val treeUri = pref[Keys.TREE_URI]
        val hour = pref[Keys.ALARM_HOUR] ?: 7
        val minute = pref[Keys.ALARM_MINUTE] ?: 0
        val fade = pref[Keys.FADE_SECONDS] ?: 2
        val snooze = pref[Keys.SNOOZE_MINUTES] ?: 10
        val exclude = pref[Keys.EXCLUDE_RECENT] ?: true

        val tracksPairs = JsonUtil.decodeTracks(pref[Keys.TRACKS_JSON])
        val tracks = tracksPairs.map { (uri, name) -> Track(uri, name) }

        val lastScan = pref[Keys.LAST_SCAN_MILLIS] ?: 0L
        val bag = JsonUtil.decodeStringList(pref[Keys.SHUFFLE_BAG_JSON])
        val recent = JsonUtil.decodeStringList(pref[Keys.RECENT_JSON])

        AppSettings(
            treeUri = treeUri,
            alarmHour = hour,
            alarmMinute = minute,
            fadeSeconds = fade,
            snoozeMinutes = snooze,
            excludeRecent = exclude,
            tracks = tracks,
            lastScanMillis = lastScan,
            shuffleBag = bag,
            recent = recent
        )
    }

    suspend fun setTreeUri(uri: String?) {
        context.dataStore.edit { pref ->
            if (uri == null) pref.remove(Keys.TREE_URI) else pref[Keys.TREE_URI] = uri
        }
    }

    suspend fun setAlarmTime(hour: Int, minute: Int) {
        context.dataStore.edit { pref ->
            pref[Keys.ALARM_HOUR] = hour
            pref[Keys.ALARM_MINUTE] = minute
        }
    }

    suspend fun setFadeSeconds(seconds: Int) {
        context.dataStore.edit { pref -> pref[Keys.FADE_SECONDS] = seconds }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { pref -> pref[Keys.SNOOZE_MINUTES] = minutes }
    }

    suspend fun setExcludeRecent(exclude: Boolean) {
        context.dataStore.edit { pref -> pref[Keys.EXCLUDE_RECENT] = exclude }
    }

    suspend fun setTracks(tracks: List<Track>) {
        context.dataStore.edit { pref ->
            val pairs = tracks.map { it.uri to it.name }
            pref[Keys.TRACKS_JSON] = JsonUtil.encodeTracks(pairs)
            pref[Keys.LAST_SCAN_MILLIS] = System.currentTimeMillis()
            // reset bag when track set changes
            pref[Keys.SHUFFLE_BAG_JSON] = JsonUtil.encodeStringList(emptyList())
        }
    }

    suspend fun setShuffleBag(bag: List<String>) {
        context.dataStore.edit { pref ->
            pref[Keys.SHUFFLE_BAG_JSON] = JsonUtil.encodeStringList(bag)
        }
    }

    suspend fun setRecent(recent: List<String>) {
        context.dataStore.edit { pref ->
            pref[Keys.RECENT_JSON] = JsonUtil.encodeStringList(recent)
        }
    }
}
