/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.ManualCorrection
import com.dd3boh.outertune.monochrome.MonochromeClientApi
import com.dd3boh.outertune.monochrome.MonochromeResult
import com.dd3boh.outertune.monochrome.MonochromeTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the Fix Match dialog.
 *
 * Handles searching Monochrome for candidate tracks and persisting the user's
 * choice as a [ManualCorrection] row in the database.
 */
@HiltViewModel
class MonochromeFixMatchViewModel @Inject constructor(
    private val monochromeClient: MonochromeClientApi,
    private val database: MusicDatabase,
) : ViewModel() {

    sealed class SearchState {
        data object Idle : SearchState()
        data object Loading : SearchState()
        data class Success(val results: List<MonochromeTrack>) : SearchState()
        data class Error(val message: String) : SearchState()
    }

    var searchState: SearchState by mutableStateOf(SearchState.Idle)
        private set

    /**
     * Execute a search against the Monochrome API for [query].
     * Updates [searchState] with the result.
     */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            searchState = SearchState.Loading
            searchState = when (val result = monochromeClient.search(trimmed)) {
                is MonochromeResult.Success -> SearchState.Success(result.data)
                is MonochromeResult.Error -> SearchState.Error(result.message)
            }
        }
    }

    /**
     * Persist a manual correction for [ytmId].
     *
     * @param ytmId           The YouTube Music track ID of the playlist song.
     * @param selectedTrack   The Monochrome track chosen by the user, or `null` to mark the
     *                        song as explicitly unavailable.
     * @param onComplete      Callback invoked on the **main** thread once the row is saved.
     */
    fun saveCorrection(
        ytmId: String,
        selectedTrack: MonochromeTrack?,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            database.upsertManualCorrection(
                ManualCorrection(
                    ytmId = ytmId,
                    correctedMonochromeId = selectedTrack?.monochromeId,
                    correctedAt = System.currentTimeMillis(),
                )
            )
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    /** Reset search state so the dialog opens fresh for each song. */
    fun resetSearch() {
        searchState = SearchState.Idle
    }
}
