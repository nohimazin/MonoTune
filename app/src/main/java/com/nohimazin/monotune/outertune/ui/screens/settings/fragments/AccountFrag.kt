/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.nohimazin.monotune.ui.screens.settings.fragments

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nohimazin.monotune.App.Companion.forgetAccount
import com.nohimazin.monotune.App.Companion.forgetMonochromeAccount
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.AccountChannelHandleKey
import com.nohimazin.monotune.constants.AccountEmailKey
import com.nohimazin.monotune.constants.AccountNameKey
import com.nohimazin.monotune.constants.DataSyncIdKey
import com.nohimazin.monotune.constants.InnerTubeCookieKey
import com.nohimazin.monotune.constants.UseLoginForBrowse
import com.nohimazin.monotune.constants.VisitorDataKey
import com.nohimazin.monotune.ui.component.EditTextPreference
import com.nohimazin.monotune.ui.component.PreferenceEntry
import com.nohimazin.monotune.ui.component.PreferenceGroupTitle
import com.nohimazin.monotune.ui.component.SwitchPreference
import com.nohimazin.monotune.ui.dialog.InfoLabel
import com.nohimazin.monotune.ui.dialog.TextFieldDialog
import com.nohimazin.monotune.utils.rememberPreference
import com.nohimazin.monotune.viewmodels.MonochromeLoginViewModel
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.utils.parseCookieString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.AccountFrag(navController: NavController) {
    val context = LocalContext.current

    val (accountName, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    // temp vars
    var showToken: Boolean by remember {
        mutableStateOf(false)
    }
    var showTokenEditor by remember {
        mutableStateOf(false)
    }

    PreferenceEntry(
        title = { Text(if (isLoggedIn) accountName else stringResource(R.string.login)) },
        description = if (isLoggedIn) {
            accountEmail.takeIf { it.isNotEmpty() }
                ?: accountChannelHandle.takeIf { it.isNotEmpty() }
        } else null,
        icon = { Icon(Icons.Rounded.Person, null) },
        onClick = { navController.navigate("login") }
    )
    if (isLoggedIn) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.action_logout)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            onClick = {
                forgetAccount(context)
            }
        )
        Spacer(Modifier.height(8.dp))
        InfoLabel(stringResource(R.string.action_logout_tooltip))
        Spacer(Modifier.height(24.dp))
    }

    PreferenceEntry(
        title = {
            if (showToken) {
                Text(stringResource(R.string.token_shown))
                Text(
                    text = if (isLoggedIn) innerTubeCookie else stringResource(R.string.not_logged_in),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1 // just give a preview so user knows it's at least there
                )
            } else {
                Text(stringResource(R.string.token_hidden))
            }
        },
        onClick = {
            if (showToken == false) {
                showToken = true
            } else {
                showTokenEditor = true
            }
        },
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showTokenEditor) {
        val text =
            "***INNERTUBE COOKIE*** =${innerTubeCookie}\n\n***VISITOR DATA*** =${visitorData}\n\n***DATASYNC ID*** =${dataSyncId}\n\n***ACCOUNT NAME*** =${accountName}\n\n***ACCOUNT EMAIL*** =${accountEmail}\n\n***ACCOUNT CHANNEL HANDLE*** =${accountChannelHandle}"
        TextFieldDialog(
            modifier = Modifier,
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                data.split("\n").forEach {
                    if (it.startsWith("***INNERTUBE COOKIE*** =")) {
                        onInnerTubeCookieChange(it.substringAfter("***INNERTUBE COOKIE*** ="))
                    } else if (it.startsWith("***VISITOR DATA*** =")) {
                        onVisitorDataChange(it.substringAfter("***VISITOR DATA*** ="))
                    } else if (it.startsWith("***DATASYNC ID*** =")) {
                        onDataSyncIdChange(it.substringAfter("***DATASYNC ID*** ="))
                    } else if (it.startsWith("***ACCOUNT NAME*** =")) {
                        onAccountNameChange(it.substringAfter("***ACCOUNT NAME*** ="))
                    } else if (it.startsWith("***ACCOUNT EMAIL*** =")) {
                        onAccountEmailChange(it.substringAfter("***ACCOUNT EMAIL*** ="))
                    } else if (it.startsWith("***ACCOUNT CHANNEL HANDLE*** =")) {
                        onAccountChannelHandleChange(it.substringAfter("***ACCOUNT CHANNEL HANDLE*** ="))
                    }
                }
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = {
                it.isNotEmpty() &&
                        try {
                            "SAPISID" in parseCookieString(it)
                            true
                        } catch (e: Exception) {
                            false
                        }
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            }
        )
    }
}

@Composable
fun ColumnScope.AccountExtrasFrag() {
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)

    SwitchPreference(
        title = { Text(stringResource(R.string.use_login_for_browse)) },
        description = stringResource(R.string.use_login_for_browse_desc),
        icon = { Icon(Icons.Rounded.Person, null) },
        checked = useLoginForBrowse,
        onCheckedChange = {
            YouTube.useLoginForBrowse = it
            onUseLoginForBrowseChange(it)
        }
    )
}

/**
 * Settings fragment that shows the Monochrome account status and provides
 * a navigation entry point to [MonochromeLoginScreen].
 *
 * When a session is active the user's display name and server URL are shown.
 * A "Log out" entry is also shown so the user can sign out without navigating
 * to the dedicated login screen.
 *
 * The **API endpoint** (hifi-api Base URL) is configured here independently of
 * login state, so users can change it without signing in or out.
 */
@Composable
fun ColumnScope.MonochromeAccountFrag(
    navController: NavController,
    viewModel: MonochromeLoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val apiEndpoint by viewModel.apiEndpoint.collectAsState()

    PreferenceGroupTitle(title = stringResource(R.string.monochrome_account))

    // ------------------------------------------------------------------
    // API endpoint – independent of login state
    // ------------------------------------------------------------------
    EditTextPreference(
        title = { Text(stringResource(R.string.monochrome_api_endpoint)) },
        icon = { Icon(Icons.Rounded.Link, null) },
        value = apiEndpoint,
        onValueChange = { viewModel.saveEndpoint(it) },
        isInputValid = { it.startsWith("http://") || it.startsWith("https://") },
    )

    // ------------------------------------------------------------------
    // Login / account entry
    // ------------------------------------------------------------------
    PreferenceEntry(
        title = {
            Text(session?.displayName ?: stringResource(R.string.monochrome_not_logged_in))
        },
        description = session?.email,
        icon = { Icon(Icons.Rounded.CloudQueue, null) },
        onClick = { navController.navigate("monochrome_login") },
    )

    if (session != null) {
        PreferenceEntry(
            title = { Text(stringResource(R.string.monochrome_logout)) },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            onClick = {
                viewModel.logout()
                forgetMonochromeAccount(context)
            },
        )
        Spacer(Modifier.height(8.dp))
        InfoLabel(stringResource(R.string.action_logout_tooltip))
    }
}

