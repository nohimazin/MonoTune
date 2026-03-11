/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.monochrome

import javax.inject.Inject
import javax.inject.Singleton

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
 * An active Monochrome account session.
 *
 * Instances are persisted via DataStore; the [authToken] is treated as an
 * opaque bearer credential until the real API contract is finalised.
 *
 * @property email        The address used to log in.
 * @property authToken    Bearer token returned by the server (opaque string).
 * @property displayName  Human-readable name shown in the UI, or [email] if not returned.
 * @property serverUrl    Base URL of the Monochrome instance.
 * @property expiresAt    Token expiry as Unix epoch milliseconds, or `null` if unknown.
 */
data class MonochromeSession(
    val email: String,
    val authToken: String,
    val displayName: String,
    val serverUrl: String,
    val expiresAt: Long? = null,
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
 * Implementations are responsible for authentication, searching the Monochrome
 * catalog, resolving stream URLs, and providing availability checks so that
 * MonoTune can decide which tracks can be offered to the user.
 *
 * TODO: Replace stub implementations with real Monochrome API calls once the
 *       backend endpoint specification is finalised.
 */
interface MonochromeClientApi {

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    /**
     * Authenticate with the Monochrome server using email and password.
     *
     * On success the returned [MonochromeSession] should be persisted by the
     * caller (see [MonochromeAuthRepository]) and supplied to subsequent calls
     * via [setSession].
     *
     * @param serverUrl  Base URL of the target Monochrome instance.
     * @param email      Account e-mail address.
     * @param password   Account password (never stored by this client).
     * @return           A [MonochromeSession] on success, or [MonochromeResult.Error].
     */
    suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession>

    /**
     * Invalidate the current session on the server and clear local state.
     */
    suspend fun logout(): MonochromeResult<Unit>

    /**
     * Attempt to refresh an existing session token before it expires.
     *
     * @param session  The session whose token should be refreshed.
     * @return         A new [MonochromeSession] with an updated token, or an error.
     */
    suspend fun refreshSession(session: MonochromeSession): MonochromeResult<MonochromeSession>

    /**
     * Install a previously-restored [MonochromeSession] so that subsequent API
     * calls are authenticated.
     */
    fun setSession(session: MonochromeSession?)

    /** Returns the current session, or `null` if no session is active. */
    fun getSession(): MonochromeSession?

    // -------------------------------------------------------------------------
    // Catalog
    // -------------------------------------------------------------------------

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
     * @return              The matching [MonochromeTrack], or a [MonochromeResult.Error]
     *                      if the track is not found.
     */
    suspend fun getTrack(monochromeId: String): MonochromeResult<MonochromeTrack>

    /**
     * Resolve a playback stream URL for the given Monochrome track.
     *
     * The returned URL is suitable for use as a Media3 / ExoPlayer data source.
     *
     * @param monochromeId  The unique Monochrome track ID.
     * @return              A time-limited stream URL, or a [MonochromeResult.Error]
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
 * yet connected.  Replace this class with a real HTTP implementation (e.g. Ktor
 * or OkHttp) once the Monochrome API endpoint specification is available.
 *
 * Auth state is kept in-memory by this stub; a real implementation will send
 * the [MonochromeSession.authToken] as a bearer header on every request.
 */
@Singleton
class MonochromeClient @Inject constructor() : MonochromeClientApi {

    private var currentSession: MonochromeSession? = null

    // -------------------------------------------------------------------------
    // Authentication stubs
    // -------------------------------------------------------------------------

    override suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession> =
        MonochromeResult.Error("Monochrome login not yet implemented")

    override suspend fun logout(): MonochromeResult<Unit> {
        currentSession = null
        return MonochromeResult.Success(Unit)
    }

    override suspend fun refreshSession(session: MonochromeSession): MonochromeResult<MonochromeSession> =
        MonochromeResult.Error("Monochrome session refresh not yet implemented")

    override fun setSession(session: MonochromeSession?) {
        currentSession = session
    }

    override fun getSession(): MonochromeSession? = currentSession

    // -------------------------------------------------------------------------
    // Catalog stubs
    // -------------------------------------------------------------------------

    override suspend fun search(query: String): MonochromeResult<List<MonochromeTrack>> =
        MonochromeResult.Error("Monochrome backend not yet implemented")

    override suspend fun getTrack(monochromeId: String): MonochromeResult<MonochromeTrack> =
        MonochromeResult.Error("Monochrome backend not yet implemented")

    override suspend fun resolveStreamUrl(monochromeId: String): MonochromeResult<String> =
        MonochromeResult.Error("Monochrome backend not yet implemented")

    override suspend fun isAvailable(monochromeId: String): Boolean = false
}
