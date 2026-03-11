/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.monochrome

import android.content.Context
import androidx.datastore.preferences.core.edit
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
 * in sync so that outgoing requests always carry a valid bearer token.
 *
 * The [login] implementation is a **stub** — it delegates to [MonochromeClientApi.login]
 * which currently returns an error.  Once the real API contract is available,
 * only [MonochromeClient] needs to be updated; the repository layer remains stable.
 */
interface MonochromeAuthRepository {

    /**
     * Reactive stream of the current session.  Emits `null` when no user is
     * logged in, or after a successful [logout].
     */
    val sessionFlow: Flow<MonochromeSession?>

    /**
     * Attempt to sign in with [email] and [password] against [serverUrl].
     *
     * On success the session is persisted in DataStore and installed in the
     * [MonochromeClientApi] instance.
     *
     * @return [MonochromeResult.Success] with the new session, or
     *         [MonochromeResult.Error] with a human-readable message.
     */
    suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession>

    /** Sign out: clear persisted session and reset the client. */
    suspend fun logout()

    /**
     * Restore a previously-persisted session from DataStore and install it in
     * the [MonochromeClientApi].  Call this once at app start-up.
     *
     * @return The restored session, or `null` if no session was saved.
     */
    suspend fun restoreSession(): MonochromeSession?
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
            if (token.isNotEmpty() && email.isNotEmpty() && url.isNotEmpty()) {
                MonochromeSession(
                    email = email,
                    authToken = token,
                    displayName = name.ifEmpty { email },
                    serverUrl = url,
                    expiresAt = expiry,
                )
            } else {
                null
            }
        }

    override suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): MonochromeResult<MonochromeSession> {
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
        }
    }

    override suspend fun restoreSession(): MonochromeSession? {
        val token = context.dataStore[MonochromeAuthTokenKey].orEmpty()
        val email = context.dataStore[MonochromeEmailKey].orEmpty()
        val name = context.dataStore[MonochromeDisplayNameKey].orEmpty()
        val url = context.dataStore[MonochromeServerUrlKey].orEmpty()
        val expiry = context.dataStore[MonochromeTokenExpiryKey]

        if (token.isEmpty() || email.isEmpty() || url.isEmpty()) return null

        val session = MonochromeSession(
            email = email,
            authToken = token,
            displayName = name.ifEmpty { email },
            serverUrl = url,
            expiresAt = expiry,
        )
        client.setSession(session)
        return session
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
