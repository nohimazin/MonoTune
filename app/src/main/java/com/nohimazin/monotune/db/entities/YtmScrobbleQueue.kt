/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.nohimazin.monotune.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Queue of playback events waiting to be scrobbled back to YouTube Music.
 *
 * Each row represents a single completed (or near-completed) track play that
 * has not yet been reported to the YTM "mark as played" / history endpoint.
 * A background worker drains this queue and removes rows after successful
 * submission.
 *
 * @property id              Auto-generated row ID.
 * @property songId          The song that was played (foreign key Å® [SongEntity.id]).
 * @property playedAt        Unix epoch milliseconds when playback started.
 * @property durationPlayedMs  How many milliseconds of the track were actually played.
 * @property scrobbled       `true` once the event has been successfully submitted to YTM.
 */
@Entity(
    tableName = "ytm_scrobble_queue",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["songId"]), Index(value = ["scrobbled"])]
)
data class YtmScrobbleQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long,
    val durationPlayedMs: Long,
    val scrobbled: Boolean = false,
)

