/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.viewmodels

import com.nohimazin.monotune.db.entities.ManualCorrection
import com.nohimazin.monotune.db.entities.MonochromeTrackMatch
import com.nohimazin.monotune.db.entities.PlaylistSong

/**
 * Effective Monochrome match state for a single playlist song.
 *
 * Priority mirrors [com.nohimazin.monotune.lyrics.MonochromeLyricsProvider.resolveMonochromeId]:
 *  1. [ManualCorrection] wins
 *  2. fallback to [MonochromeTrackMatch]
 */
enum class MonochromeMatchStatus {
    /** Automatic match from the track-matching pipeline. */
    MATCHED_AUTO,

    /** User has explicitly selected a Monochrome track for this song. */
    MATCHED_MANUAL,

    /**
     * User has explicitly marked this song as unavailable on Monochrome
     * ([ManualCorrection.correctedMonochromeId] == null).
     */
    EXPLICITLY_UNAVAILABLE,

    /** No automatic match and no manual correction exists yet. */
    UNRESOLVED,
}

/**
 * Wraps a [PlaylistSong] with its current Monochrome match data so that the UI
 * can display per-song status badges and group songs into sections.
 */
data class PlaylistSongWithStatus(
    val playlistSong: PlaylistSong,
    val autoMatch: MonochromeTrackMatch?,
    val manualCorrection: ManualCorrection?,
) {
    /** Derived status applying manual-correction-first priority. */
    val status: MonochromeMatchStatus
        get() = when {
            manualCorrection != null && manualCorrection.correctedMonochromeId == null ->
                MonochromeMatchStatus.EXPLICITLY_UNAVAILABLE
            manualCorrection != null ->
                MonochromeMatchStatus.MATCHED_MANUAL
            autoMatch != null ->
                MonochromeMatchStatus.MATCHED_AUTO
            else ->
                MonochromeMatchStatus.UNRESOLVED
        }

    /** The effective Monochrome ID to use for playback/lyrics, or null if unavailable. */
    val effectiveMonochromeId: String?
        get() = manualCorrection?.correctedMonochromeId ?: autoMatch?.monochromeId
}

