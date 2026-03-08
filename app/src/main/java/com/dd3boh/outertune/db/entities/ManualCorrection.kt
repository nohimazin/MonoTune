/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores user-supplied corrections that override the automatic [MonochromeTrackMatch].
 *
 * When a user manually selects a different Monochrome track for a YTM song, or
 * explicitly marks a track as unavailable, the correction is stored here and
 * takes priority over any automatically computed match.
 *
 * @property ytmId               YouTube Music track ID (foreign key → [SongEntity.id]).
 * @property correctedMonochromeId  The Monochrome track ID chosen by the user, or `null`
 *                               if the user has explicitly marked the track as unavailable.
 * @property correctedAt         Unix epoch milliseconds when the correction was saved.
 * @property note                Optional free-text note added by the user.
 */
@Entity(
    tableName = "manual_correction",
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
data class ManualCorrection(
    @PrimaryKey val ytmId: String,
    val correctedMonochromeId: String?,
    val correctedAt: Long,
    val note: String? = null,
)
