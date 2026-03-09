/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.monochrome

/**
 * Represents a track available in the Monochrome streaming backend.
 *
 * @property monochromeId  Unique identifier in the Monochrome catalog.
 * @property title         Track title.
 * @property artist        Primary artist name.
 * @property album         Album name, if available.
 * @property durationSecs  Track duration in seconds.
 * @property streamUrl     Resolved streaming URL (non-null when the track is ready to play).
 */
data class MonochromeTrack(
    val monochromeId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSecs: Int = -1,
    val streamUrl: String? = null,
)

/**
 * Sealed result type for Monochrome API calls.
 */
sealed class MonochromeResult<out T> {
    data class Success<T>(val data: T) : MonochromeResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : MonochromeResult<Nothing>()
}

/**
 * Client interface for the Monochrome streaming backend.
 *
 * Implementations are responsible for searching the Monochrome catalog, resolving
 * stream URLs, and providing availability checks so that MonoTune can decide
 * which tracks can be offered to the user.
 *
 * TODO: Replace stub implementations with real Monochrome API calls once the
 *       backend endpoint specification is finalised.
 */
interface MonochromeClientApi {
    /**
     * Search the Monochrome catalog by free-text query.
     *
     * @param query  Search string (title, artist, or any combination).
     * @return       List of matching [MonochromeTrack] instances, ordered by relevance.
     */
    suspend fun search(query: String): MonochromeResult<List<MonochromeTrack>>

    /**
     * Look up a single track by its Monochrome identifier.
     *
     * @param monochromeId  The unique Monochrome track ID.
     * @return              The matching [MonochromeTrack], or an [MonochromeResult.Error]
     *                      if the track is not found.
     */
    suspend fun getTrack(monochromeId: String): MonochromeResult<MonochromeTrack>

    /**
     * Resolve a playback stream URL for the given Monochrome track.
     *
     * The returned URL is suitable for use as a Media3 / ExoPlayer data source.
     *
     * @param monochromeId  The unique Monochrome track ID.
     * @return              A time-limited stream URL, or an [MonochromeResult.Error]
     *                      if the track is unavailable or the request fails.
     */
    suspend fun resolveStreamUrl(monochromeId: String): MonochromeResult<String>

    /**
     * Check whether a given track is currently available on Monochrome.
     *
     * This is a lightweight availability probe — implementations may cache
     * results to avoid redundant network calls.
     *
     * @param monochromeId  The unique Monochrome track ID.
     * @return              `true` if the track can be streamed right now.
     */
    suspend fun isAvailable(monochromeId: String): Boolean
}

/**
 * Stub implementation of [MonochromeClientApi].
 *
 * All methods return [MonochromeResult.Error] indicating that the backend is not
 * yet connected.  Replace this class with a real HTTP/gRPC implementation once
 * the Monochrome API endpoint is available.
 */
class MonochromeClient : MonochromeClientApi {

    override suspend fun search(query: String): MonochromeResult<List<MonochromeTrack>> =
        MonochromeResult.Error("Monochrome backend not yet implemented")

    override suspend fun getTrack(monochromeId: String): MonochromeResult<MonochromeTrack> =
        MonochromeResult.Error("Monochrome backend not yet implemented")

    override suspend fun resolveStreamUrl(monochromeId: String): MonochromeResult<String> =
        MonochromeResult.Error("Monochrome backend not yet implemented")

    override suspend fun isAvailable(monochromeId: String): Boolean = false

    companion object {
        /** Singleton instance; replace with Hilt injection once the real client is implemented. */
        val instance: MonochromeClient by lazy { MonochromeClient() }
    }
}
