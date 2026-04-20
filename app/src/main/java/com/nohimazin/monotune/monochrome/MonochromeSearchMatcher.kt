/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.monochrome

import com.nohimazin.monotune.models.MediaMetadata

import android.util.Log
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
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
 *    - duration is within ±[DURATION_TOLERANCE_SECS] seconds (when both are known),
 *      with a second-pass fallback of ±[RELAXED_DURATION_TOLERANCE_SECS] seconds.
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

    /** Fallback tolerance for slight edit/version length differences. */
    private val RELAXED_DURATION_TOLERANCE_SECS = 20

    /** Maximum number of query ↁEresults entries to hold in the in-memory cache. */
    private val MAX_CACHE_ENTRIES = 200

    /** In-memory cache: normalized query string ↁElist of monochrome tracks (may be empty). */
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

    /**
     * Match a single [MediaMetadata] against the Monochrome catalog.
     */
    suspend fun matchSong(metadata: MediaMetadata): MonochromeTrack? =
        withContext(Dispatchers.IO) {
            val artistName = metadata.artists.firstOrNull()?.name ?: ""
            val candidates = searchCandidates(
                title = metadata.title.orEmpty(),
                artist = artistName
            )

            // Adapt the selection logic for MediaMetadata
            val normTitle = normalize(metadata.title ?: "")
            val normArtist = normalize(artistName)
            val songDuration: Int? = metadata.duration

            selectBestCandidate(
                normTitle = normTitle,
                normArtist = normArtist,
                songDuration = songDuration,
                candidates = candidates
            )
        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun findMatch(song: SongItem): MonochromeTrack? {
        val candidates = searchCandidates(
            title = song.title,
            artist = song.artists.firstOrNull()?.name.orEmpty()
        )
        return selectBestCandidate(song, candidates)
    }

    /**
     * Search using multiple query variants to reduce false negatives.
     *
     * Order matters: most precise query first, then broader fallbacks.
     */
    private suspend fun searchCandidates(title: String, artist: String): List<MonochromeTrack> {
        val queries = buildQueries(title = title, artist = artist)
        val merged = LinkedHashMap<String, MonochromeTrack>()
        var firstHitQuery: String? = null

        for (query in queries) {
            val tracks = cachedSearch(query)
            if (firstHitQuery == null && tracks.isNotEmpty()) {
                firstHitQuery = query
            }
            for (track in tracks) {
                // Prefer monotonic ID when available, otherwise fall back to a composite key.
                val key = track.monochromeId.ifBlank {
                    "${track.title.lowercase(Locale.ROOT)}|${track.artist.lowercase(Locale.ROOT)}|${track.durationSecs}"
                }
                merged.putIfAbsent(key, track)
            }
        }

        if (firstHitQuery != null && firstHitQuery != queries.firstOrNull()) {
            Log.d(TAG, "searchCandidates: fallback query used ('$firstHitQuery') for title='$title'")
        }
        if (merged.isEmpty()) {
            Log.w(TAG, "searchCandidates: no candidates for queries=$queries")
        }

        return merged.values.toList()
    }

    /**
     * Build ordered query fallbacks for Monochrome search.
     *
     * 1) title + artist (most specific)
     * 2) title only
     * 3) title with common bracketed suffixes removed (e.g. "(Live)", "[Remaster]")
     */
    private fun buildQueries(title: String, artist: String): List<String> {
        val titleTrimmed = title.trim()
        val artistTrimmed = artist.trim()
        val titleWithoutBracketSuffix = titleTrimmed
            .replace(Regex("\\s*[\\(\\[].*[\\)\\]]\\s*$"), "")
            .trim()

        return listOf(
            buildQuery(titleTrimmed, artistTrimmed),
            titleTrimmed,
            titleWithoutBracketSuffix
        ).filter { it.isNotBlank() }.distinct()
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
        return buildQuery(song.title, artist)
    }

    private fun buildQuery(title: String, artist: String): String {
        return "$title $artist".trim()
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
        return selectBestCandidate(
            normTitle = normalize(song.title),
            normArtist = normalize(song.artists.firstOrNull()?.name.orEmpty()),
            songDuration = song.duration,
            candidates = candidates
        )
    }

    private fun selectBestCandidate(
        normTitle: String,
        normArtist: String,
        songDuration: Int?,
        candidates: List<MonochromeTrack>,
    ): MonochromeTrack? {
        return findCandidate(
            normTitle = normTitle,
            normArtist = normArtist,
            songDuration = songDuration,
            candidates = candidates,
            durationToleranceSecs = DURATION_TOLERANCE_SECS
        ) ?: findCandidate(
            normTitle = normTitle,
            normArtist = normArtist,
            songDuration = songDuration,
            candidates = candidates,
            durationToleranceSecs = RELAXED_DURATION_TOLERANCE_SECS
        )
    }

    private fun findCandidate(
        normTitle: String,
        normArtist: String,
        songDuration: Int?,
        candidates: List<MonochromeTrack>,
        durationToleranceSecs: Int,
    ): MonochromeTrack? {
        // If both title and artist are missing after normalization, avoid random matches.
        if (normTitle.isEmpty() && normArtist.isEmpty()) return null

        // Capture as a local val so the compiler can smart-cast across module boundaries.
        return candidates.firstOrNull { candidate ->
            val candTitle = normalize(candidate.title)
            val candArtist = normalize(candidate.artist)

            val titleMatch = candTitle == normTitle ||
                candTitle.contains(normTitle) ||
                normTitle.contains(candTitle)

            val artistMatch = normArtist.isEmpty() || // no artist metadata ↁEcan't disqualify
                candArtist.contains(normArtist) ||
                normArtist.contains(candArtist)

            val durationMatch = songDuration == null || songDuration < 0 ||
                candidate.durationSecs < 0 ||
                kotlin.math.abs(songDuration - candidate.durationSecs) <= durationToleranceSecs

            titleMatch && artistMatch && durationMatch
        }
    }

    /**
     * Normalize [text] for fuzzy comparison:
     * lowercase, strip non-alphanumeric (except spaces), collapse whitespace.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
