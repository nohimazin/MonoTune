/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nohimazin.monotune.LocalPlayerAwareWindowInsets
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.TopBarInsets
import com.nohimazin.monotune.ui.dialog.InfoLabel
import com.nohimazin.monotune.ui.component.button.IconButton
import com.nohimazin.monotune.ui.utils.backToMain
import com.nohimazin.monotune.viewmodels.MonochromeLoginViewModel

/**
 * Login / account screen for the Monochrome streaming backend.
 *
 * When a session is already active the screen shows the signed-in state and
 * a logout button.  When no session is present, an email/password form is shown.
 *
 * The **API endpoint** (hifi-api Base URL) is configured independently in the
 * Account & Sync settings and is not part of the login flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonochromeLoginScreen(
    navController: NavController,
    viewModel: MonochromeLoginViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsState()
    val apiEndpoint by viewModel.apiEndpoint.collectAsState()
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Clear error when input changes
    LaunchedEffect(email, password) { viewModel.clearError() }

    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar space (TopAppBar is drawn separately below)
        Spacer(Modifier.height(64.dp))

        when (val activeSession = session) {
            null -> {
                // ----------------------------------------------------------------
                // Login form
                // ----------------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Info note
                    InfoLabel(stringResource(R.string.monochrome_backend_note))

                    Spacer(Modifier.height(4.dp))

                    // Show the currently configured endpoint so the user knows
                    // which instance will be used.  The endpoint itself is
                    // configured in Settings > Account & Sync.
                    InfoLabel(
                        stringResource(
                            R.string.monochrome_login_endpoint_note,
                            apiEndpoint,
                        )
                    )

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.monochrome_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.monochrome_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (!viewModel.isLoading) {
                                    viewModel.login(email, password)
                                }
                            },
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Error message
                    if (viewModel.loginError != null) {
                        Text(
                            text = stringResource(
                                R.string.monochrome_login_failed,
                                viewModel.loginError.orEmpty(),
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator()
                        } else {
                            Button(
                                onClick = { viewModel.login(email, password) },
                                enabled = email.isNotBlank() && password.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.monochrome_login_button))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
            else -> {
                // ----------------------------------------------------------------
                // Logged-in state
                // ----------------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = activeSession.displayName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = activeSession.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = activeSession.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.monochrome_logout))
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.monochrome_login)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
        },
        windowInsets = TopBarInsets,
    )
}

