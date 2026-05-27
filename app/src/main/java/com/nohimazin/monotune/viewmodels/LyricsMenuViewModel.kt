package com.nohimazin.monotune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nohimazin.monotune.constants.LYRIC_FETCH_TIMEOUT
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.lyrics.LyricsHelper
import com.nohimazin.monotune.lyrics.LyricsResult
import com.nohimazin.monotune.models.MediaMetadata
import com.nohimazin.monotune.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.utils.SemanticLyrics
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel @Inject constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    fun search(mediaId: String, title: String, artist: String, duration: Int) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                    lyricsHelper.getAllLyrics(mediaId, title, artist, duration) { result ->
                        results.update {
                            it + result
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(mediaMetadata: MediaMetadata, onDone: (SemanticLyrics?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            database.deleteLyricById(mediaMetadata.id)
            val lyrics = withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                lyrics
            }
            withContext(Dispatchers.Main) {
                onDone(lyrics)
            }
        }
    }
}
