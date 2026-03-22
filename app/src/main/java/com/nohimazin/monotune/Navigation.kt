/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

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

fun NavGraphBuilder.mainGraph(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    getNavPadding: () -> Dp
) {
    composable(Screens.Home.route) { HomeScreen(navController) }
    composable(Screens.Songs.route) { LibrarySongsScreen(navController) }
    composable(Screens.Artists.route) { LibraryArtistsScreen(navController) }
    composable(Screens.Albums.route) { LibraryAlbumsScreen(navController) }
    composable(Screens.Playlists.route) { LibraryPlaylistsScreen(navController) }
    composable(Screens.Library.route) { LibraryScreen(navController, scrollBehavior) }
    composable(Screens.Player.route) { PlayerScreen(navController, bottomPadding = getNavPadding()) }
    composable("history") { HistoryScreen(navController) }
    composable("stats") { StatsScreen(navController) }
    composable("mood_and_genres") { MoodAndGenresScreen(navController, scrollBehavior) }
    composable("account") { AccountScreen(navController, scrollBehavior) }
}

fun NavGraphBuilder.mediaGraph(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    composable(
        route = "browse/{browseId}",
        arguments = listOf(navArgument("browseId") { type = NavType.StringType })
    ) {
        BrowseScreen(navController, scrollBehavior, it.arguments?.getString("browseId"))
    }
    composable(route = "search") { SearchBarContainer(navController, scrollBehavior) }
    composable(
        route = "search/{query}",
        arguments = listOf(navArgument("query") { type = NavType.StringType })
    ) { OnlineSearchResult(navController) }
    composable(
        route = "album/{albumId}",
        arguments = listOf(navArgument("albumId") { type = NavType.StringType })
    ) { AlbumScreen(navController, scrollBehavior) }
    composable(
        route = "artist/{artistId}",
        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
    ) { ArtistScreen(navController, scrollBehavior) }
    composable(
        route = "artist/{artistId}/songs",
        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
    ) { ArtistSongsScreen(navController, scrollBehavior) }
    composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(navArgument("artistId") { type = NavType.StringType })
    ) { ArtistAlbumsScreen(navController, scrollBehavior) }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") { type = NavType.StringType },
            navArgument("browseId") { type = NavType.StringType; nullable = true },
            navArgument("params") { type = NavType.StringType; nullable = true }
        )
    ) { ArtistItemsScreen(navController, scrollBehavior) }
}

fun NavGraphBuilder.playlistGraph(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { OnlinePlaylistScreen(navController, scrollBehavior) }
    composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { LocalPlaylistScreen(navController, scrollBehavior) }
    composable(
        route = "auto_playlist/{playlistId}",
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { AutoPlaylistScreen(navController, scrollBehavior) }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") { type = NavType.StringType; nullable = true },
            navArgument("params") { type = NavType.StringType; nullable = true }
        )
    ) { YouTubeBrowseScreen(navController, scrollBehavior) }
}

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior
) {
    composable("settings") { SettingsScreen(navController, scrollBehavior) }
    composable("settings/appearance") { AppearanceSettings(navController, scrollBehavior) }
    composable("settings/interface") { InterfaceSettings(navController, scrollBehavior) }
    composable("settings/library") { LibrarySettings(navController, scrollBehavior) }
    composable("settings/library/lyrics") { LyricsSettings(navController, scrollBehavior) }
    composable("settings/account_sync") { AccountSyncSettings(navController, scrollBehavior) }
    composable("settings/player") { PlayerSettings(navController, scrollBehavior) }
    composable("settings/storage") { StorageSettings(navController, scrollBehavior) }
    composable("settings/backup_restore") { BackupAndRestore(navController, scrollBehavior) }
    composable("settings/experimental") { ExperimentalSettings(navController, scrollBehavior) }
    composable("settings/about") { AboutScreen(navController, scrollBehavior) }
    composable(route = "settings/about/attribution") { AttributionScreen(navController, scrollBehavior) }
    composable(route = "settings/about/oss_licenses") { LibrariesScreen(navController, scrollBehavior) }
}

fun NavGraphBuilder.authGraph(navController: NavController) {
    composable(route = "login") { LoginScreen(navController) }
    composable(route = "monochrome_login") { MonochromeLoginScreen(navController) }
    composable(route = "setup_wizard") { SetupWizard(navController) }
}
