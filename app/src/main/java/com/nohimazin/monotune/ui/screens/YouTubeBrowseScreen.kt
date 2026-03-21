package com.nohimazin.monotune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.nohimazin.monotune.LocalMenuState
import com.nohimazin.monotune.LocalPlayerAwareWindowInsets
import com.nohimazin.monotune.LocalPlayerConnection
import com.nohimazin.monotune.constants.TopBarInsets
import com.nohimazin.monotune.extensions.togglePlayPause
import com.nohimazin.monotune.models.toMediaMetadata
import com.nohimazin.monotune.playback.queues.YouTubeQueue
import com.nohimazin.monotune.ui.component.LazyColumnScrollbar
import com.nohimazin.monotune.ui.component.NavigationTitle
import com.nohimazin.monotune.ui.component.button.IconButton
import com.nohimazin.monotune.ui.component.items.YouTubeListItem
import com.nohimazin.monotune.ui.component.shimmer.ListItemPlaceHolder
import com.nohimazin.monotune.ui.component.shimmer.ShimmerHost
import com.nohimazin.monotune.ui.menu.YouTubeAlbumMenu
import com.nohimazin.monotune.ui.menu.YouTubeArtistMenu
import com.nohimazin.monotune.ui.menu.YouTubePlaylistMenu
import com.nohimazin.monotune.ui.menu.YouTubeSongMenu
import com.nohimazin.monotune.ui.utils.backToMain
import com.nohimazin.monotune.viewmodels.YouTubeBrowseViewModel
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val browseResult by viewModel.result.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) {
        if (browseResult == null) {
            item {
                ShimmerHost {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }

        browseResult?.items?.forEach {
            it.title?.let { title ->
                item {
                    NavigationTitle(title)
                }
            }

            items(it.items) { item ->
                YouTubeListItem(
                    item = item,
                    isActive = when (item) {
                        is SongItem -> mediaMetadata?.id == item.id
                        is AlbumItem -> mediaMetadata?.album?.id == item.id
                        else -> false
                    },
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    when (item) {
                                        is SongItem -> YouTubeSongMenu(
                                            song = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )

                                        is AlbumItem -> YouTubeAlbumMenu(
                                            albumItem = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )

                                        is ArtistItem -> YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss
                                        )

                                        is PlaylistItem -> YouTubePlaylistMenu(
                                            navController = navController,
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .clickable {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata()),
                                            isRadio = true,
                                        )
                                    }
                                }

                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                            }
                        }
                        .animateItem()
                )
            }
        }
    }
    LazyColumnScrollbar(
        state = lazyListState,
    )

    TopAppBar(
        title = { Text(browseResult?.title.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}
