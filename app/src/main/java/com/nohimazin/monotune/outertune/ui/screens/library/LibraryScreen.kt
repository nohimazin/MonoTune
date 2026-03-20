package com.nohimazin.monotune.ui.screens.library

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nohimazin.monotune.LocalMenuState
import com.nohimazin.monotune.LocalPlayerAwareWindowInsets
import com.nohimazin.monotune.LocalPlayerConnection
import com.nohimazin.monotune.MainActivity
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.CONTENT_TYPE_HEADER
import com.nohimazin.monotune.constants.CONTENT_TYPE_LIST
import com.nohimazin.monotune.constants.CONTENT_TYPE_PLAYLIST
import com.nohimazin.monotune.constants.DEFAULT_ENABLED_FILTERS
import com.nohimazin.monotune.constants.EnabledFiltersKey
import com.nohimazin.monotune.constants.GridThumbnailHeight
import com.nohimazin.monotune.constants.LibraryFilterKey
import com.nohimazin.monotune.constants.LibrarySortDescendingKey
import com.nohimazin.monotune.constants.LibrarySortType
import com.nohimazin.monotune.constants.LibrarySortTypeKey
import com.nohimazin.monotune.constants.LibraryViewType
import com.nohimazin.monotune.constants.LibraryViewTypeKey
import com.nohimazin.monotune.constants.ShowLikedAndDownloadedPlaylist
import com.nohimazin.monotune.db.entities.Album
import com.nohimazin.monotune.db.entities.Artist
import com.nohimazin.monotune.db.entities.Playlist
import com.nohimazin.monotune.db.entities.PlaylistEntity
import com.nohimazin.monotune.ui.component.ChipsLazyRow
import com.nohimazin.monotune.ui.component.EmptyPlaceholder
import com.nohimazin.monotune.ui.component.LazyColumnScrollbar
import com.nohimazin.monotune.ui.component.LazyVerticalGridScrollbar
import com.nohimazin.monotune.ui.component.LibraryAlbumGridItem
import com.nohimazin.monotune.ui.component.LibraryAlbumListItem
import com.nohimazin.monotune.ui.component.LibraryArtistGridItem
import com.nohimazin.monotune.ui.component.LibraryArtistListItem
import com.nohimazin.monotune.ui.component.LibraryPlaylistGridItem
import com.nohimazin.monotune.ui.component.LibraryPlaylistListItem
import com.nohimazin.monotune.ui.component.ScrollToTopManager
import com.nohimazin.monotune.ui.component.SortHeader
import com.nohimazin.monotune.ui.component.button.IconButton
import com.nohimazin.monotune.ui.component.items.AutoPlaylistGridItem
import com.nohimazin.monotune.ui.component.items.AutoPlaylistListItem
import com.nohimazin.monotune.ui.screens.Screens
import com.nohimazin.monotune.ui.screens.Screens.LibraryFilter
import com.nohimazin.monotune.utils.rememberEnumPreference
import com.nohimazin.monotune.utils.rememberPreference
import com.nohimazin.monotune.viewmodels.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var viewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)
    val enabledFilters by rememberPreference(EnabledFiltersKey, defaultValue = DEFAULT_ENABLED_FILTERS)
    var filter by rememberEnumPreference(LibraryFilterKey, LibraryFilter.ALL)

    val (sortType, onSortTypeChange) = rememberEnumPreference(LibrarySortTypeKey, LibrarySortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(LibrarySortDescendingKey, true)
    val (showLikedAndDownloadedPlaylist) = rememberPreference(ShowLikedAndDownloadedPlaylist, true)

    val allItems by viewModel.allItems.collectAsState()

    val isSyncingRemotePlaylists by viewModel.isSyncingRemotePlaylists.collectAsState()
    val isSyncingRemoteAlbums by viewModel.isSyncingRemoteAlbums.collectAsState()
    val isSyncingRemoteArtists by viewModel.isSyncingRemoteArtists.collectAsState()
    val isSyncingRemoteSongs by viewModel.isSyncingRemoteSongs.collectAsState()
    val isSyncingRemoteLikedSongs by viewModel.isSyncingRemoteLikedSongs.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val likedPlaylist = PlaylistEntity(id = "liked", name = stringResource(id = R.string.liked_songs))
    val downloadedPlaylist = PlaylistEntity(id = "downloaded", name = stringResource(id = R.string.downloaded_songs))

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val filterString = when (filter) {
        LibraryFilter.ALBUMS -> stringResource(R.string.albums)
        LibraryFilter.ARTISTS -> stringResource(R.string.artists)
        LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
        LibraryFilter.SONGS -> stringResource(R.string.songs)
        LibraryFilter.ALL -> ""
    }

    val defaultFilter: Collection<Pair<LibraryFilter, String>> = Screens.getFilters(enabledFilters).map {
        when (it) {
            LibraryFilter.ALBUMS -> LibraryFilter.ALBUMS to stringResource(R.string.albums)
            LibraryFilter.ARTISTS -> LibraryFilter.ARTISTS to stringResource(R.string.artists)
            LibraryFilter.PLAYLISTS -> LibraryFilter.PLAYLISTS to stringResource(R.string.playlists)
            LibraryFilter.SONGS -> LibraryFilter.SONGS to stringResource(R.string.songs)
            else -> LibraryFilter.ALL to stringResource(R.string.home) // there is no all filter, use as null value
        }
    }.filterNot { it.first == LibraryFilter.ALL }

    val chips = remember { SnapshotStateList<Pair<LibraryFilter, String>>() }

    var filterSelected by remember {
        mutableStateOf(filter)
    }

    LaunchedEffect(Unit) {
        if (filter == LibraryFilter.ALL)
            chips.addAll(defaultFilter)
        else
            chips.add(filter to filterString)
    }

    LaunchedEffect(filter) {
        if (filter == LibraryFilter.ALL) {
            defaultFilter.forEachIndexed { index, it ->
                if (!chips.contains(it)) chips.add(index, it)
            }
            filterSelected = LibraryFilter.ALL
        } else {
            filterSelected = filter
            chips.filter { it.first != filter }
                .onEach {
                    if (chips.contains(it)) chips.remove(it)
                }
        }
    }

    val filterContent = @Composable {

        Column {
            Row {
                ChipsLazyRow(
                    chips = chips,
                    currentValue = filter,
                    onValueUpdate = {
                        filter = if (filter == LibraryFilter.ALL)
                            it
                        else
                            LibraryFilter.ALL
                    },
                    modifier = Modifier.weight(1f),
                    selected = { it == filterSelected },
                    isLoading = { filter ->
                        (filter == LibraryFilter.PLAYLISTS && isSyncingRemotePlaylists)
                                || (filter == LibraryFilter.ALBUMS && isSyncingRemoteAlbums)
                                || (filter == LibraryFilter.ARTISTS && isSyncingRemoteArtists)
                                || (filter == LibraryFilter.SONGS && (isSyncingRemoteSongs || isSyncingRemoteLikedSongs))
                    }
                )

                if (filter != LibraryFilter.SONGS) {
                    IconButton(
                        onClick = {
                            viewType = viewType.toggle()
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector =
                                when (viewType) {
                                    LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                                    LibraryViewType.GRID -> Icons.Rounded.GridView
                                },
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }

    val headerContent = @Composable {
        SortHeader(
            sortType = sortType,
            sortDescending = sortDescending,
            onSortTypeChange = onSortTypeChange,
            onSortDescendingChange = onSortDescendingChange,
            sortTypeText = { sortType ->
                when (sortType) {
                    LibrarySortType.CREATE_DATE -> R.string.sort_by_create_date
                    LibrarySortType.NAME -> R.string.sort_by_name
                }
            },
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    if (filter != LibraryFilter.ALL) {
        BackHandler {
            filter = LibraryFilter.ALL
        }
    }

    // scroll to top
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isSyncingRemotePlaylists || isSyncingRemoteAlbums || isSyncingRemoteArtists
                        || isSyncingRemoteSongs || isSyncingRemoteLikedSongs,
                onRefresh = {
                    viewModel.syncAll(true)
                }
            ),
    ) {
        when (filter) {
            LibraryFilter.ALBUMS ->
                LibraryAlbumsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.ARTISTS ->
                LibraryArtistsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.PLAYLISTS ->
                LibraryPlaylistsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.SONGS ->
                LibrarySongsScreen(
                    navController,
                    libraryFilterContent = filterContent
                )

            LibraryFilter.ALL -> {
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
                                filterContent()
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

                            allItems.let { allItems ->
                                if (allItems.isEmpty() && !showLikedAndDownloadedPlaylist) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.AutoMirrored.Rounded.List,
                                            text = stringResource(R.string.library_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                items(
                                    items = allItems.distinctBy { it.hashCode() },
                                    key = { it.hashCode() },
                                    contentType = { CONTENT_TYPE_LIST }
                                ) { item ->
                                    when (item) {
                                        is Album -> {
                                            LibraryAlbumListItem(
                                                navController = navController,
                                                menuState = menuState,
                                                album = item,
                                                isActive = item.id == mediaMetadata?.album?.id,
                                                isPlaying = isPlaying,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        is Artist -> {
                                            LibraryArtistListItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                modifier = Modifier.animateItem(),
                                                artist = item
                                            )
                                        }

                                        is Playlist -> {
                                            LibraryPlaylistListItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                playlist = item,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        else -> {}
                                    }
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
                                filterContent()
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

                            allItems.let { allItems ->
                                if (allItems.isEmpty() && !showLikedAndDownloadedPlaylist) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.AutoMirrored.Rounded.List,
                                            text = stringResource(R.string.library_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }

                                items(
                                    items = allItems.distinctBy { it.hashCode() },
                                    key = { it.hashCode() },
                                    contentType = { CONTENT_TYPE_LIST }
                                ) { item ->
                                    when (item) {
                                        is Album -> {
                                            LibraryAlbumGridItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                album = item,
                                                isActive = item.id == mediaMetadata?.album?.id,
                                                isPlaying = isPlaying,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        is Artist -> {
                                            LibraryArtistGridItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                modifier = Modifier.animateItem(),
                                                artist = item
                                            )
                                        }

                                        is Playlist -> {
                                            LibraryPlaylistGridItem(
                                                navController = navController,
                                                menuState = menuState,
                                                coroutineScope = coroutineScope,
                                                playlist = item,
                                                modifier = Modifier.animateItem()
                                            )
                                        }

                                        else -> {}
                                    }
                                }
                            }
                        }
                        LazyVerticalGridScrollbar(
                            state = lazyGridState,
                        )
                    }
                }
            }
        }

        Indicator(
            isRefreshing = isSyncingRemotePlaylists || isSyncingRemoteAlbums || isSyncingRemoteArtists
                    || isSyncingRemoteSongs || isSyncingRemoteLikedSongs,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

