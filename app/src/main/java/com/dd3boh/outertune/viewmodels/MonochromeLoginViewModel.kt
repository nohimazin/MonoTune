/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.monochrome.MonochromeAuthRepository
import com.dd3boh.outertune.monochrome.MonochromeResult
import com.dd3boh.outertune.monochrome.MonochromeSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Monochrome login screen and account settings fragment.
 *
 * Exposes the current session state and API endpoint reactively and handles
 * login/logout/endpoint actions.  All business logic lives here so the
 * Compose screens stay thin.
 */
@HiltViewModel
class MonochromeLoginViewModel @Inject constructor(
    private val authRepository: MonochromeAuthRepository,
) : ViewModel() {

    /** The currently active Monochrome session, or `null` when signed out. */
    val session = authRepository.sessionFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /**
     * The currently configured Monochrome API endpoint URL.
     *
     * Changes are applied immediately to the API client; no restart is needed.
     */
    val apiEndpoint = authRepository.apiEndpointFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.dd3boh.outertune.monochrome.DEFAULT_MONOCHROME_API_URL,
    )

    var isLoading by mutableStateOf(false)
        private set

    /** Non-null when a recent login attempt produced an error message. */
    var loginError by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        loginError = null
    }

    /**
     * Attempt to sign in with the given credentials.
     *
     * The hifi-api endpoint is taken from the value previously saved via
     * [saveEndpoint] (or the default if none was set).
     *
     * @param email      Account e-mail address.
     * @param password   Account password.
     * @param onSuccess  Called on the main thread when login succeeds.
     */
    fun login(
        email: String,
        password: String,
        onSuccess: (MonochromeSession) -> Unit = {},
    ) {
        viewModelScope.launch {
            isLoading = true
            loginError = null
            val result = authRepository.login(email, password)
            isLoading = false
            when (result) {
                is MonochromeResult.Success -> onSuccess(result.data)
                is MonochromeResult.Error -> loginError = result.message
            }
        }
    }

    /** Sign out the current Monochrome account. */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    /**
     * Persist [url] as the Monochrome API endpoint and apply it to the client
     * immediately.
     */
    fun saveEndpoint(url: String) {
        viewModelScope.launch {
            authRepository.saveApiEndpoint(url)
        }
    }
}
