package com.example.shufflealarm.util

import org.json.JSONArray
import org.json.JSONObject

object JsonUtil {

    fun encodeStringList(values: List<String>): String {
        val arr = JSONArray()
        values.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx, null)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun encodeTracks(tracks: List<Pair<String, String>>): String {
        val arr = JSONArray()
        tracks.forEach { (uri, name) ->
            val obj = JSONObject()
            obj.put("uri", uri)
            obj.put("name", name)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeTracks(json: String?): List<Pair<String, String>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val uri = obj.optString("uri", null) ?: return@mapNotNull null
                val name = obj.optString("name", "")
                uri to name
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
