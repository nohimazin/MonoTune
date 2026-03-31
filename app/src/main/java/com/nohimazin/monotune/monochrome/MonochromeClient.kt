/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.monochrome

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// Constants ? monochrome backend
// ---------------------------------------------------------------------------

/**
 * Appwrite authentication endpoint used by the official monochrome.tf instance.
 * Authentication is managed by Appwrite; the hifi-api instances themselves are open
 * (no client-side auth token required for music streaming).
 *
 * Reference: js/accounts/config.js in the monochrome-music/monochrome repo.
 */
const val APPWRITE_ENDPOINT = "https://auth.monochrome.tf/v1"
const val APPWRITE_PROJECT_ID = "auth-for-monochrome"

/**
 * Default hifi-api instance for search and metadata.
 * Source: public/instances.json in monochrome-music/monochrome.
 */
const val DEFAULT_MONOCHROME_API_URL = "https://api.monochrome.tf"

/**
 * Default hifi-api streaming instance (used for `/track/` requests that return manifests).
 * Source: public/instances.json in monochrome-music/monochrome.
 */
const val DEFAULT_MONOCHROME_STREAMING_URL = "https://arran.monochrome.tf"

/** Base URL for TIDAL album/track cover art. */
const val TIDAL_IMAGE_BASE_URL = "https://resources.tidal.com/images"

/** LRCLib endpoint used by monochrome for synced lyrics. */
const val LRCLIB_API_URL = "https://lrclib.net/api/get"

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

/**
 * Represents a TIDAL track surfaced through the monochrome / hifi-api backend.
 *
 * [monochromeId] is the TIDAL track ID (numeric, stored as a String).
 * [coverArtId]   is the TIDAL cover UUID (dashes), used to build cover art URLs
 *                via [tidalCoverUrl].
 */
data class MonochromeTrack(
    val monochromeId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSecs: Int = -1,
    val coverArtId: String? = null,
    val isrc: String? = null,
    val streamUrl: String? = null,
)

/**
 * Lyrics data returned by the LRCLib API.
 *
 * [syncedLyrics] is in LRC format (`[mm:ss.xx] line`) when available.
 * [plainLyrics]  is plain text, used as a fallback.
 */
data class MonochromeLyrics(
    val trackName: String,
    val artistName: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val instrumental: Boolean = false,
)

/**
 * An active Monochrome account session.
 *
 * The [serverUrl] points to the **hifi-api** instance used for music search and
 * metadata (e.g. `https://api.monochrome.tf`).  It is separate from the Appwrite
 * auth endpoint which is hardcoded to [APPWRITE_ENDPOINT].
 *
 * The [authToken] is the Appwrite session token extracted from the `Set-Cookie`
 * response header after a successful login.  It is sent as the
 * `X-Appwrite-Session` header on subsequent Appwrite API calls.
 *
 * Music streaming via the hifi-api does **not** require client auth ? the token
 * is only needed for account-sync features (library, history, playlists).
 */
data class MonochromeSession(
    val email: String,
    val authToken: String,
    val displayName: String,
    val serverUrl: String,
    val expiresAt: Long? = null,
)

