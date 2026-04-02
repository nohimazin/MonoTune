package com.nohimazin.monotune.lyrics

import android.content.Context
import android.util.LruCache
import com.nohimazin.monotune.constants.LyricTrimKey
import com.nohimazin.monotune.constants.MultilineLrcKey
import com.nohimazin.monotune.db.MusicDatabase
import com.nohimazin.monotune.db.entities.LyricsEntity
import com.nohimazin.monotune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.nohimazin.monotune.models.MediaMetadata
import com.nohimazin.monotune.utils.dataStore
import com.nohimazin.monotune.utils.get
import com.nohimazin.monotune.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.parseLrc
import javax.inject.Inject

class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    private val monochromeLyricsProvider: MonochromeLyricsProvider,
) {
    // Monochrome is tried first (uses accurate TIDAL metadata + album for LRCLib lookup).
    // If no Monochrome match exists or the request fails, subsequent providers are attempted.
    private val lyricsProviders: List<LyricsProvider> by lazy {
        listOf(
            monochromeLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeLyricsProvider,
        )
    }
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    /**
     * Retrieve lyrics from all sources
     *
     * How lyrics are resolved are determined by PreferLocalLyrics settings key. If this is true, prioritize local lyric
     * files over all cloud providers, true is vice versa.
     *
     * Lyrics stored in the database are fetched first. If this is not available, it is resolved by other means.
     * If local lyrics are preferred, lyrics from the lrc file is fetched, and then resolve by other means.
     *
     * @param mediaMetadata Song to fetch lyrics for
     * @param database MusicDatabase connection. Database lyrics are prioritized over all sources.
     * If no database is provided, the database source is disabled
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata): SemanticLyrics? {
        val trim = context.dataStore.get(LyricTrimKey, defaultValue = false)
        val multiline = context.dataStore.get(MultilineLrcKey, defaultValue = true)

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return parseLrc(cached.lyrics, trim, multiline)
        }
        val dbLyrics = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
        if (dbLyrics != null) {
            return parseLrc(dbLyrics, trim, multiline)
        }

        val remoteLyrics = getRemoteLyrics(mediaMetadata)
        if (remoteLyrics != null) {
            database.query {
                upsert(
                    LyricsEntity(
                        id = mediaMetadata.id,
                        lyrics = remoteLyrics
                    )
                )
            }
            return parseLrc(remoteLyrics, trim, multiline)
        }

        database.query {
            upsert(
                LyricsEntity(
                    id = mediaMetadata.id,
                    lyrics = LYRICS_NOT_FOUND
                )
            )
        }
        return null
    }

    /**
     * Lookup lyrics from remote providers
     */
    private suspend fun getRemoteLyrics(mediaMetadata: MediaMetadata): String? {
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration
                ).onSuccess { lyrics ->
                    return lyrics
                }.onFailure {
                    // LyricsNotFoundException is an expected "no match" signal  Enot a true error.
                    if (it !is LyricsNotFoundException) {
                        reportException(it)
                    }
                }
            }
        }
        return null
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        cache.get(mediaId)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(mediaId, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
