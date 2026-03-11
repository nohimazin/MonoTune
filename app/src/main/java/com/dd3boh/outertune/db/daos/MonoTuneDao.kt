/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.dd3boh.outertune.db.entities.ManualCorrection
import com.dd3boh.outertune.db.entities.MonochromeTrackMatch
import com.dd3boh.outertune.db.entities.YtmScrobbleQueue
import kotlinx.coroutines.flow.Flow

/**
 * DAO for MonoTune-specific tables: [MonochromeTrackMatch], [ManualCorrection],
 * and [YtmScrobbleQueue].
 *
 * Exposes through [com.dd3boh.outertune.db.DatabaseDao] which extends this interface.
 */
@Dao
interface MonoTuneDao {

    // region MonochromeTrackMatch

    /**
     * Insert or replace an automatic track match.
     */
    @Upsert
    suspend fun upsertTrackMatch(match: MonochromeTrackMatch)

    /**
     * Return the automatic match for a given YTM track ID, or `null` if none exists.
     */
    @Query("SELECT * FROM monochrome_track_match WHERE ytmId = :ytmId")
    suspend fun getTrackMatch(ytmId: String): MonochromeTrackMatch?

    /**
     * Observe the automatic match for a given YTM track ID reactively.
     */
    @Query("SELECT * FROM monochrome_track_match WHERE ytmId = :ytmId")
    fun trackMatchFlow(ytmId: String): Flow<MonochromeTrackMatch?>

    /**
     * Delete the automatic match for a given YTM track ID.
     */
    @Query("DELETE FROM monochrome_track_match WHERE ytmId = :ytmId")
    suspend fun deleteTrackMatch(ytmId: String)

    // endregion

    // region ManualCorrection

    /**
     * Insert or replace a user-supplied manual correction.
     */
    @Upsert
    suspend fun upsertManualCorrection(correction: ManualCorrection)

    /**
     * Return the manual correction for a given YTM track ID, or `null` if the user
     * has not overridden the automatic match.
     */
    @Query("SELECT * FROM manual_correction WHERE ytmId = :ytmId")
    suspend fun getManualCorrection(ytmId: String): ManualCorrection?

    /**
     * Observe the manual correction for a given YTM track ID reactively.
     */
    @Query("SELECT * FROM manual_correction WHERE ytmId = :ytmId")
    fun manualCorrectionFlow(ytmId: String): Flow<ManualCorrection?>

    /**
     * Delete the manual correction for a given YTM track ID, reverting to the
     * automatic match (if one exists).
     */
    @Query("DELETE FROM manual_correction WHERE ytmId = :ytmId")
    suspend fun deleteManualCorrection(ytmId: String)

    // endregion

    // region YtmScrobbleQueue

    /**
     * Enqueue a new scrobble event.
     *
     * The [YtmScrobbleQueue.id] is auto-generated; pass `id = 0` (the default).
     */
    @Upsert
    suspend fun enqueueScrobble(event: YtmScrobbleQueue)

    /**
     * Return all scrobble events that have not yet been submitted to YTM.
     */
    @Query("SELECT * FROM ytm_scrobble_queue WHERE scrobbled = 0 ORDER BY playedAt ASC")
    suspend fun getPendingScrobbles(): List<YtmScrobbleQueue>

    /**
     * Observe pending (unsubmitted) scrobble events reactively.
     */
    @Query("SELECT * FROM ytm_scrobble_queue WHERE scrobbled = 0 ORDER BY playedAt ASC")
    fun pendingScrobblesFlow(): Flow<List<YtmScrobbleQueue>>

    /**
     * Mark a scrobble event as successfully submitted to YTM.
     *
     * @param id  The [YtmScrobbleQueue.id] of the event to mark.
     */
    @Query("UPDATE ytm_scrobble_queue SET scrobbled = 1 WHERE id = :id")
    suspend fun markScrobbled(id: Long)

    /**
     * Delete all scrobble events that have already been submitted.
     *
     * Call this periodically to keep the queue lean.
     */
    @Query("DELETE FROM ytm_scrobble_queue WHERE scrobbled = 1")
    suspend fun purgeSubmittedScrobbles()

    // endregion
}