/** Sealed result type for Monochrome API calls. */
sealed class MonochromeResult<out T> {
    data class Success<T>(val data: T) : MonochromeResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : MonochromeResult<Nothing>()
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Build a TIDAL cover-art URL from [coverUuid] (dash-separated UUID) at [size]~[size] px.
 *
 * Example:
 * ```
 * tidalCoverUrl("e77e4cc0-6cd0-4522-807d-88aeac488065", 320)
 * //  "https://resources.tidal.com/images/e77e4cc0/6cd0/4522/807d/88aeac488065/320x320.jpg"
 * ```
 */
fun tidalCoverUrl(coverUuid: String, size: Int = 320): String =
    "$TIDAL_IMAGE_BASE_URL/${coverUuid.replace('-', '/')}/${size}x${size}.jpg"

// ---------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------

/**
 * Client interface for the monochrome / hifi-api music backend.
 *
 * The underlying backend is the **hifi-api** (https://github.com/binimum/hifi-api),
 * a Python REST proxy to TIDAL's API.  Account management (for library sync) is
 * handled by the Appwrite instance at [APPWRITE_ENDPOINT].
 *
 * ### Endpoint summary (hifi-api)
 * | Purpose         | Path                          |
 * |-----------------|-------------------------------|
 * | Search tracks   | `GET /search/?s={query}`      |
 * | Track metadata  | `GET /info/?id={tidalId}`     |
 * | Stream manifest | `GET /track/?id={tidalId}&quality={quality}` |
 * | Album info      | `GET /album/?id={albumId}`    |
 * | Artist info     | `GET /artist/?id={artistId}`  |
 * | Playlist info   | `GET /playlist/?id={uuid}`    |
 * | Recommendations | `GET /recommendations/?id={tidalId}` |
 *
 * ### Appwrite auth (account sync only)
 * | Purpose        | Path                                        |
 * |----------------|---------------------------------------------|
 * | Login          | `POST /v1/account/sessions/email`           |
 * | Get account    | `GET  /v1/account`                          |
 * | Logout         | `DELETE /v1/account/sessions/current`       |
 *
 * ### Lyrics (LRCLib)
 * `GET https://lrclib.net/api/get?artist_name=c&track_name=c&album_name=c&duration=c`
 */
interface MonochromeClientApi {

    // -----------------------------------------------------------------------
    // Authentication (Appwrite)
    // -----------------------------------------------------------------------

    /**
     * Sign in with [email] and [password] against the official monochrome.tf
     * Appwrite instance ([APPWRITE_ENDPOINT]).
     *
     * [serverUrl] is the hifi-api base URL to associate with this session for
     * music streaming calls (e.g. `https://api.monochrome.tf`).
     *
     * On success the returned [MonochromeSession] should be persisted by the
     * caller (see [MonochromeAuthRepository]) and installed via [setSession].
     */
    suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession>

    /** Invalidate the current Appwrite session and clear local state. */
    suspend fun logout(): MonochromeResult<Unit>

    /**
     * Validate the existing [session] by fetching the current account from
     * Appwrite.  Returns an updated [MonochromeSession] with the latest
     * display-name on success, or an error if the session is expired/invalid.
     */
    suspend fun refreshSession(session: MonochromeSession): MonochromeResult<MonochromeSession>

    /** Install a previously-restored [MonochromeSession]. */
    fun setSession(session: MonochromeSession?)

    /** Returns the current session, or `null` when signed out. */
    fun getSession(): MonochromeSession?

    // -----------------------------------------------------------------------
    // API endpoint (independent of login state)
    // -----------------------------------------------------------------------

    /**
     * Update the hifi-api Base URL used for all catalog and streaming calls.
     *
     * The change takes effect immediately for subsequent requests.  The caller
     * is responsible for persisting [url] to DataStore so the value survives
     * restarts (see [com.nohimazin.monotune.monochrome.MonochromeAuthRepository.saveApiEndpoint]).
     *
     * @param url Base URL of the hifi-api instance (e.g. `https://api.monochrome.tf`).
     *            Trailing slashes are trimmed automatically.  An empty string resets
     *            the value to [DEFAULT_MONOCHROME_API_URL].
     */
    fun setApiEndpoint(url: String)

    /** Returns the currently configured hifi-api Base URL. */
    fun getApiEndpoint(): String

    // -----------------------------------------------------------------------
    // Catalog (hifi-api)
    // -----------------------------------------------------------------------

    /**
     * Search the TIDAL catalog (via hifi-api) for tracks matching [query].
     *
     * Uses `GET {serverUrl}/search/?s={query}`.
     */
    suspend fun search(query: String): MonochromeResult<List<MonochromeTrack>>

    /**
     * Fetch metadata for a single TIDAL track by its numeric [tidalId].
     *
     * Uses `GET {serverUrl}/info/?id={tidalId}`.
     */
    suspend fun getTrack(tidalId: String): MonochromeResult<MonochromeTrack>

