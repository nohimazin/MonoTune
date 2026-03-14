/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.db.entities.ManualCorrection
import com.nohimazin.monotune.db.entities.MonochromeTrackMatch
import com.nohimazin.monotune.monochrome.MonochromeClientApi
import com.nohimazin.monotune.monochrome.MonochromeResult
import com.nohimazin.monotune.monochrome.MonochromeTrack
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

    /** Current automatic match for the song being edited, if any. */
    var currentAutoMatch: MonochromeTrackMatch? by mutableStateOf(null)
        private set

    /** Current manual correction for the song being edited, if any. */
    var currentManualCorrection: ManualCorrection? by mutableStateOf(null)
        private set

    /**
     * Load the current match state (auto and manual) for [ytmId] from the database.
     * Call this once when the dialog opens for a given song.
     */
    fun loadCurrentMatch(ytmId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val autoMatch = database.getTrackMatch(ytmId)
            val manualCorrection = database.getManualCorrection(ytmId)
            withContext(Dispatchers.Main) {
                currentAutoMatch = autoMatch
                currentManualCorrection = manualCorrection
            }
        }
    }

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

    /** Reset search state and current-match state so the dialog opens fresh for each song. */
    fun resetSearch() {
        searchState = SearchState.Idle
        currentAutoMatch = null
        currentManualCorrection = null
    }
}
