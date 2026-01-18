package jp.masatonasu.shufflewake.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {

    private val sp = context.getSharedPreferences("shufflewake", Context.MODE_PRIVATE)

    fun getFolderUri(): String? = sp.getString(KEY_FOLDER_URI, null)
    fun setFolderUri(uri: String) {
        sp.edit().putString(KEY_FOLDER_URI, uri).apply()
    }

    fun getTimes(): List<TimeEntry> {
        val raw = sp.getString(KEY_TIMES_JSON, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<TimeEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                TimeEntry(
                    id = o.getString("id"),
                    hour = o.getInt("hour"),
                    minute = o.getInt("minute"),
                    enabled = o.optBoolean("enabled", true)
                )
            )
        }
        return out
    }

    fun addTime(entry: TimeEntry) {
        val list = getTimes().toMutableList()
        list.add(entry)
        saveTimes(list)
    }

    fun updateTime(entry: TimeEntry) {
        val list = getTimes().toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            list[idx] = entry
            saveTimes(list)
        }
    }

    fun deleteTime(id: String) {
        val list = getTimes().filterNot { it.id == id }
        saveTimes(list)
    }

    private fun saveTimes(list: List<TimeEntry>) {
        val arr = JSONArray()
        list.forEach { t ->
            val o = JSONObject()
            o.put("id", t.id)
            o.put("hour", t.hour)
            o.put("minute", t.minute)
            o.put("enabled", t.enabled)
            arr.put(o)
        }
        sp.edit().putString(KEY_TIMES_JSON, arr.toString()).apply()
    }

    fun setTrackUris(uris: List<String>) {
        val arr = JSONArray()
        uris.forEach { arr.put(it) }
        sp.edit().putString(KEY_TRACKS_JSON, arr.toString()).apply()
        sp.edit().putInt(KEY_TRACK_COUNT, uris.size).apply()
    }

    fun getTrackUris(): List<String> {
        val raw = sp.getString(KEY_TRACKS_JSON, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun getCachedTrackCount(): Int = sp.getInt(KEY_TRACK_COUNT, 0)

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_TIMES_JSON = "times_json"
        private const val KEY_TRACKS_JSON = "tracks_json"
        private const val KEY_TRACK_COUNT = "track_count"
    }
}
