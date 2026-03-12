/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.monochrome.MonochromeTrack
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.viewmodels.MonochromeFixMatchViewModel

/**
 * Full-screen-ish dialog that lets the user search Monochrome for a candidate
 * track for the given [song] and save the result as a [ManualCorrection].
 *
 * On success [onSaved] is called so the caller can also dismiss the parent menu.
 */
@Composable
fun MonochromeFixMatchDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: MonochromeFixMatchViewModel = hiltViewModel(),
) {
    val defaultQuery = remember(song.song.id) {
        "${song.song.title} ${song.artists.firstOrNull()?.name ?: ""}".trim()
    }

    var searchQuery by remember(song.song.id) { mutableStateOf(defaultQuery) }
    var selectedTrack by remember(song.song.id) { mutableStateOf<MonochromeTrack?>(null) }
    val searchState = viewModel.searchState

    // Reset state and kick off the initial search each time this dialog appears.
    LaunchedEffect(song.song.id) {
        viewModel.resetSearch()
        if (defaultQuery.isNotEmpty()) {
            viewModel.search(defaultQuery)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // ── Title ────────────────────────────────────────────────────
                Text(
                    text = stringResource(R.string.monochrome_fix_match),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(Modifier.height(4.dp))

                // ── Song info ────────────────────────────────────────────────
                Text(
                    text = song.song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (song.artists.isNotEmpty()) {
                    Text(
                        text = song.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Search box ───────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.monochrome_fix_match_search_hint),
                                maxLines = 1,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.search(searchQuery) }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Results area ─────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                ) {
                    when (val state = searchState) {
                        is MonochromeFixMatchViewModel.SearchState.Idle -> Unit

                        is MonochromeFixMatchViewModel.SearchState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        }

                        is MonochromeFixMatchViewModel.SearchState.Error -> {
                            Text(
                                text = stringResource(R.string.monochrome_fix_match_error, state.message),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        is MonochromeFixMatchViewModel.SearchState.Success -> {
                            if (state.results.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.monochrome_fix_match_no_results),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(
                                        items = state.results,
                                        key = { it.monochromeId },
                                    ) { track ->
                                        TrackResultItem(
                                            track = track,
                                            isSelected = selectedTrack?.monochromeId == track.monochromeId,
                                            onClick = {
                                                selectedTrack =
                                                    if (selectedTrack?.monochromeId == track.monochromeId) null
                                                    else track
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Action buttons ───────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // "Mark unavailable" – left-aligned tertiary action
                    TextButton(
                        onClick = {
                            viewModel.saveCorrection(
                                ytmId = song.song.id,
                                selectedTrack = null,
                                onComplete = onSaved,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.monochrome_mark_unavailable),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Cancel
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(android.R.string.cancel))
                    }

                    Spacer(Modifier.width(8.dp))

                    // Save
                    Button(
                        onClick = {
                            viewModel.saveCorrection(
                                ytmId = song.song.id,
                                selectedTrack = selectedTrack,
                                onComplete = onSaved,
                            )
                        },
                        enabled = selectedTrack != null,
                    ) {
                        Text(text = stringResource(R.string.monochrome_fix_match_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackResultItem(
    track: MonochromeTrack,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(track.artist)
                if (track.durationSecs > 0) {
                    append(" · ")
                    append(makeTimeString(track.durationSecs * 1000L))
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
