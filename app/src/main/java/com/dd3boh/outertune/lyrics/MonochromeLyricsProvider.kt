/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.lyrics

import android.content.Context
import android.util.Log
import com.nohimazin.monotune.db.daos.MonoTuneDao
import com.nohimazin.monotune.monochrome.MonochromeClientApi
import com.nohimazin.monotune.monochrome.MonochromeLyrics
import com.nohimazin.monotune.monochrome.MonochromeResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lyrics provider that fetches lyrics through the Monochrome/LRCLib pipeline
 * using accurate TIDAL track metadata (title, artist, album, duration).
 *
 * When the playing YTM track has a Monochrome match, this provider:
 *  1. Resolves the monochromeId via `manual_correction` (user override) or
 *     `monochrome_track_match` (automatic match) — manual correction wins.
 *  2. Fetches TIDAL track metadata to obtain album info, which improves
 *     LRCLib match accuracy over plain YTM metadata.
 *  3. Queries LRCLib through the Monochrome client with the enriched metadata.
 *  4. Prefers synced LRC lyrics when available; falls back to plain text.
 *
 * If no Monochrome match exists for the YTM track, this provider returns a
 * failure result so that [LyricsHelper] falls through to the next provider
 * (e.g. LrcLib with YTM metadata, KuGou, YouTube).
 *
 * Network errors and unexpected API responses are caught and wrapped as
 * [Result.failure] so callers never receive an exception.
 */
@Singleton
class MonochromeLyricsProvider @Inject constructor(
    private val monochromeClient: MonochromeClientApi,
    private val dao: MonoTuneDao,
) : LyricsProvider {

    override val name = "Monochrome"

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        return try {
            val monochromeId = resolveMonochromeId(dao, id)
                ?: return Result.failure(LyricsNotFoundException("No Monochrome match for track"))

            // Attempt to fetch TIDAL metadata for more accurate album-based LRCLib lookups.
            // A failure here is non-fatal: we fall back to YTM metadata.
            val trackMeta = when (val r = monochromeClient.getTrack(monochromeId)) {
                is MonochromeResult.Success -> r.data
                is MonochromeResult.Error -> {
                    Log.w(TAG, "Could not load Monochrome track metadata: ${r.message}")
                    null
                }
            }

            val queryTitle = trackMeta?.title ?: title
            val queryArtist = trackMeta?.artist ?: artist
            val queryAlbum = trackMeta?.album
            val queryDuration = duration.takeIf { it > 0 } ?: trackMeta?.durationSecs

            when (val result = monochromeClient.getLyrics(
                title = queryTitle,
                artist = queryArtist,
                album = queryAlbum,
                duration = queryDuration,
            )) {
                is MonochromeResult.Success -> {
                    val text = selectLyricsText(result.data)
                    if (text != null) {
                        Result.success(text)
                    } else {
                        Result.failure(LyricsNotFoundException("No lyrics available for \"$queryTitle\""))
                    }
                }
                is MonochromeResult.Error ->
                    Result.failure(Exception(result.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching Monochrome lyrics", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MonochromeLyricsProvider"

        /**
         * Resolve the Monochrome track ID for a given YTM [ytmId].
         *
         * Priority:
         * 1. `manual_correction` – explicit user override; a `null` correctedMonochromeId
         *    means the user has explicitly marked the track as unavailable in Monochrome.
         * 2. `monochrome_track_match` – automatic match created by the search matcher.
         *
         * Returns `null` when no match exists, or when the correction marks the
         * track as unavailable.
         *
         * Typed as [MonoTuneDao] so that unit tests can supply a lightweight fake
         * without a Room database.  In production code the injected [MonoTuneDao]
         * is satisfied by the [com.nohimazin.monotune.db.MusicDatabase] binding in
         * [com.nohimazin.monotune.di.AppModule].
         */
        internal suspend fun resolveMonochromeId(dao: MonoTuneDao, ytmId: String): String? {
            val correction = dao.getManualCorrection(ytmId)
            if (correction != null) {
                // Explicit user override; null correctedMonochromeId = "not available"
                return correction.correctedMonochromeId
            }
            return dao.getTrackMatch(ytmId)?.monochromeId
        }

        /**
         * Select the best lyrics text from a [MonochromeLyrics] response.
         *
         * Priority: synced LRC > plain text > null
         *
         * Returns `null` for instrumental tracks or when no lyrics content is present.
         */
        internal fun selectLyricsText(lyrics: MonochromeLyrics?): String? = when {
            lyrics == null -> null
            lyrics.instrumental -> null
            lyrics.syncedLyrics != null -> lyrics.syncedLyrics
            lyrics.plainLyrics != null -> lyrics.plainLyrics
            else -> null
        }
    }
}


