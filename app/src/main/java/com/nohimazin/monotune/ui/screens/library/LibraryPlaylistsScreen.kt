package com.nohimazin.monotune.ui.screens.library

import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nohimazin.monotune.LocalMenuState
import com.nohimazin.monotune.LocalPlayerAwareWindowInsets
import com.nohimazin.monotune.MainActivity
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.CONTENT_TYPE_HEADER
import com.nohimazin.monotune.constants.CONTENT_TYPE_PLAYLIST
import com.nohimazin.monotune.constants.GridThumbnailHeight
import com.nohimazin.monotune.constants.LibraryViewType
import com.nohimazin.monotune.constants.LibraryViewTypeKey
import com.nohimazin.monotune.constants.PlaylistFilter
import com.nohimazin.monotune.constants.PlaylistFilterKey
import com.nohimazin.monotune.constants.PlaylistSortDescendingKey
import com.nohimazin.monotune.constants.PlaylistSortType
import com.nohimazin.monotune.constants.PlaylistSortTypeKey
import com.nohimazin.monotune.constants.PlaylistViewTypeKey
import com.nohimazin.monotune.constants.ShowLikedAndDownloadedPlaylist
import com.nohimazin.monotune.db.entities.PlaylistEntity
import com.nohimazin.monotune.ui.component.ChipsRow
import com.nohimazin.monotune.ui.component.EmptyPlaceholder
import com.nohimazin.monotune.ui.component.LazyColumnScrollbar
import com.nohimazin.monotune.ui.component.LazyVerticalGridScrollbar
import com.nohimazin.monotune.ui.component.LibraryPlaylistGridItem
import com.nohimazin.monotune.ui.component.LibraryPlaylistListItem
import com.nohimazin.monotune.ui.component.ScrollToTopManager
import com.nohimazin.monotune.ui.component.SortHeader
import com.nohimazin.monotune.ui.component.items.AutoPlaylistGridItem
import com.nohimazin.monotune.ui.component.items.AutoPlaylistListItem
import com.nohimazin.monotune.ui.dialog.CreatePlaylistDialog
import com.nohimazin.monotune.ui.menu.ActionDropdown
import com.nohimazin.monotune.ui.menu.DropdownItem
import com.nohimazin.monotune.utils.rememberEnumPreference
import com.nohimazin.monotune.utils.rememberPreference
import com.nohimazin.monotune.viewmodels.LibraryPlaylistsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable() (() -> Unit)? = null,
) {
    Log.v("LibraryPlaylistsScreen", "LP_RC-1")
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    var filter by rememberEnumPreference(PlaylistFilterKey, PlaylistFilter.LIBRARY)
    libraryFilterContent?.let { filter = PlaylistFilter.LIBRARY }

    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    val libraryViewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)
    val viewType = if (libraryFilterContent != null) libraryViewType else playlistViewType

    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)
    val (showLikedAndDownloadedPlaylist) = rememberPreference(ShowLikedAndDownloadedPlaylist, true)

    val playlists by viewModel.allPlaylists.collectAsState()
    val isSyncingRemotePlaylists by viewModel.isSyncingRemotePlaylists.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val likedPlaylist = PlaylistEntity(id = "liked", name = stringResource(id = R.string.liked_songs))
    val downloadedPlaylist = PlaylistEntity(id = "downloaded", name = stringResource(id = R.string.downloaded_songs))

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.syncPlaylists() }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    val filterContent = @Composable {
        Column {
            Row {
                ChipsRow(
                    chips = listOf(
                        PlaylistFilter.LIBRARY to stringResource(R.string.filter_library),
                        PlaylistFilter.DOWNLOADED to stringResource(R.string.filter_downloaded)
                    ),
                    currentValue = filter,
                    onValueUpdate = {
                        filter = it
                        if (it == PlaylistFilter.LIBRARY) viewModel.syncPlaylists()
                    },
                    isLoading = { filter ->
                        filter == PlaylistFilter.LIBRARY && isSyncingRemotePlaylists
                    }
                )
            }
        }
    }

    // TODO: full migration to flow row?
    val headerContent = @Composable {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp)
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                    }
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                playlists?.let { playlists ->
                    Text(
                        text = pluralStringResource(R.plurals.n_playlist, playlists.size, playlists.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.width(4.dp))
                ActionDropdown(
                    actions = listOf(
                        DropdownItem(
                            title = stringResource(R.string.library_filter),
                            leadingIcon = { Icon(Icons.Rounded.FilterAlt, null) },
                            action = {},
                            secondaryDropdown =
                                listOf(
                                    DropdownItem(
                                        title = stringResource(R.string.filter_library),
                                        leadingIcon = null,
                                        action = { filter = PlaylistFilter.LIBRARY }
                                    ),
                                    DropdownItem(
                                        title = stringResource(R.string.filter_downloaded),
                                        leadingIcon = null,
                                        action = { filter = PlaylistFilter.DOWNLOADED }
                                    ),
                                )
                        ),
                        DropdownItem(
                            title = stringResource(R.string.create_playlist),
                            leadingIcon = { Icon(Icons.Rounded.Add, null) },
                            action = { showCreatePlaylistDialog = true }
                        ),
                        DropdownItem(
                            title = stringResource(R.string.import_playlist),
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Input, null) },
                            action = {},
                        ),
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isSyncingRemotePlaylists,
                onRefresh = {
                    viewModel.syncPlaylists(true)
                }
            ),
    ) {
        ScrollToTopManager(navController, lazyListState)
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() } ?: filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    if (showLikedAndDownloadedPlaylist) {
                        item(
                            key = likedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistListItem(
                                playlist = likedPlaylist,
                                thumbnail = Icons.Rounded.Favorite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${likedPlaylist.id}")
                                    }
                                    .animateItem()
                            )
                        }

                        item(
                            key = downloadedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistListItem(
                                playlist = downloadedPlaylist,
                                thumbnail = Icons.Rounded.CloudDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${downloadedPlaylist.id}")
                                    }
                                    .animateItem()
                            )
                        }
                    }

                    playlists?.let { playlists ->
                        if (playlists.isEmpty() && !showLikedAndDownloadedPlaylist) {
                            item {
                                EmptyPlaceholder(
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    text = stringResource(R.string.library_playlist_empty),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        items(
                            items = playlists,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) { playlist ->
                            LibraryPlaylistListItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = playlist,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
                LazyColumnScrollbar(
                    state = lazyListState,
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() } ?: filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    if (showLikedAndDownloadedPlaylist) {
                        item(
                            key = likedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistGridItem(
                                playlist = likedPlaylist,
                                thumbnail = Icons.Rounded.Favorite,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${likedPlaylist.id}")
                                    }
                                    .animateItem()
                            )
                        }

                        item(
                            key = downloadedPlaylist.id,
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) {
                            AutoPlaylistGridItem(
                                playlist = downloadedPlaylist,
                                thumbnail = Icons.Rounded.CloudDownload,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("auto_playlist/${downloadedPlaylist.id}")
                                    }
                                    .animateItem()
                            )
                        }
                    }

                    playlists?.let { playlists ->
                        if (playlists.isEmpty() && !showLikedAndDownloadedPlaylist) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyPlaceholder(
                                    icon = R.drawable.queue_music,
                                    text = stringResource(R.string.library_playlist_empty),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        items(
                            items = playlists,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_PLAYLIST }
                        ) { playlist ->
                            LibraryPlaylistGridItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = playlist,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
                LazyVerticalGridScrollbar(
                    state = lazyGridState,
                )
            }
        }

        /**
         * Dialog
         */

        Indicator(
            isRefreshing = isSyncingRemotePlaylists,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )

    }
}