    /**
     * Resolve a playback stream URL for [tidalId].
     *
     * Calls `GET {streamingUrl}/track/?id={tidalId}&quality={quality}`, decodes
     * the base64 manifest, and returns either:
     * - a direct audio URL (for `application/vnd.tidal.bts` manifests), or
     * - a `data:application/dash+xml;base64,c` URI (for MPEG-DASH manifests)
     *   that can be passed directly to ExoPlayer.
     *
     * @param tidalId The TIDAL track ID.
     * @param quality Audio quality token; defaults to `LOSSLESS`.
     *                Valid values: `HI_RES_LOSSLESS`, `LOSSLESS`, `HIGH`, `LOW`.
     */
    suspend fun resolveStreamUrl(
        tidalId: String,
        quality: String = "LOSSLESS",
    ): MonochromeResult<String>

    /**
     * Check whether [tidalId] is currently streamable.
     *
     * Returns `true` when the `/info/` response has `allowStreaming == true`
     * and `streamReady == true`.
     */
    suspend fun isAvailable(tidalId: String): Boolean

    // -----------------------------------------------------------------------
    // Lyrics (LRCLib)
    // -----------------------------------------------------------------------

    /**
     * Fetch lyrics for a track from LRCLib (`https://lrclib.net`).
     *
     * Returns `null` when no lyrics entry was found (HTTP 404).
     *
     * @param title    Track title.
     * @param artist   Primary artist name.
     * @param album    Album title (improves match accuracy).
     * @param duration Track duration in seconds (improves match accuracy).
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        album: String? = null,
        duration: Int? = null,
    ): MonochromeResult<MonochromeLyrics?>
}

// ---------------------------------------------------------------------------
// Implementation
// ---------------------------------------------------------------------------

/**
 * OkHttp-based implementation of [MonochromeClientApi].
 *
 * **Auth** is performed against the official monochrome.tf Appwrite instance
 * ([APPWRITE_ENDPOINT]); the Appwrite session token is extracted from the
 * `Set-Cookie` response header and stored in-memory (see [MonochromeSession]).
 *
 * **Music API** calls target the hifi-api Base URL controlled by [_apiEndpoint],
 * which can be updated at any time via [setApiEndpoint] without requiring a login.
 * It defaults to [DEFAULT_MONOCHROME_API_URL].
 *
 * **Lyrics** are fetched from LRCLib ([LRCLIB_API_URL]).
 */
@Singleton
class MonochromeClient @Inject constructor() : MonochromeClientApi {

    private val TAG = "MonochromeClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentSession: MonochromeSession? = null

    /** Configured hifi-api Base URL, independent of login state. */
    @Volatile
    private var _apiEndpoint: String = DEFAULT_MONOCHROME_API_URL

    override fun setApiEndpoint(url: String) {
        _apiEndpoint = url.trimEnd('/').ifEmpty { DEFAULT_MONOCHROME_API_URL }
    }

    override fun getApiEndpoint(): String = _apiEndpoint

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    override suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$APPWRITE_ENDPOINT/account/sessions/email")
                .addHeader("X-Appwrite-Project", APPWRITE_PROJECT_ID)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseAppwriteError(responseBody, response.code)
                return@withContext MonochromeResult.Error(errorMsg)
            }

            // Extract session token from the Set-Cookie header.
            // Appwrite sets: "a_session_{projectId}={token}; Path=/; ..."
            val sessionCookie = response.headers("Set-Cookie")
                .firstOrNull { it.startsWith("a_session_") }
                ?.split(";")?.firstOrNull()
                ?.substringAfter("=")
                .orEmpty()

            if (sessionCookie.isEmpty()) {
                Log.w(TAG, "Login succeeded but no session cookie found in response headers")
            }

            // Fetch user info to get display name.
            val displayName = fetchDisplayName(sessionCookie) ?: email

