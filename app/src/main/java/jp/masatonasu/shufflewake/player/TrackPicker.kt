package jp.masatonasu.shufflewake.player

import android.content.Context
import jp.masatonasu.shufflewake.data.SettingsRepository
import kotlin.random.Random

class TrackPicker(
    private val context: Context,
    private val repo: SettingsRepository
) {
    fun pickRandom(): String? {
        val tracks = repo.getTrackUris()
        if (tracks.isEmpty()) return null
        return tracks[Random.nextInt(tracks.size)]
    }
}
