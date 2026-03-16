/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.nohimazin.monotune.ui.component.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nohimazin.monotune.BuildConfig
import com.nohimazin.monotune.LocalDatabase
import com.nohimazin.monotune.LocalDownloadUtil
import com.nohimazin.monotune.LocalMenuState
import com.nohimazin.monotune.LocalPlayerConnection
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.GridThumbnailHeight
import com.nohimazin.monotune.constants.ListThumbnailSize
import com.nohimazin.monotune.constants.ThumbnailCornerRadius
import com.nohimazin.monotune.db.entities.Playlist
import com.nohimazin.monotune.db.entities.PlaylistSong
import com.nohimazin.monotune.db.entities.Song
import com.nohimazin.monotune.extensions.toMediaItem
import com.nohimazin.monotune.extensions.togglePlayPause
import com.nohimazin.monotune.ui.component.PlayingIndicatorBox
import com.nohimazin.monotune.ui.component.SwipeToQueueBox
import com.nohimazin.monotune.ui.component.button.IconButton
import com.nohimazin.monotune.ui.menu.MenuState
import com.nohimazin.monotune.ui.menu.SongMenu
import com.nohimazin.monotune.utils.joinByBullet
import com.nohimazin.monotune.utils.makeTimeString
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.nohimazin.monotune.viewmodels.MonochromeMatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    albumIndex: Int? = null,
    playlistSong: PlaylistSong? = null,
    playlist: Playlist? = null,
    navController: NavController,
    snackbarHostState: SnackbarHostState? = null,

    isActive: Boolean,
    isPlaying: Boolean,
    inSelectMode: Boolean?,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    swipeEnabled: Boolean,

    showMenu: Boolean = true,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = true,
    showDownloadIcon: Boolean = true,
    matchStatus: MonochromeMatchStatus? = null,
    matchConfidence: Float? = null,

    thumbnailSize: Int,
    onPlay: () -> Unit,
    dragHandleModifier: Modifier? = null,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val listItem: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            subtitle = joinByBullet(
                (if (BuildConfig.DEBUG) song.song.id else ""),
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L)
            ),
            badges = {
                if (showLikedIcon && song.song.liked) {
                    Icon.Favorite()
                }
                if (showInLibraryIcon && song.song.inLibrary != null) {
                    Icon.Library()
                }
                if (showDownloadIcon) {
                    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
                    Icon.Download(download)
                }
                if (matchStatus != null) {
                    val labelText = when {
                        matchStatus == MonochromeMatchStatus.MATCHED_AUTO && matchConfidence != null ->
                            "${stringResource(R.string.monochrome_status_auto)} ${(matchConfidence * 100).toInt()}%"
                        else -> stringResource(
                            when (matchStatus) {
                                MonochromeMatchStatus.MATCHED_AUTO -> R.string.monochrome_status_auto
                                MonochromeMatchStatus.MATCHED_MANUAL -> R.string.monochrome_status_manual
                                MonochromeMatchStatus.EXPLICITLY_UNAVAILABLE -> R.string.monochrome_status_unavailable
                                MonochromeMatchStatus.UNRESOLVED -> R.string.monochrome_status_unresolved
                            }
                        )
                    }
                    val labelColor = when (matchStatus) {
                        MonochromeMatchStatus.MATCHED_AUTO,
                        MonochromeMatchStatus.MATCHED_MANUAL -> MaterialTheme.colorScheme.primary
                        MonochromeMatchStatus.EXPLICITLY_UNAVAILABLE,
                        MonochromeMatchStatus.UNRESOLVED -> MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = labelText,
                        color = labelColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            },
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl,
                    preferredSize = thumbnailSize,
                    albumIndex = albumIndex,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = {
                if (inSelectMode == true) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = onSelectedChange
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (showMenu) {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        playlistSong = playlistSong,
                                        playlist = playlist,
                                        navController = navController,
                                        matchStatus = matchStatus,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }

                            haptic.performHapticFeedback(HapticFeedbackType.Companion.ContextClick)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = null
                        )
                    }
                }

                if (dragHandleModifier != null) {
                    IconButton(
                        onClick = { },
                        modifier = dragHandleModifier
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = null
                        )
                    }
                }
            },
            isSelected = inSelectMode == true && isSelected,
            isActive = isActive,
            modifier = modifier.combinedClickable(
                onClick = {
                    if (inSelectMode == true) {
                        onSelectedChange(!isSelected)
                    } else if (isActive) {
                        playerConnection.player.togglePlayPause()
                    } else {
                        onPlay()
                    }
                },
                onLongClick = {
                    if (inSelectMode == null) {
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    } else if (!inSelectMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.Companion.LongPress)
                        onSelectedChange(true)
                    }
                }
            )
        )
    }

    SwipeToQueueBox(
        item = song.toMediaItem(),
        content = { listItem() },
        snackbarHostState = snackbarHostState,
        swipeEnabled = swipeEnabled
    )
}

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon(
                painter = painterResource(R.drawable.library_add_check),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
            Icon.Download(download)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = song.song.title,
    subtitle = joinByBullet(
        song.artists.joinToString { it.name },
        makeTimeString(song.song.duration * 1000L)
    ),
    badges = badges,
    thumbnailContent = {
        val density = LocalDensity.current
        val px = (GridThumbnailHeight.value * density.density).roundToInt()
        Box(
            contentAlignment = Alignment.Companion.Center,
            modifier = Modifier.size(GridThumbnailHeight)
        ) {
            AsyncImage(
                model = song.song.getThumbnailModel(px, px),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
            PlayingIndicatorBox(
                isActive = isActive,
                playWhenReady = isPlaying,
                color = Color.Companion.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color.Companion.Black.copy(alpha = ActiveBoxAlpha),
                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                    )
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)
