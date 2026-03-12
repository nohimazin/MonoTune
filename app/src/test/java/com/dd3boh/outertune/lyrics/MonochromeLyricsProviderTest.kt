/*
 * Copyright (C) 2025 MonoTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.lyrics

import com.dd3boh.outertune.db.entities.ManualCorrection
import com.dd3boh.outertune.db.entities.MonochromeTrackMatch
import com.dd3boh.outertune.db.daos.MonoTuneDao
import com.dd3boh.outertune.db.entities.YtmScrobbleQueue
import com.dd3boh.outertune.monochrome.MonochromeLyrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/** Simple in-memory fake for [MonoTuneDao] — no Room, no Android context needed. */
private class FakeMonoTuneDao(
    private val corrections: Map<String, ManualCorrection?> = emptyMap(),
    private val matches: Map<String, MonochromeTrackMatch?> = emptyMap(),
) : MonoTuneDao {

    override suspend fun upsertTrackMatch(match: MonochromeTrackMatch) = Unit
    override suspend fun getTrackMatch(ytmId: String) = matches[ytmId]
    override fun trackMatchFlow(ytmId: String): Flow<MonochromeTrackMatch?> = flowOf(matches[ytmId])
    override suspend fun deleteTrackMatch(ytmId: String) = Unit

    override suspend fun upsertManualCorrection(correction: ManualCorrection) = Unit
    override suspend fun getManualCorrection(ytmId: String) = corrections[ytmId]
    override fun manualCorrectionFlow(ytmId: String): Flow<ManualCorrection?> = flowOf(corrections[ytmId])
    override suspend fun deleteManualCorrection(ytmId: String) = Unit

    override suspend fun enqueueScrobble(event: YtmScrobbleQueue) = Unit
    override suspend fun getPendingScrobbles(): List<YtmScrobbleQueue> = emptyList()
    override fun pendingScrobblesFlow(): Flow<List<YtmScrobbleQueue>> = flowOf(emptyList())
    override suspend fun markScrobbled(id: Long) = Unit
    override suspend fun purgeSubmittedScrobbles() = Unit
}

// ---------------------------------------------------------------------------
// Tests: resolveMonochromeId
// ---------------------------------------------------------------------------

class MonochromeLyricsProviderTest {

    private val ytmId = "ytm_abc"

    // --- resolveMonochromeId ---

    @Test
    fun `resolveMonochromeId returns correctedMonochromeId from manual correction`() = runBlocking {
        val dao = FakeMonoTuneDao(
            corrections = mapOf(ytmId to ManualCorrection(ytmId, "mono_123", 0L)),
            matches = mapOf(ytmId to MonochromeTrackMatch(ytmId, "mono_auto", 0.9f, 0L)),
        )
        val result = MonochromeLyricsProvider.resolveMonochromeId(dao, ytmId)
        assertEquals("mono_123", result)
    }

    @Test
    fun `resolveMonochromeId returns null when manual correction marks track unavailable`() = runBlocking {
        val dao = FakeMonoTuneDao(
            corrections = mapOf(ytmId to ManualCorrection(ytmId, null, 0L)),
            matches = mapOf(ytmId to MonochromeTrackMatch(ytmId, "mono_auto", 0.9f, 0L)),
        )
        val result = MonochromeLyricsProvider.resolveMonochromeId(dao, ytmId)
        assertNull(result)
    }

    @Test
    fun `resolveMonochromeId falls back to auto match when no correction exists`() = runBlocking {
        val dao = FakeMonoTuneDao(
            corrections = emptyMap(),
            matches = mapOf(ytmId to MonochromeTrackMatch(ytmId, "mono_auto", 0.9f, 0L)),
        )
        val result = MonochromeLyricsProvider.resolveMonochromeId(dao, ytmId)
        assertEquals("mono_auto", result)
    }

    @Test
    fun `resolveMonochromeId returns null when neither correction nor match exists`() = runBlocking {
        val dao = FakeMonoTuneDao()
        val result = MonochromeLyricsProvider.resolveMonochromeId(dao, ytmId)
        assertNull(result)
    }

    // --- selectLyricsText ---

    @Test
    fun `selectLyricsText returns synced lyrics when both are present`() {
        val lyrics = MonochromeLyrics("Title", "Artist", "Plain text", "[00:01.00] Line 1")
        assertEquals("[00:01.00] Line 1", MonochromeLyricsProvider.selectLyricsText(lyrics))
    }

    @Test
    fun `selectLyricsText returns plain lyrics when synced is absent`() {
        val lyrics = MonochromeLyrics("Title", "Artist", "Plain text", null)
        assertEquals("Plain text", MonochromeLyricsProvider.selectLyricsText(lyrics))
    }

    @Test
    fun `selectLyricsText returns null for instrumental tracks`() {
        val lyrics = MonochromeLyrics("Title", "Artist", null, null, instrumental = true)
        assertNull(MonochromeLyricsProvider.selectLyricsText(lyrics))
    }

    @Test
    fun `selectLyricsText returns null when lyrics object is null`() {
        assertNull(MonochromeLyricsProvider.selectLyricsText(null))
    }

    @Test
    fun `selectLyricsText returns null when both plainLyrics and syncedLyrics are null`() {
        val lyrics = MonochromeLyrics("Title", "Artist", null, null)
        assertNull(MonochromeLyricsProvider.selectLyricsText(lyrics))
    }
}
