package com.dd3boh.outertune.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.models.ItemsPage
import com.dd3boh.outertune.monochrome.MonochromeSearchMatcher
import com.dd3boh.outertune.monochrome.MonochromeTrack
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.pages.SearchSummary
import com.zionhuang.innertube.pages.SearchSummaryPage
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val monochromeSearchMatcher: MonochromeSearchMatcher,
) : ViewModel() {
    val query = savedStateHandle.get<String>("query")!!
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    /**
     * Map from YTM video ID to the matched [MonochromeTrack].
     * Populated after each batch of matching completes.
     */
    val matchedMonochromeTracks = mutableStateMapOf<String, MonochromeTrack>()

    /**
     * `true` while a monochrome-matching pass is in progress.
     * The UI can use this to show an additional loading indicator.
     */
    var isMatchingInProgress by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            filter.collect { filter ->
                if (filter == null) {
                    if (summaryPage == null) {
                        YouTube.searchSummary(query)
                            .onSuccess { page ->
                                val songs = page.summaries
                                    .flatMap { it.items }
                                    .filterIsInstance<SongItem>()
                                if (songs.isEmpty()) {
                                    summaryPage = page
                                } else {
                                    isMatchingInProgress = true
                                    try {
                                        val matches = monochromeSearchMatcher.matchSongs(songs)
                                        matchedMonochromeTracks.putAll(matches)
                                        // Keep only summaries that contain at least one item after
                                        // filtering songs to matched-only.
                                        val filteredSummaries = page.summaries
                                            .map { summary ->
                                                SearchSummary(
                                                    title = summary.title,
                                                    items = summary.items.filter { item ->
                                                        item !is SongItem || matches.containsKey(item.id)
                                                    },
                                                )
                                            }
                                            .filter { it.items.isNotEmpty() }
                                        summaryPage = SearchSummaryPage(filteredSummaries)
                                    } finally {
                                        isMatchingInProgress = false
                                    }
                                }
                            }
                            .onFailure {
                                reportException(it)
                            }
                    }
                } else {
                    if (viewStateMap[filter.value] == null) {
                        YouTube.search(query, filter)
                            .onSuccess { result ->
                                val allItems = result.items.distinctBy { it.id }
                                val songs = allItems.filterIsInstance<SongItem>()
                                if (songs.isEmpty() || filter != YouTube.SearchFilter.FILTER_SONG) {
                                    // Non-song filters: pass through as-is.
                                    viewStateMap[filter.value] = ItemsPage(allItems, result.continuation)
                                } else {
                                    isMatchingInProgress = true
                                    try {
                                        val matches = monochromeSearchMatcher.matchSongs(songs)
                                        matchedMonochromeTracks.putAll(matches)
                                        val filtered = allItems.filter { item ->
                                            item !is SongItem || matches.containsKey(item.id)
                                        }
                                        viewStateMap[filter.value] = ItemsPage(filtered, result.continuation)
                                    } finally {
                                        isMatchingInProgress = false
                                    }
                                }
                            }
                            .onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val filter = filter.value ?: return
        val filterValue = filter.value
        viewModelScope.launch {
            val viewState = viewStateMap[filterValue] ?: return@launch
            val continuation = viewState.continuation ?: return@launch
            val searchResult = YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
            val newItems = searchResult.items.filterNot { new -> viewState.items.any { it.id == new.id } }
            val newSongs = newItems.filterIsInstance<SongItem>()
            if (newSongs.isEmpty() || filter != YouTube.SearchFilter.FILTER_SONG) {
                viewStateMap[filterValue] = ItemsPage(
                    (viewState.items + newItems).distinctBy { it.id },
                    searchResult.continuation,
                )
            } else {
                val matches = monochromeSearchMatcher.matchSongs(newSongs)
                matchedMonochromeTracks.putAll(matches)
                val filteredNew = newItems.filter { item ->
                    item !is SongItem || matches.containsKey(item.id)
                }
                viewStateMap[filterValue] = ItemsPage(
                    (viewState.items + filteredNew).distinctBy { it.id },
                    searchResult.continuation,
                )
            }
        }
    }
}
