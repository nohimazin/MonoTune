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
 * ViewModel for the Monochrome login screen.
 *
 * Exposes the current session state reactively and handles login/logout actions.
 * All business logic lives here so the Compose screen stays thin.
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
     * @param serverUrl  Base URL of the Monochrome instance.
     * @param email      Account e-mail address.
     * @param password   Account password.
     * @param onSuccess  Called on the main thread when login succeeds.
     */
    fun login(
        serverUrl: String,
        email: String,
        password: String,
        onSuccess: (MonochromeSession) -> Unit = {},
    ) {
        viewModelScope.launch {
            isLoading = true
            loginError = null
            val result = authRepository.login(serverUrl.trimEnd('/'), email, password)
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
}
