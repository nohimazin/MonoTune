package com.nohimazin.monotune.ui.screens.library

import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GridView
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
import com.nohimazin.monotune.constants.ArtistFilter
import com.nohimazin.monotune.constants.ArtistFilterKey
import com.nohimazin.monotune.constants.ArtistSortDescendingKey
import com.nohimazin.monotune.constants.ArtistSortType
import com.nohimazin.monotune.constants.ArtistSortTypeKey
import com.nohimazin.monotune.constants.ArtistViewTypeKey
import com.nohimazin.monotune.constants.CONTENT_TYPE_ARTIST
import com.nohimazin.monotune.constants.CONTENT_TYPE_HEADER
import com.nohimazin.monotune.constants.GridThumbnailHeight
import com.nohimazin.monotune.constants.LibraryViewType
import com.nohimazin.monotune.constants.LibraryViewTypeKey
import com.nohimazin.monotune.ui.component.ChipsRow
import com.nohimazin.monotune.ui.component.EmptyPlaceholder
import com.nohimazin.monotune.ui.component.LazyColumnScrollbar
import com.nohimazin.monotune.ui.component.LazyVerticalGridScrollbar
import com.nohimazin.monotune.ui.component.LibraryArtistGridItem
import com.nohimazin.monotune.ui.component.LibraryArtistListItem
import com.nohimazin.monotune.ui.component.ScrollToTopManager
import com.nohimazin.monotune.ui.component.SortHeader
import com.nohimazin.monotune.ui.component.button.IconButton
import com.nohimazin.monotune.ui.menu.ActionDropdown
import com.nohimazin.monotune.ui.menu.DropdownItem
import com.nohimazin.monotune.utils.rememberEnumPreference
import com.nohimazin.monotune.utils.rememberPreference
import com.nohimazin.monotune.viewmodels.LibraryArtistsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable() (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)

    var artistViewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val libraryViewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)
    val viewType = if (libraryFilterContent != null) libraryViewType else artistViewType

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSortTypeKey, ArtistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)

    val artists by viewModel.allArtists.collectAsState()
    val isSyncingRemoteArtists by viewModel.isSyncingRemoteArtists.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    LaunchedEffect(Unit) { viewModel.syncArtists() }

    val filterContent = @Composable {
        Column {
            Row {
                ChipsRow(
                    chips = listOf(
                        ArtistFilter.LIKED to stringResource(R.string.filter_liked),
                        ArtistFilter.LIBRARY to stringResource(R.string.filter_library),
                        ArtistFilter.DOWNLOADED to stringResource(R.string.filter_downloaded)
                    ),
                    currentValue = filter,
                    onValueUpdate = {
                        filter = it
                        if ((it == ArtistFilter.LIBRARY || it == ArtistFilter.LIKED)
                            && !isSyncingRemoteArtists
                        ) viewModel.syncArtists()
                    },
                    modifier = Modifier.weight(1f),
                    isLoading = {
                        (it == ArtistFilter.LIBRARY || it == ArtistFilter.LIKED)
                                && isSyncingRemoteArtists
                    }
                )

                IconButton(
                    onClick = {
                        artistViewType = artistViewType.toggle()
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Icon(
                        imageVector =
                            when (artistViewType) {
                                LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                                LibraryViewType.GRID -> Icons.Rounded.GridView
                            },
                        contentDescription = null
                    )
                }
            }
        }
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        ArtistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        ArtistSortType.NAME -> R.string.sort_by_name
                        ArtistSortType.SONG_COUNT -> R.string.sort_by_song_count
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            artists?.let { artists ->
                Text(
                    text = pluralStringResource(R.plurals.n_artist, artists.size, artists.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
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
                                        title = stringResource(R.string.filter_liked),
                                        leadingIcon = null,
                                        action = { filter = ArtistFilter.LIKED }
                                    ),
                                    DropdownItem(
                                        title = stringResource(R.string.filter_library),
                                        leadingIcon = null,
                                        action = { filter = ArtistFilter.LIBRARY }
                                    ),
                                    DropdownItem(
                                        title = stringResource(R.string.filter_downloaded),
                                        leadingIcon = null,
                                        action = { filter = ArtistFilter.DOWNLOADED }
                                    ),
                                )
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
                isRefreshing = isSyncingRemoteArtists,
                onRefresh = {
                    viewModel.syncArtists(true)
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

                    artists?.let { artists ->
                        if (artists.isEmpty()) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.artist,
                                    text = stringResource(R.string.library_artist_empty),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }

                        items(
                            items = artists,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ARTIST }
                        ) { artist ->
                            LibraryArtistListItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                modifier = Modifier.animateItem(),
                                artist = artist
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

                    artists?.let { artists ->
                        if (artists.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyPlaceholder(
                                    icon = R.drawable.artist,
                                    text = stringResource(R.string.library_artist_empty),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        items(
                            items = artists,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ARTIST }
                        ) { artist ->
                            LibraryArtistGridItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                modifier = Modifier.animateItem(),
                                artist = artist
                            )
                        }
                    }
                }
                LazyVerticalGridScrollbar(
                    state = lazyGridState,
                )
            }
        }

        Indicator(
            isRefreshing = isSyncingRemoteArtists,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

