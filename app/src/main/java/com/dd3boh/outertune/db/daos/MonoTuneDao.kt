/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.dd3boh.outertune.db.entities.ManualCorrection
import com.dd3boh.outertune.db.entities.MonochromeTrackMatch
import com.dd3boh.outertune.db.entities.YtmScrobbleQueue
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the three MonoTune-specific tables:
 * - [MonochromeTrackMatch]: auto-generated YTM → Monochrome catalog matches
 * - [ManualCorrection]: user-supplied overrides for track matching
 * - [YtmScrobbleQueue]: playback events pending submission to YTM history
 */
@Dao
interface MonoTuneDao {

    // -------------------------------------------------------------------------
    // MonochromeTrackMatch
    // -------------------------------------------------------------------------

    /** Returns the Monochrome match for the given YTM track ID, or `null` if none exists. */
    @Query("SELECT * FROM monochrome_track_match WHERE ytmId = :ytmId")
    fun getMonochromeMatch(ytmId: String): Flow<MonochromeTrackMatch?>

    /** Returns all Monochrome matches, ordered by confidence (highest first). */
    @Query("SELECT * FROM monochrome_track_match ORDER BY confidence DESC")
    fun getAllMonochromeMatches(): Flow<List<MonochromeTrackMatch>>

    /**
     * Inserts or replaces the Monochrome match for a YTM track.
     * Use this when the matching pipeline produces a new or updated result.
     */
    @Upsert
    fun upsertMonochromeMatch(match: MonochromeTrackMatch)

    /** Removes the automatic Monochrome match for the given YTM track ID. */
    @Query("DELETE FROM monochrome_track_match WHERE ytmId = :ytmId")
    fun deleteMonochromeMatch(ytmId: String)

    // -------------------------------------------------------------------------
    // ManualCorrection
    // -------------------------------------------------------------------------

    /** Returns the user correction for the given YTM track ID, or `null` if none exists. */
    @Query("SELECT * FROM manual_correction WHERE ytmId = :ytmId")
    fun getManualCorrection(ytmId: String): Flow<ManualCorrection?>

    /** Returns all user corrections. */
    @Query("SELECT * FROM manual_correction ORDER BY correctedAt DESC")
    fun getAllManualCorrections(): Flow<List<ManualCorrection>>

    /**
     * Inserts or replaces a user correction.
     * A `null` [ManualCorrection.correctedMonochromeId] means the user has explicitly
     * marked the track as unavailable on Monochrome.
     */
    @Upsert
    fun upsertManualCorrection(correction: ManualCorrection)

    /** Removes the user correction for the given YTM track ID. */
    @Query("DELETE FROM manual_correction WHERE ytmId = :ytmId")
    fun deleteManualCorrection(ytmId: String)

    // -------------------------------------------------------------------------
    // YtmScrobbleQueue
    // -------------------------------------------------------------------------

    /** Enqueues a new playback event to be scrobbled to YTM. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun enqueueScrobble(entry: YtmScrobbleQueue)

    /**
     * Returns all pending (not yet scrobbled) entries, ordered by play time.
     * A background worker should call this to drain the queue.
     */
    @Query("SELECT * FROM ytm_scrobble_queue WHERE scrobbled = 0 ORDER BY playedAt ASC")
    fun getPendingScrobbles(): Flow<List<YtmScrobbleQueue>>

    /** Marks a single scrobble entry as successfully submitted. */
    @Query("UPDATE ytm_scrobble_queue SET scrobbled = 1 WHERE id = :id")
    fun markScrobbled(id: Long)

    /** Removes all entries that have already been successfully submitted to YTM. */
    @Query("DELETE FROM ytm_scrobble_queue WHERE scrobbled = 1")
    fun deleteScrobbled()

    /** Removes a specific scrobble entry (e.g. after a permanent failure). */
    @Delete
    fun deleteScrobble(entry: YtmScrobbleQueue)
}
