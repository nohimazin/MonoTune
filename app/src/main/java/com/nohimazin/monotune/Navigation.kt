/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.nohimazin.monotune

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nohimazin.monotune.ui.screens.*
import com.nohimazin.monotune.ui.screens.artist.*
import com.nohimazin.monotune.ui.screens.library.*
import com.nohimazin.monotune.ui.screens.playlist.*
import com.nohimazin.monotune.ui.screens.search.*
import com.nohimazin.monotune.ui.screens.settings.*

@OptIn(ExperimentalMaterial3Api::class)
fun mainGraph(
    builder: NavGraphBuilder,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    getNavPadding: () -> Dp
) {
    builder.composable(Screens.Home.route) { HomeScreen(navController) }
    builder.composable(Screens.Songs.route) { LibrarySongsScreen(navController) }
    builder.composable(Screens.Artists.route) { LibraryArtistsScreen(navController) }
    builder.composable(Screens.Albums.route) { LibraryAlbumsScreen(navController) }
    builder.composable(Screens.Playlists.route) { LibraryPlaylistsScreen(navController) }
    builder.composable(Screens.Library.route) { LibraryScreen(navController, scrollBehavior) }
    builder.composable(Screens.Player.route) { PlayerScreen(navController, bottomPadding = getNavPadding()) }
    builder.composable("history") { HistoryScreen(navController) }
    builder.composable("stats") { StatsScreen(navController) }
    builder.composable("mood_and_genres") { MoodAndGenresScreen(navController, scrollBehavior) }
    builder.composable("account") { AccountScreen(navController, scrollBehavior) }
}

@OptIn(ExperimentalMaterial3Api::class)
fun mediaGraph(
    builder: NavGraphBuilder,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    builder.composable(
        route = "browse/{browseId}",
        arguments = listOf(navArgument("browseId") { type = NavType.StringType })
    ) {
        BrowseScreen(navController, scrollBehavior, it.arguments?.getString("browseId"))
    }
    builder.composable(route = "search") { SearchBarContainer(navController, scrollBehavior) }
    builder.composable(
        route = "search/{query}",
        arguments = listOf(navArgument("query") { type = NavType.StringType })
    ) { OnlineSearchResult(navController) }
    builder.composable(
        route = "album/{albumId}",
        arguments = listOf(navArgument("albumId") { type = NavType.StringType })
    ) { AlbumScreen(navController, scrollBehavior) }
    builder.composable(
        route = "artist/{artistId}",
        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
    ) { ArtistScreen(navController, scrollBehavior) }
    builder.composable(
        route = "artist/{artistId}/songs",
        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
    ) { ArtistSongsScreen(navController, scrollBehavior) }
    builder.composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
    ) { ArtistAlbumsScreen(navController, scrollBehavior) }
    builder.composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") { type = NavType.StringType },
            navArgument("browseId") { type = NavType.StringType; nullable = true },
            navArgument("params") { type = NavType.StringType; nullable = true }
        )
    ) { ArtistItemsScreen(navController, scrollBehavior) }
}

@OptIn(ExperimentalMaterial3Api::class)
fun playlistGraph(
    builder: NavGraphBuilder,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    builder.composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { OnlinePlaylistScreen(navController, scrollBehavior) }
    builder.composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { LocalPlaylistScreen(navController, scrollBehavior) }
    builder.composable(
        route = "auto_playlist/{playlistId}",
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { AutoPlaylistScreen(navController, scrollBehavior) }
    builder.composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") { type = NavType.StringType; nullable = true },
            navArgument("params") { type = NavType.StringType; nullable = true }
        )
    ) { YouTubeBrowseScreen(navController, scrollBehavior) }
}

@OptIn(ExperimentalMaterial3Api::class)
fun settingsGraph(
    builder: NavGraphBuilder,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    builder.composable("settings") { SettingsScreen(navController, scrollBehavior) }
    builder.composable("settings/appearance") { AppearanceSettings(navController, scrollBehavior) }
    builder.composable("settings/interface") { InterfaceSettings(navController, scrollBehavior) }
    builder.composable("settings/library") { LibrarySettings(navController, scrollBehavior) }
    builder.composable("settings/library/lyrics") { LyricsSettings(navController, scrollBehavior) }
    builder.composable("settings/account_sync") { AccountSyncSettings(navController, scrollBehavior) }
    builder.composable("settings/player") { PlayerSettings(navController, scrollBehavior) }
    builder.composable("settings/storage") { StorageSettings(navController, scrollBehavior) }
    builder.composable("settings/backup_restore") { BackupAndRestore(navController, scrollBehavior) }
    builder.composable("settings/experimental") { ExperimentalSettings(navController, scrollBehavior) }
    builder.composable("settings/about") { AboutScreen(navController, scrollBehavior) }
    builder.composable(route = "settings/about/attribution") { AttributionScreen(navController, scrollBehavior) }
    builder.composable(route = "settings/about/oss_licenses") { LibrariesScreen(navController, scrollBehavior) }
}

fun authGraph(builder: NavGraphBuilder, navController: NavController) {
    builder.composable(route = "login") { LoginScreen(navController) }
    builder.composable(route = "monochrome_login") { MonochromeLoginScreen(navController) }
    builder.composable(route = "setup_wizard") { SetupWizard(navController) }
}
