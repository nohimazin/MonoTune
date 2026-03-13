/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.monochrome

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.dd3boh.outertune.constants.MonochromeApiEndpointKey
import com.dd3boh.outertune.constants.MonochromeAuthTokenKey
import com.dd3boh.outertune.constants.MonochromeDisplayNameKey
import com.dd3boh.outertune.constants.MonochromeEmailKey
import com.dd3boh.outertune.constants.MonochromeServerUrlKey
import com.dd3boh.outertune.constants.MonochromeTokenExpiryKey
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that manages the Monochrome account session lifecycle.
 *
 * Auth state is persisted in the app's DataStore so that the user remains
 * logged in across restarts.  The repository also keeps the [MonochromeClientApi]
 * in sync so that subsequent Appwrite calls carry a valid session token and
 * music-API calls target the correct hifi-api instance.
 *
 * The **API endpoint** ([MonochromeApiEndpointKey]) is stored independently of
 * the login session.  It can be configured and changed at any time via
 * [saveApiEndpoint] without logging in or out.
 *
 * The [login] flow:
 * 1. Calls [MonochromeClientApi.login] which authenticates against the Appwrite
 *    instance at [APPWRITE_ENDPOINT] and uses the currently-configured API endpoint.
 * 2. On success, persists the [MonochromeSession] to DataStore.
 * 3. Installs the session in the [MonochromeClientApi] singleton.
 */
interface MonochromeAuthRepository {

    /**
     * Reactive stream of the current session.  Emits `null` when no user is
     * logged in, or after a successful [logout].
     */
    val sessionFlow: Flow<MonochromeSession?>

    /**
     * Reactive stream of the configured Monochrome API endpoint URL.
     *
     * Emits the current value immediately and on every subsequent change made
     * via [saveApiEndpoint].  Never emits an empty string – falls back to
     * [DEFAULT_MONOCHROME_API_URL].
     */
    val apiEndpointFlow: Flow<String>

    /**
     * Attempt to sign in with [email] and [password].
     *
     * The hifi-api endpoint used for this session is taken from the value
     * previously saved via [saveApiEndpoint] (or the default if none was set).
     *
     * On success the session is persisted in DataStore and installed in the
     * [MonochromeClientApi] instance.
     *
     * @return [MonochromeResult.Success] with the new session, or
     *         [MonochromeResult.Error] with a human-readable message.
     */
    suspend fun login(
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession>

    /** Sign out: clear persisted session and reset the client. */
    suspend fun logout()

    /**
     * Restore a previously-persisted session from DataStore and install it in
     * the [MonochromeClientApi].  Call this once at app start-up.
     *
     * Also restores the API endpoint from [MonochromeApiEndpointKey] (or falls
     * back to [DEFAULT_MONOCHROME_API_URL] if none is saved yet).
     *
     * @return The restored session, or `null` if no session was saved.
     */
    suspend fun restoreSession(): MonochromeSession?

    /**
     * Persist [url] as the Monochrome API endpoint and apply it immediately to
     * the [MonochromeClientApi] singleton so that subsequent requests use the
     * new URL without requiring an app restart.
     *
     * [url] is normalised (trailing slash trimmed) before saving.  An empty
     * string resets the endpoint to [DEFAULT_MONOCHROME_API_URL].
     */
    suspend fun saveApiEndpoint(url: String)
}

@Singleton
class MonochromeAuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: MonochromeClientApi,
) : MonochromeAuthRepository {

    override val sessionFlow: Flow<MonochromeSession?> =
        context.dataStore.data.map { prefs ->
            val token = prefs[MonochromeAuthTokenKey].orEmpty()
            val email = prefs[MonochromeEmailKey].orEmpty()
            val name = prefs[MonochromeDisplayNameKey].orEmpty()
            val url = prefs[MonochromeServerUrlKey].orEmpty()
            val expiry = prefs[MonochromeTokenExpiryKey]
            if (token.isNotEmpty() && email.isNotEmpty()) {
                MonochromeSession(
                    email = email,
                    authToken = token,
                    displayName = name.ifEmpty { email },
                    serverUrl = url.ifEmpty { DEFAULT_MONOCHROME_API_URL },
                    expiresAt = expiry,
                )
            } else {
                null
            }
        }

    override val apiEndpointFlow: Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[MonochromeApiEndpointKey]?.ifEmpty { null }
                ?: DEFAULT_MONOCHROME_API_URL
        }

    override suspend fun login(
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession> {
        val serverUrl = client.getApiEndpoint()
        val result = client.login(serverUrl, email, password)
        if (result is MonochromeResult.Success) {
            persistSession(result.data)
            client.setSession(result.data)
        }
        return result
    }

    override suspend fun logout() {
        client.logout()
        client.setSession(null)
        context.dataStore.edit { prefs ->
            prefs.remove(MonochromeAuthTokenKey)
            prefs.remove(MonochromeEmailKey)
            prefs.remove(MonochromeDisplayNameKey)
            prefs.remove(MonochromeServerUrlKey)
            prefs.remove(MonochromeTokenExpiryKey)
            // MonochromeApiEndpointKey is intentionally NOT removed on logout –
            // the endpoint configuration is independent of the account session.
        }
    }

    override suspend fun restoreSession(): MonochromeSession? {
        // Always restore the API endpoint first, even if no session exists.
        val endpoint = context.dataStore[MonochromeApiEndpointKey]?.ifEmpty { null }
            ?: DEFAULT_MONOCHROME_API_URL
        client.setApiEndpoint(endpoint)

        // Now restore the auth session (if any).
        val token = context.dataStore[MonochromeAuthTokenKey].orEmpty()
        val email = context.dataStore[MonochromeEmailKey].orEmpty()
        val name = context.dataStore[MonochromeDisplayNameKey].orEmpty()
        val url = context.dataStore[MonochromeServerUrlKey].orEmpty()
        val expiry = context.dataStore[MonochromeTokenExpiryKey]

        if (token.isEmpty() || email.isEmpty()) return null

        val session = MonochromeSession(
            email = email,
            authToken = token,
            displayName = name.ifEmpty { email },
            serverUrl = url.ifEmpty { endpoint },
            expiresAt = expiry,
        )
        client.setSession(session)
        return session
    }

    override suspend fun saveApiEndpoint(url: String) {
        val normalised = url.trimEnd('/').ifEmpty { DEFAULT_MONOCHROME_API_URL }
        context.dataStore.edit { it[MonochromeApiEndpointKey] = normalised }
        client.setApiEndpoint(normalised)
    }

    // -------------------------------------------------------------------------

    private suspend fun persistSession(session: MonochromeSession) {
        context.dataStore.edit { prefs ->
            prefs[MonochromeAuthTokenKey] = session.authToken
            prefs[MonochromeEmailKey] = session.email
            prefs[MonochromeDisplayNameKey] = session.displayName
            prefs[MonochromeServerUrlKey] = session.serverUrl
            if (session.expiresAt != null) {
                prefs[MonochromeTokenExpiryKey] = session.expiresAt
            } else {
                prefs.remove(MonochromeTokenExpiryKey)
            }
        }
    }
}
