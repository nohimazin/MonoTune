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
     * ...existing code...
     */
}
