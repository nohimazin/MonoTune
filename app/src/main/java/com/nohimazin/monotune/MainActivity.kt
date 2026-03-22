/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O?u?t?er?Tu?ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nohimazin.monotune

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.window.core.layout.WindowWidthSizeClass
import com.nohimazin.monotune.constants.AppBarHeight
import com.nohimazin.monotune.constants.DEFAULT_ENABLED_TABS
import com.nohimazin.monotune.constants.DarkMode
import com.nohimazin.monotune.constants.DarkModeKey
import com.nohimazin.monotune.constants.DefaultOpenTabKey
import com.nohimazin.monotune.constants.DynamicThemeKey
import com.nohimazin.monotune.constants.EnabledTabsKey
import com.nohimazin.monotune.constants.HighContrastKey
import com.nohimazin.monotune.constants.LibraryFilterKey
import com.nohimazin.monotune.constants.MinMiniPlayerHeight
import com.nohimazin.monotune.constants.MiniPlayerHeight
import com.nohimazin.monotune.constants.NavigationBarAnimationSpec
import com.nohimazin.monotune.constants.NavigationBarHeight
import com.nohimazin.monotune.constants.OOBE_VERSION
import com.nohimazin.monotune.constants.OobeStatusKey
import com.nohimazin.monotune.constants.PureBlackKey
import com.nohimazin.monotune.constants.SlimNavBarKey
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.extensions.tabMode
import com.nohimazin.monotune.playback.DownloadUtil
import com.nohimazin.monotune.playback.MediaControllerViewModel
import com.nohimazin.monotune.playback.MusicService
import com.nohimazin.monotune.playback.PlayerConnection
import com.nohimazin.monotune.ui.component.rememberBottomSheetState
import com.nohimazin.monotune.ui.component.shimmer.ShimmerTheme
import com.nohimazin.monotune.ui.menu.BottomSheetMenu
import com.nohimazin.monotune.ui.menu.MenuState
import com.nohimazin.monotune.ui.player.BottomSheetPlayer
import com.nohimazin.monotune.ui.screens.PlayerScreen
import com.nohimazin.monotune.ui.screens.Screens
import com.nohimazin.monotune.ui.screens.search.SearchBarContainer
import androidx.navigation.NavGraphBuilder
import com.nohimazin.monotune.ui.theme.OuterTuneTheme
import com.nohimazin.monotune.ui.utils.appBarScrollBehavior
import com.nohimazin.monotune.utils.ActivityLauncherHelper
import com.nohimazin.monotune.utils.NetworkConnectivityObserver
import com.nohimazin.monotune.utils.SyncUtils
import com.nohimazin.monotune.utils.rememberEnumPreference
import com.nohimazin.monotune.utils.rememberPreference
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val MAIN_TAG = "MainOtActivity"

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    lateinit var activityLauncher: ActivityLauncherHelper
    lateinit var connectivityObserver: NetworkConnectivityObserver

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)

    val controllerViewModel: MediaControllerViewModel by viewModels()

    // storage permission helper (kept for potential future use)
    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onDestroy() {
        Log.i(MAIN_TAG, "onDestroy() called. isFinishing = $isFinishing")
        try {
            connectivityObserver.unregister()
        } catch (e: UninitializedPropertyAccessException) {
            // lol
        }
        // https://github.com/androidx/media/issues/805
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (playerConnection?.player?.playWhenReady != true || playerConnection?.player?.mediaItemCount == 0)) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(MusicService.NOTIFICATION_ID)
        }
        lifecycle.removeObserver(controllerViewModel)
        playerConnection = null

        super.onDestroy()
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
            playerConnection = PlayerConnection(controllerViewModel, database)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        activityLauncher = ActivityLauncherHelper(this)

        setContent {
            Log.v(MAIN_TAG, "RC-1")
            val coroutineScope = rememberCoroutineScope()
            val haptic = LocalHapticFeedback.current
            val snackbarHostState = remember { SnackbarHostState() }

            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val highContrastCompat by rememberPreference(HighContrastKey, defaultValue = false)
            val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }

            val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
            val tabMode = this@MainActivity.tabMode()
            val useNavRail by remember {
                derivedStateOf {
                    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED && !tabMode
                }
            }


            val (oobeStatus) = rememberPreference(OobeStatusKey, defaultValue = 0)

            var filter by rememberEnumPreference(LibraryFilterKey, Screens.LibraryFilter.ALL)
            val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
            val (enabledTabs) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
            val navigationItems = remember {
                Screens.getScreens(enabledTabs)
            }
            val (defaultOpenTab, onDefaultOpenTabChange) = rememberPreference(
                DefaultOpenTabKey,
                defaultValue = Screens.Home.route
            )




            LaunchedEffect(Unit) {
                // local media & download folders auto scan
                coroutineScope.launch {
                    scanInit(
                        this@MainActivity, database, downloadUtil, coroutineScope, playerConnection,
                        snackbarHostState
                    )
                }
            }


            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            try {
                connectivityObserver.unregister()
            } catch (e: UninitializedPropertyAccessException) {
                // lol
            }
            connectivityObserver = NetworkConnectivityObserver(this@MainActivity)
            val isNetworkConnected by connectivityObserver.networkStatus.collectAsState(true)


            OuterTuneTheme(
                context = this@MainActivity,
                playerConnection = playerConnection,
                enableDynamicTheme = enableDynamicTheme,
                isSystemInDarkTheme = isSystemInDarkTheme,
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                highContrastCompat = highContrastCompat,
            ) {
                Log.v(MAIN_TAG, "RC-2.1")
                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val cutoutInsets = WindowInsets.displayCutout

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()

                val tabOpenedFromShortcut = remember {
                    // reroute to library page for new layout is handled in NavHost section
                    when (intent?.action) {
                        ACTION_SONGS -> if (navigationItems.contains(Screens.Songs)) Screens.Songs else Screens.Library
                        ACTION_ALBUMS -> if (navigationItems.contains(Screens.Albums)) Screens.Albums else Screens.Library
                        ACTION_PLAYLISTS -> if (navigationItems.contains(Screens.Playlists)) Screens.Playlists else Screens.Library
                        else -> null
                    }
                }
                // setup filters for new layout
                if (tabOpenedFromShortcut != null && navigationItems.contains(Screens.Library)) {
                    filter = when (intent?.action) {
                        ACTION_SONGS -> Screens.LibraryFilter.SONGS
                        ACTION_ALBUMS -> Screens.LibraryFilter.ALBUMS
                        ACTION_PLAYLISTS -> Screens.LibraryFilter.PLAYLISTS
                        ACTION_SEARCH -> {
                            navController.navigate("search")
                            filter
                        } // do change filter for search
                        else -> Screens.LibraryFilter.ALL
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val maxW = maxWidth
                    Log.v(MAIN_TAG, "RC-2.2")

                    fun getNavPadding(): Dp {
                        return if (!useNavRail) (if (slimNav) 52.dp else 68.dp) else MinMiniPlayerHeight
                    }

                    val playerBottomSheetState = rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound = bottomInset + MiniPlayerHeight + getNavPadding(),
                        expandedBound = maxHeight,
                    )

                    val playerAwareWindowInsets =
                        remember(
                            bottomInset,
                            playerBottomSheetState.isDismissed,
                        ) {
                            // TODO: Navbar is shown in all screens except for oobe (which doesn't use these insets). Idk what do to tbh
                            var bottom = bottomInset + if (!useNavRail) NavigationBarHeight else 0.dp

                            if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                            if (!tabMode) {
                                windowsInsets
                                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                                    .add(cutoutInsets.only(WindowInsetsSides.Horizontal))
                                    .add(
                                        WindowInsets(
                                            left = if (!useNavRail) 0.dp else NavigationBarHeight,
                                            top = AppBarHeight,
                                            bottom = bottom
                                        )
                                    )
                            } else {
                                windowsInsets
                                    .only(WindowInsetsSides.Top)
                                    .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                            }
                        }

                    val scrollBehavior = appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )


                    DisposableEffect(Unit) {
                        val listener = Consumer<Intent> { intent ->
                            val uri =
                                intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri()
                                ?: return@Consumer
                            youtubeNavigator(
                                this@MainActivity,
                                navController,
                                coroutineScope,
                                playerConnection,
                                snackbarHostState,
                                uri
                            )
                        }

                        addOnNewIntentListener(listener)
                        onDispose { removeOnNewIntentListener(listener) }
                    }

                    CompositionLocalProvider(
                        LocalDatabase provides database,
                        LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalMenuState provides MenuState(rememberModalBottomSheetState()),
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        LocalNetworkConnected provides isNetworkConnected,
                        LocalSnackbarHostState provides snackbarHostState,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Log.v(MAIN_TAG, "RC-3")


                            val navHost: @Composable() (() -> Unit) = @Composable {
                                NavHost(
                                    navController = navController,
                                    startDestination = (Screens.getAllScreens()
                                        .find { it.route == defaultOpenTab })?.route
                                        ?: Screens.Home.route,
                                    enterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        else
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                    },
                                    exitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(100))
                                        else
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(100))
                                    },
                                    popEnterTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }
                                        val previousRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }

                                        if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                                            slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                        else
                                            slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                    },
                                    popExitTransition = {
                                        val currentRouteIndex = navigationItems.indexOfFirst {
                                            it.route == initialState.destination.route
                                        }
                                        val targetRouteIndex = navigationItems.indexOfFirst {
                                            it.route == targetState.destination.route
                                        }

                                        if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                                            slideOutHorizontally { -it / 8 } + fadeOut(tween(100))
                                        else
                                            slideOutHorizontally { it / 8 } + fadeOut(tween(100))
                                    },
                                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                                    builder = {
                                        authGraph(navController)
                                        mainGraph(navController, scrollBehavior) { getNavPadding() }
                                        mediaGraph(navController, scrollBehavior)
                                        playlistGraph(navController, scrollBehavior)
                                        settingsGraph(navController, scrollBehavior)
                                    }
                                )
                            }

                            val navbar: @Composable() (() -> Unit) = @Composable {
                                val navigationBarHeight by animateDpAsState(
                                    targetValue = NavigationBarHeight,
                                    animationSpec = NavigationBarAnimationSpec,
                                    label = ""
                                )

                                NavigationBar(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .height(bottomInset + getNavPadding())
                                        .offset {
                                            if (navigationBarHeight == 0.dp) {
                                                IntOffset(
                                                    x = 0,
                                                    y = (bottomInset + NavigationBarHeight).roundToPx()
                                                )
                                            } else {
                                                val slideOffset =
                                                    (bottomInset + NavigationBarHeight) * playerBottomSheetState.progress.coerceIn(
                                                        0f,
                                                        1f
                                                    )
                                                val hideOffset =
                                                    (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                IntOffset(
                                                    x = 0,
                                                    y = (slideOffset + hideOffset).roundToPx()
                                                )
                                            }
                                        },
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                                ) {
                                    navigationItems.fastForEach { screen ->
                                        // TODO: display selection when based on root page user entered
//                                        val isSelected = navBackStackEntry?.destination?.hierarchy?.any {
//                                            it.route?.substringBefore("?")?.substringBefore("/") == screen.route
//                                        } == true
                                        NavigationBarItem(
                                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true,
                                            icon = {
                                                Icon(
                                                    screen.icon,
                                                    contentDescription = null
                                                )
                                            },
                                            label = {
                                                if (!slimNav) {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            onClick = {
                                                if (playerBottomSheetState.isExpanded) {
                                                    playerBottomSheetState.collapseSoft()
                                                }

                                                if (navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true) {
                                                    navBackStackEntry?.savedStateHandle?.set(
                                                        "scrollToTop",
                                                        true
                                                    )
                                                } else if (navigationItems.none { scr -> navBackStackEntry?.destination?.hierarchy?.any { it.route == scr.route } == true }) {
                                                    // this eye bleach allows you to navigate back when you tap on the navbar on a non-root page
                                                    // TODO: nav3 allows us to access back stack... maybe do indicators properly and remove this hack
                                                    navController.navigateUp()
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }

                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            }
                                        )
                                    }
                                }
                            }

                            @Composable
                            fun navRail(alignment: Alignment) {
                                val layoutDirection = LocalLayoutDirection.current
                                val navigationBarHeight by animateDpAsState(
                                    targetValue = NavigationBarHeight,
                                    animationSpec = NavigationBarAnimationSpec,
                                    label = ""
                                )
                                val leftInset = remember {
                                    derivedStateOf {
                                        playerAwareWindowInsets.getLeft(density, layoutDirection).dp
                                    }
                                }
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                                    header = {
                                        Spacer(Modifier.height(8.dp))
                                        Image(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .padding(start = 8.dp),
                                            painter = painterResource(R.drawable.small_icon),
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier
                                        .align(alignment)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState())
                                        .offset {
                                            if (navigationBarHeight == 0.dp) {
                                                IntOffset(
                                                    x = 0,
                                                    y = (bottomInset + NavigationBarHeight).roundToPx()
                                                )
                                            } else {
                                                val slideOffset =
                                                    (bottomInset + NavigationBarHeight + leftInset.value) *
                                                            playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                val hideOffset =
                                                    (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                                                IntOffset(
                                                    x = -(slideOffset + hideOffset).roundToPx(),
                                                    y = 0
                                                )
                                            }
                                        },
                                ) {
                                    navigationItems.fastForEach { screen ->
                                        // TODO: display selection when based on root page user entered
//                                                val isSelected = navBackStackEntry?.destination?.hierarchy?.any {
//                                                    it.route?.substringBefore("?")?.substringBefore("/") == screen.route
//                                                } == true
                                        NavigationRailItem(
                                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true,
                                            icon = {
                                                Icon(
                                                    screen.icon,
                                                    contentDescription = null
                                                )
                                            },
                                            label = {
                                                if (!slimNav) {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            },
                                            onClick = {
                                                if (playerBottomSheetState.isExpanded) {
                                                    playerBottomSheetState.collapseSoft()
                                                }
                                                if (navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true) {
                                                    navBackStackEntry?.savedStateHandle?.set(
                                                        "scrollToTop",
                                                        true
                                                    )
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }

                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }

                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            }
                                        )
                                    }
                                }
                            }

                            val bottomSheetMenu: @Composable() (() -> Unit) = @Composable {
                                BottomSheetMenu(
                                    state = LocalMenuState.current,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }

                            // phone
                            if (!tabMode) {
                                navHost()

                                SearchBarContainer(navController, scrollBehavior)

                                if (oobeStatus >= OOBE_VERSION) {
                                    if (!navigationItems.contains(Screens.Player)) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController
                                        )
                                    }

                                    if (!useNavRail) {
                                        navbar()
                                    } else {
                                        navRail(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Alignment.BottomEnd else Alignment.BottomStart)
                                    }
                                }
                                bottomSheetMenu()

                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier
                                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                                        .align(Alignment.BottomCenter)
                                )
                            } else {
                                // tabmode only enables >= 600dp (unless it's forced on). For those who wish to try down
                                // to the widescreen limit, 320dp player is the minimum acceptable size for the player
                                val playerW = (maxW.value * 0.4).coerceIn(320.0, 500.0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(playerW.dp)
                                    ) {
                                        if (oobeStatus >= OOBE_VERSION && !navigationItems.contains(Screens.Player)) {
                                            PlayerScreen(navController)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                    ) {
                                        navHost()

                                        SearchBarContainer(navController, scrollBehavior)

                                        if (oobeStatus >= OOBE_VERSION) {
                                            navbar()
                                        }
                                        bottomSheetMenu()

                                        SnackbarHost(
                                            hostState = snackbarHostState,
                                            modifier = Modifier
                                                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                }

                            }

                            // Setup wizard
                            LaunchedEffect(Unit) {
                                if (oobeStatus < OOBE_VERSION) {
                                    navController.navigate("setup_wizard")
                                }
                            }

                            if (BuildConfig.DEBUG) {
                                val debugColour = Color.Red
                                Column(
                                    modifier = Modifier.padding(start = 50.dp, top = 100.dp)
                                ) {
                                    Text(
                                        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                    Text(
                                        text = "${BuildConfig.APPLICATION_ID} | ${BuildConfig.BUILD_TYPE}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                    Text(
                                        text = "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                    Text(
                                        text = "${Build.VERSION.SDK_INT} (${Build.ID})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = debugColour
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        // sdk24 support
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.nohimazin.monotune.action.SEARCH"
        const val ACTION_SONGS = "com.nohimazin.monotune.action.SONGS"
        const val ACTION_ALBUMS = "com.nohimazin.monotune.action.ALBUMS"
        const val ACTION_PLAYLISTS = "com.nohimazin.monotune.action.PLAYLISTS"
    }

}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalMenuState = staticCompositionLocalOf<MenuState> { error("No menu state provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No player WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalNetworkConnected = staticCompositionLocalOf<Boolean> { error("No Network Status provided") }
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState provided") }

