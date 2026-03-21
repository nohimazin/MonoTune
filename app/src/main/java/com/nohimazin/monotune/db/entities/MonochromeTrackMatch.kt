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
 * Stores the result of matching a YouTube Music track to a Monochrome catalog entry.
 *
 * Rows are created automatically by the track-matching pipeline.  Manual user
 * corrections are stored separately in [ManualCorrection] and take precedence
 * over this table at playback time.
 *
 * @property ytmId         YouTube Music track ID (foreign key Å® [SongEntity.id]).
 * @property monochromeId  Matched Monochrome track identifier.
 * @property confidence    Match confidence in [0.0, 1.0]; higher is more certain.
 * @property matchedAt     Unix epoch milliseconds when the match was recorded.
 */
@Entity(
    tableName = "monochrome_track_match",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["ytmId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["ytmId"])]
)
data class MonochromeTrackMatch(
    @PrimaryKey val ytmId: String,
    val monochromeId: String,
    val confidence: Float,
    val matchedAt: Long,
)

