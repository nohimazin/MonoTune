package com.dd3boh.outertune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nohimazin.monotune.constants.PlaylistSongSortDescendingKey
import com.nohimazin.monotune.constants.PlaylistSongSortType
import com.nohimazin.monotune.constants.PlaylistSongSortTypeKey
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.extensions.reversed
import com.nohimazin.monotune.extensions.toEnum
import com.nohimazin.monotune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlistWithSongs = combine(
        database.playlist(playlistId),
        database.playlistSongs(playlistId),
        context.dataStore.data
            .map {
                it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM) to
                        (it[PlaylistSongSortDescendingKey] ?: true)
            }
            .distinctUntilChanged()
    ) { playlist, songs, (sortType, sortDescending) ->
        val sortedSongs = when (sortType) {
            PlaylistSongSortType.CUSTOM -> songs
            PlaylistSongSortType.NAME -> songs.sortedBy { it.song.song.title.lowercase() }
            PlaylistSongSortType.ARTIST -> songs.sortedBy { song ->
                song.song.artists.joinToString { it.name }.lowercase()
            }
            PlaylistSongSortType.ADDED_DATE -> songs.sortedBy { it.song.song.inLibrary }
            PlaylistSongSortType.MODIFIED_DATE -> songs.sortedBy { it.song.song.dateModified }
            PlaylistSongSortType.RELEASE_DATE -> songs.sortedBy { it.song.song.getDateLong() }
        }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)

        Pair(playlist, sortedSongs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, Pair(null, emptyList()))

    /**
     * Reactive map of song ID → [PlaylistSongWithStatus] for all songs in this playlist.
     *
     * Applies the same manual-correction-first priority used by playback:
     *  1. [com.dd3boh.outertune.db.entities.ManualCorrection] wins
     *  2. fallback to [com.dd3boh.outertune.db.entities.MonochromeTrackMatch]
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val songsWithStatus = playlistWithSongs
        .flatMapLatest { (_, songs) ->
            if (songs.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val ytmIds = songs.map { it.song.id }
                combine(
                    database.trackMatchesForSongsFlow(ytmIds),
                    database.manualCorrectionsForSongsFlow(ytmIds),
                ) { matches, corrections ->
                    val matchMap = matches.associateBy { it.ytmId }
                    val correctionMap = corrections.associateBy { it.ytmId }
                    songs.associate { song ->
                        song.song.id to PlaylistSongWithStatus(
                            playlistSong = song,
                            autoMatch = matchMap[song.song.id],
                            manualCorrection = correctionMap[song.song.id],
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        // Fix playlist song order
        viewModelScope.launch(Dispatchers.IO) {
            val sortedSongs = playlistWithSongs.first().second.sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, song ->
                    if (song.map.position != index) {
                        update(song.map.copy(position = index))
                    }
                }
            }
        }
    }
}