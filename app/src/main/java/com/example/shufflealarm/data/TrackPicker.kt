package com.example.shufflealarm.data

import kotlin.random.Random

class TrackPicker(
    private val recentLimit: Int = 7,
    private val rng: Random = Random.Default
) {

    /**
     * Picks the next track uri.
     *
     * - Uses shuffle-bag: consume from bag until empty, then refill & reshuffle
     * - Optionally excludes 'recent' items when refilling (if possible)
     */
    fun pickNext(
        tracks: List<Track>,
        shuffleBag: List<String>,
        recent: List<String>,
        excludeRecent: Boolean
    ): PickResult {
        if (tracks.isEmpty()) return PickResult(null, shuffleBag, recent)

        val allUris = tracks.map { it.uri }
        var bag = shuffleBag.toMutableList()
        var rec = recent.toMutableList()

        if (bag.isEmpty()) {
            val candidate = if (excludeRecent) {
                val filtered = allUris.filterNot { rec.contains(it) }
                if (filtered.isNotEmpty()) filtered else allUris
            } else {
                allUris
            }
            bag = candidate.shuffled(rng).toMutableList()
        }

        val next = bag.removeAt(0)

        // update recent
        rec.add(0, next)
        rec = rec.distinct().toMutableList()
        if (rec.size > recentLimit) {
            rec = rec.take(recentLimit).toMutableList()
        }

        return PickResult(next, bag, rec)
    }

    data class PickResult(
        val pickedUri: String?,
        val newBag: List<String>,
        val newRecent: List<String>
    )
}