            val session = MonochromeSession(
                email = email,
                authToken = sessionCookie,
                displayName = displayName,
                serverUrl = serverUrl.trimEnd('/').ifEmpty { DEFAULT_MONOCHROME_API_URL },
                expiresAt = parseExpiry(responseBody),
            )
            currentSession = session
            MonochromeResult.Success(session)
        } catch (e: IOException) {
            Log.e(TAG, "login network error", e)
            MonochromeResult.Error("Network error: ${e.message}", e)
        } catch (e: JSONException) {
            Log.e(TAG, "login JSON parse error", e)
            MonochromeResult.Error("Unexpected response from server", e)
        }
    }

    override suspend fun logout(): MonochromeResult<Unit> = withContext(Dispatchers.IO) {
        val token = currentSession?.authToken
        currentSession = null
        if (token.isNullOrEmpty()) return@withContext MonochromeResult.Success(Unit)

        try {
            val request = Request.Builder()
                .url("$APPWRITE_ENDPOINT/account/sessions/current")
                .addHeader("X-Appwrite-Project", APPWRITE_PROJECT_ID)
                .addHeader("X-Appwrite-Session", token)
                .delete()
                .build()
            httpClient.newCall(request).execute().close()
        } catch (e: IOException) {
            Log.w(TAG, "logout request failed (session may already be invalid)", e)
        }
        MonochromeResult.Success(Unit)
    }

    override suspend fun refreshSession(
        session: MonochromeSession,
    ): MonochromeResult<MonochromeSession> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$APPWRITE_ENDPOINT/account")
                .addHeader("X-Appwrite-Project", APPWRITE_PROJECT_ID)
                .addHeader("X-Appwrite-Session", session.authToken)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseAppwriteError(responseBody, response.code)
                return@withContext MonochromeResult.Error(errorMsg)
            }

            val json = JSONObject(responseBody)
            val displayName = json.optString("name").ifEmpty { session.email }
            val refreshed = session.copy(displayName = displayName)
            currentSession = refreshed
            MonochromeResult.Success(refreshed)
        } catch (e: IOException) {
            Log.e(TAG, "refreshSession network error", e)
            MonochromeResult.Error("Network error: ${e.message}", e)
        } catch (e: JSONException) {
            Log.e(TAG, "refreshSession JSON parse error", e)
            MonochromeResult.Error("Unexpected response from server", e)
        }
    }

    override fun setSession(session: MonochromeSession?) {
        currentSession = session
    }

    override fun getSession(): MonochromeSession? = currentSession

    // -----------------------------------------------------------------------
    // Catalog
    // -----------------------------------------------------------------------

    override suspend fun search(query: String): MonochromeResult<List<MonochromeTrack>> =
        withContext(Dispatchers.IO) {
            val apiUrl = _apiEndpoint
            try {
                val request = Request.Builder()
                    .url("$apiUrl/search/?s=${query.encodeUrlParam()}")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext MonochromeResult.Error(
                        "Search failed (HTTP ${response.code})"
                    )
                }

                val items = JSONObject(body)
                    .optJSONObject("data")
                    ?.optJSONArray("items")
                    ?: return@withContext MonochromeResult.Success(emptyList())

                val tracks = buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        parseTrackJson(item)?.let { add(it) }
                    }
                }
                MonochromeResult.Success(tracks)
            } catch (e: IOException) {
                Log.e(TAG, "search network error", e)
                MonochromeResult.Error("Network error: ${e.message}", e)
            } catch (e: JSONException) {
                Log.e(TAG, "search JSON parse error", e)
                MonochromeResult.Error("Unexpected response from server", e)
            }
        }

    override suspend fun getTrack(tidalId: String): MonochromeResult<MonochromeTrack> =
        withContext(Dispatchers.IO) {
            val apiUrl = _apiEndpoint
            try {
                val request = Request.Builder()
                    .url("$apiUrl/info/?id=$tidalId")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.code == 404) {
                    return@withContext MonochromeResult.Error("Track $tidalId not found")
                }
                if (!response.isSuccessful) {
                    return@withContext MonochromeResult.Error(
                        "Failed to fetch track (HTTP ${response.code})"
                    )
                }

                val data = JSONObject(body).optJSONObject("data")
                    ?: return@withContext MonochromeResult.Error("Missing 'data' in response")

                val track = parseTrackJson(data)
                    ?: return@withContext MonochromeResult.Error("Failed to parse track data")
                MonochromeResult.Success(track)
            } catch (e: IOException) {
                Log.e(TAG, "getTrack network error", e)
                MonochromeResult.Error("Network error: ${e.message}", e)
            } catch (e: JSONException) {
                Log.e(TAG, "getTrack JSON parse error", e)
                MonochromeResult.Error("Unexpected response from server", e)
            }
        }

    override suspend fun resolveStreamUrl(
        tidalId: String,
        quality: String,
    ): MonochromeResult<String> = withContext(Dispatchers.IO) {
        // Use the dedicated streaming endpoint if using the default official API.
        // The official API (api.monochrome.tf) disables /track/ streaming to save bandwidth.
        val streamingUrl = if (_apiEndpoint == DEFAULT_MONOCHROME_API_URL) {
            DEFAULT_MONOCHROME_STREAMING_URL
        } else {
            _apiEndpoint
        }
        try {
            val request = Request.Builder()
                .url("$streamingUrl/track/?id=$tidalId&quality=$quality")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext MonochromeResult.Error(
                    "Stream resolve failed (HTTP ${response.code})"
                )
            }

            val data = JSONObject(body).optJSONObject("data")
                ?: return@withContext MonochromeResult.Error("Missing 'data' in stream response")

            val mimeType = data.optString("manifestMimeType")
            val manifestBase64 = data.optString("manifest")
            if (manifestBase64.isEmpty()) {
                return@withContext MonochromeResult.Error("Empty manifest in stream response")
            }

            val manifestBytes = Base64.decode(manifestBase64, Base64.DEFAULT)
            val url = when (mimeType) {
                "application/vnd.tidal.bts" -> {
                    // BTS manifest: base64-encoded JSON with a "urls" array.
                    val manifestJson = JSONObject(String(manifestBytes))
                    manifestJson.optJSONArray("urls")
                        ?.optString(0)
                        ?: return@withContext MonochromeResult.Error(
                            "No URL in BTS manifest"
                        )
                }
                "application/dash+xml" -> {
                    // MPEG-DASH manifest: pass as a data URI so ExoPlayer can handle it.
                    "data:application/dash+xml;base64,$manifestBase64"
                }
                else -> {
                    // Unknown manifest type ? return as data URI and let the player decide.
                    Log.w(TAG, "Unknown manifest MIME type: $mimeType")
                    "data:$mimeType;base64,$manifestBase64"
                }
            }
            MonochromeResult.Success(url)
        } catch (e: IOException) {
            Log.e(TAG, "resolveStreamUrl network error", e)
            MonochromeResult.Error("Network error: ${e.message}", e)
        } catch (e: JSONException) {
            Log.e(TAG, "resolveStreamUrl JSON parse error", e)
            MonochromeResult.Error("Unexpected response from server", e)
        }
    }

    override suspend fun isAvailable(tidalId: String): Boolean {
        return when (val result = getTrack(tidalId)) {
            is MonochromeResult.Success -> {
                // Track is available if both flags are true (reflected in the fact that
                // parseTrackJson only returns a track when streamReady is true).
                true
            }
            is MonochromeResult.Error -> false
        }
    }

    // -----------------------------------------------------------------------
    // Lyrics
    // -----------------------------------------------------------------------

    override suspend fun getLyrics(
        title: String,
        artist: String,
        album: String?,
        duration: Int?,
    ): MonochromeResult<MonochromeLyrics?> = withContext(Dispatchers.IO) {
        try {
            val params = buildString {
                append("artist_name=${artist.encodeUrlParam()}")
                append("&track_name=${title.encodeUrlParam()}")
                if (!album.isNullOrEmpty()) append("&album_name=${album.encodeUrlParam()}")
                if (duration != null) append("&duration=$duration")
            }

            val request = Request.Builder()
                .url("$LRCLIB_API_URL?$params")
                .addHeader("Lrclib-Client", "MonoTune v1 (github.com/nohimazin/MonoTune)")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.code == 404) {
                return@withContext MonochromeResult.Success(null)
            }
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext MonochromeResult.Error(
                    "Lyrics fetch failed (HTTP ${response.code})"
                )
            }

            val json = JSONObject(body)
            val lyrics = MonochromeLyrics(
                trackName = json.optString("trackName"),
                artistName = json.optString("artistName"),
                plainLyrics = json.optString("plainLyrics").ifEmpty { null },
                syncedLyrics = json.optString("syncedLyrics").ifEmpty { null },
                instrumental = json.optBoolean("instrumental", false),
            )
            MonochromeResult.Success(lyrics)
        } catch (e: IOException) {
            Log.e(TAG, "getLyrics network error", e)
            MonochromeResult.Error("Network error: ${e.message}", e)
        } catch (e: JSONException) {
            Log.e(TAG, "getLyrics JSON parse error", e)
            MonochromeResult.Error("Unexpected response from lyrics server", e)
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Parse a TIDAL track JSON object (from hifi-api `/search/` or `/info/` response)
     * into a [MonochromeTrack].
     *
     * Returns `null` when the track is not streamable (`allowStreaming == false` or
     * `streamReady == false`).
     */
    private fun parseTrackJson(json: JSONObject): MonochromeTrack? {
        if (!json.optBoolean("allowStreaming", true)) return null
        if (!json.optBoolean("streamReady", true)) return null

        val id = json.optInt("id", -1)
        if (id < 0) return null

        val artistName = json.optJSONObject("artist")?.optString("name")
            ?: json.optJSONArray("artists")?.optJSONObject(0)?.optString("name")
            ?: "Unknown Artist"

        val album = json.optJSONObject("album")
        val albumTitle = album?.optString("title")
        val coverArtId = album?.optString("cover")

        return MonochromeTrack(
            monochromeId = id.toString(),
            title = json.optString("title", "Unknown"),
            artist = artistName,
            album = albumTitle,
            durationSecs = json.optInt("duration", -1),
            coverArtId = coverArtId?.ifEmpty { null },
            isrc = json.optString("isrc").ifEmpty { null },
        )
    }

    /**
     * Fetch the Appwrite account display name for [sessionToken].
     * Returns `null` on failure (non-fatal; email will be used as fallback).
     */
    private fun fetchDisplayName(sessionToken: String): String? {
        if (sessionToken.isEmpty()) return null
        return try {
            val request = Request.Builder()
                .url("$APPWRITE_ENDPOINT/account")
                .addHeader("X-Appwrite-Project", APPWRITE_PROJECT_ID)
                .addHeader("X-Appwrite-Session", sessionToken)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            JSONObject(body).optString("name").ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "fetchDisplayName failed", e)
            null
        }
    }

    /** Extract the `expire` field from an Appwrite session response as epoch millis. */
    private fun parseExpiry(responseBody: String): Long? = try {
        val expire = JSONObject(responseBody).optString("expire")
        if (expire.isEmpty()) null
        else java.time.Instant.parse(expire).toEpochMilli()
    } catch (e: Exception) {
        null
    }

    /**
     * Parse an Appwrite error response body into a human-readable message.
     *
     * Appwrite error format: `{"message": "...", "code": ..., "type": "..."}`
     */
    private fun parseAppwriteError(body: String, httpCode: Int): String = try {
        JSONObject(body).optString("message").ifEmpty { "Request failed (HTTP $httpCode)" }
    } catch (e: JSONException) {
        "Request failed (HTTP $httpCode)"
    }

    /** URL-encode a query parameter value. */
    private fun String.encodeUrlParam(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}

