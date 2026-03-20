package com.nohimazin.monotune.ui.screens.search

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.nohimazin.monotune.LocalDatabase
import com.nohimazin.monotune.LocalPlayerAwareWindowInsets
import com.nohimazin.monotune.LocalPlayerConnection
import com.nohimazin.monotune.R
import com.nohimazin.monotune.constants.DEFAULT_ENABLED_TABS
import com.nohimazin.monotune.constants.EnabledTabsKey
import com.nohimazin.monotune.constants.PauseSearchHistoryKey
import com.nohimazin.monotune.constants.SearchSource
import com.nohimazin.monotune.constants.SearchSourceKey
import com.nohimazin.monotune.constants.UpdateAvailableKey
import com.nohimazin.monotune.db.entities.SearchHistory
import com.nohimazin.monotune.extensions.tabMode
import com.nohimazin.monotune.ui.component.SearchBar
import com.nohimazin.monotune.ui.component.button.IconButton
import com.nohimazin.monotune.ui.screens.Screens
import com.nohimazin.monotune.utils.dataStore
import com.nohimazin.monotune.utils.get
import com.nohimazin.monotune.utils.rememberEnumPreference
import com.nohimazin.monotune.utils.rememberPreference
import com.nohimazin.monotune.utils.urlEncode
import com.nohimazin.monotune.youtubeNavigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarContainer(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    Log.v("SearchBarContainer", "SB-1")
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current

    val enabledTabs by rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    val updateAvailable by rememberPreference(UpdateAvailableKey, defaultValue = false)

    val navigationItems = remember { Screens.getScreens(enabledTabs) }
    val searchBarFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    val (query, onQueryChange) = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    var searchActive by rememberSaveable {
        mutableStateOf(false)
    }
    val onSearchActiveChange: (Boolean) -> Unit = { newActive ->
        searchActive = newActive
        if (!newActive) {
            focusManager.clearFocus()
            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                onQueryChange(TextFieldValue())
            }
        }
    }

    val onSearch: (String) -> Unit = {
        if (it.isNotEmpty()) {
            if (searchSource == SearchSource.LOCAL) {
                focusManager.clearFocus(true)
            } else {
                onSearchActiveChange(false)
                if (youtubeNavigator(
                        context,
                        navController,
                        coroutineScope,
                        playerConnection,
                        snackbarHostState,
                        it.toUri()
                    )
                ) {
                    // don't do anything
                } else {
                    navController.navigate("search/${it.urlEncode()}")
                    if (context.dataStore[PauseSearchHistoryKey] != true) {
                        database.query {
                            insert(SearchHistory(query = it))
                        }
                    }
                }
            }
        }
    }


    val shouldShowSearchBar = remember(searchActive, navBackStackEntry) {
        (searchActive || navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                navBackStackEntry?.destination?.route?.startsWith("search/") == true)
    }

    LaunchedEffect(navBackStackEntry) {
        if (searchActive) {
            onSearchActiveChange(false)
        }
    }

    AnimatedVisibility(
        visible = shouldShowSearchBar,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val searchBarInset = if (!context.tabMode()) {
            WindowInsets.safeDrawing.union(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Start))
        }
        else {
            WindowInsets()
        }
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            active = searchActive,
            onActiveChange = onSearchActiveChange,
            scrollBehavior = scrollBehavior,
            placeholder = {
                Text(
                    text = stringResource(
                        if (!searchActive) R.string.search
                        else when (searchSource) {
                            SearchSource.LOCAL -> R.string.search_library
                            SearchSource.ONLINE -> R.string.search_yt_music
                        }
                    )
                )
            },
            leadingIcon = {
                IconButton(
                    onClick = {
                        when {
                            searchActive -> onSearchActiveChange(false)

                            !searchActive && navBackStackEntry?.destination?.route?.startsWith(
                                "search"
                            ) == true -> {
                                navController.navigateUp()
                            }

                            else -> onSearchActiveChange(true)
                        }
                    },
                ) {
                    Icon(
                        imageVector =
                            if (searchActive || navBackStackEntry?.destination?.route?.startsWith("search") == true) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Search
                            },
                        contentDescription = null
                    )
                }
            },
            trailingIcon = {
                if (searchActive) {
                    if (query.text.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange(TextFieldValue("")) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            searchSource =
                                if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                        }
                    ) {
                        Icon(
                            imageVector = when (searchSource) {
                                SearchSource.LOCAL -> Icons.Rounded.LibraryMusic
                                SearchSource.ONLINE -> Icons.Rounded.Language
                            },
                            contentDescription = null
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                navController.navigate("settings")
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null
                        )
                    }
                }
            },
            windowInsets = searchBarInset,
            focusRequester = searchBarFocusRequester,
        ) {
            Log.v("SearchBarContainer", "SB-2")
            Crossfade(
                targetState = searchSource,
                label = "",
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) { searchSource ->
                when (searchSource) {
                    SearchSource.LOCAL -> LocalSearchScreen(
                        query = query.text,
                        navController = navController,
                        onDismiss = { onSearchActiveChange(false) },
                    )

                    SearchSource.ONLINE -> OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = onQueryChange,
                        navController = navController,
                        onSearch = {
                            if (youtubeNavigator(
                                    context,
                                    navController,
                                    coroutineScope,
                                    playerConnection,
                                    snackbarHostState,
                                    it.toUri()
                                )
                            ) {
                                return@OnlineSearchScreen
                            } else {
                                navController.navigate("search/${it.urlEncode()}")
                                if (context.dataStore[PauseSearchHistoryKey] != true) {
                                    database.query {
                                        insert(SearchHistory(query = it))
                                    }
                                }
                            }
                        },
                        onDismiss = { onSearchActiveChange(false) },
                    )
                }
            }
        }
    }
}

