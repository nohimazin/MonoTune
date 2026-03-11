/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.monochrome

import android.util.Log
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches a list of YTM [SongItem]s to their corresponding [MonochromeTrack]s.
 *
 * ### Matching heuristics
 * 1. **Query**: `"${song.title} ${song.artists.firstOrNull()?.name}"` (trimmed).
 * 2. **Candidates**: all tracks returned by [MonochromeClientApi.search] for the query.
 * 3. **Selection**: first candidate where
 *    - normalized title contains or equals the YTM title, AND
 *    - normalized artist string contains or equals the primary YTM artist, AND
 *    - duration is within ±[DURATION_TOLERANCE_SECS] seconds (when both are known).
 *
 * ### Caching
 * Search results are cached in memory keyed by the normalized query string.
 * The cache is bounded to [MAX_CACHE_ENTRIES]; when it is full the entire cache
 * is cleared to keep memory bounded (simple eviction strategy).
 *
 * ### Concurrency
 * At most [MAX_CONCURRENT_REQUESTS] monochrome search calls run in parallel.
 */
@Singleton
class MonochromeSearchMatcher @Inject constructor(
    private val client: MonochromeClientApi,
) {

    private val TAG = "MonochromeSearchMatcher"

    /** Maximum parallel monochrome API requests per [matchSongs] call. */
    private val MAX_CONCURRENT_REQUESTS = 3

    /** Acceptable duration difference in seconds between a YTM song and a monochrome track. */
    private val DURATION_TOLERANCE_SECS = 5

    /** Maximum number of query → results entries to hold in the in-memory cache. */
    private val MAX_CACHE_ENTRIES = 200

    /** In-memory cache: normalized query string → list of monochrome tracks (may be empty). */
    private val cache = ConcurrentHashMap<String, List<MonochromeTrack>>()

    /**
     * Match each [SongItem] in [songs] against the Monochrome catalog.
     *
     * @return a map from YTM video ID to the best-matching [MonochromeTrack].
     *         Songs with no match are absent from the map.
     */
    suspend fun matchSongs(songs: List<SongItem>): Map<String, MonochromeTrack> =
        withContext(Dispatchers.IO) {
            if (songs.isEmpty()) return@withContext emptyMap()

            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            val result = ConcurrentHashMap<String, MonochromeTrack>()

            coroutineScope {
                songs.map { song ->
                    async {
                        semaphore.withPermit {
                            val match = findMatch(song)
                            if (match != null) {
                                result[song.id] = match
                            }
                        }
                    }
                }.awaitAll()
            }

            result
        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun findMatch(song: SongItem): MonochromeTrack? {
        val query = buildQuery(song)
        val candidates = cachedSearch(query)
        return selectBestCandidate(song, candidates)
    }

    private suspend fun cachedSearch(query: String): List<MonochromeTrack> {
        cache[query]?.let { return it }

        val tracks: List<MonochromeTrack> = when (val result = client.search(query)) {
            is MonochromeResult.Success -> result.data
            is MonochromeResult.Error -> {
                Log.w(TAG, "monochrome search failed for '$query': ${result.message}")
                emptyList()
            }
        }

        // Evict entire cache when capacity is reached (simple strategy).
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.clear()
        }
        cache[query] = tracks
        return tracks
    }

    /**
     * Build the query string for a [song]: `"<title> <primaryArtist>"`.
     */
    private fun buildQuery(song: SongItem): String {
        val artist = song.artists.firstOrNull()?.name.orEmpty()
        return "${song.title} $artist".trim()
    }

    /**
     * Pick the best candidate from [candidates] for the given [song].
     *
     * Returns `null` when no candidate satisfies the matching criteria.
     */
    private fun selectBestCandidate(
        song: SongItem,
        candidates: List<MonochromeTrack>,
    ): MonochromeTrack? {
        val normTitle = normalize(song.title)
        val normArtist = normalize(song.artists.firstOrNull()?.name.orEmpty())
        // Capture as a local val so the compiler can smart-cast across module boundaries.
        val songDuration: Int? = song.duration

        return candidates.firstOrNull { candidate ->
            val candTitle = normalize(candidate.title)
            val candArtist = normalize(candidate.artist)

            val titleMatch = candTitle == normTitle ||
                candTitle.contains(normTitle) ||
                normTitle.contains(candTitle)

            val artistMatch = normArtist.isEmpty() ||
                candArtist.contains(normArtist) ||
                normArtist.contains(candArtist)

            val durationMatch = songDuration == null ||
                candidate.durationSecs < 0 ||
                kotlin.math.abs(songDuration - candidate.durationSecs) <= DURATION_TOLERANCE_SECS

            titleMatch && artistMatch && durationMatch
        }
    }

    /**
     * Normalize [text] for fuzzy comparison:
     * lowercase, strip non-alphanumeric (except spaces), collapse whitespace.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
