package com.nohimazin.monotune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsPlusPublicLyricsProviderTest {

    @Test
    fun `parseLyrics prefers synced lyrics when present`() {
        val json = """
            {
              "syncedLyrics": "[00:01.00] line1",
              "plainLyrics": "plain"
            }
        """.trimIndent()

        val result = LyricsPlusPublicLyricsProvider.parseLyrics(json)

        assertEquals("[00:01.00] line1", result)
    }

    @Test
    fun `parseLyrics falls back to plain lyrics`() {
        val json = """
            {
              "plainLyrics": "plain only"
            }
        """.trimIndent()

        val result = LyricsPlusPublicLyricsProvider.parseLyrics(json)

        assertEquals("plain only", result)
    }

    @Test
    fun `parseLyrics extracts nested line array`() {
        val json = """
            {
              "data": {
                "lyricsData": [
                  {"text": "line A"},
                  {"line": "line B"}
                ]
              }
            }
        """.trimIndent()

        val result = LyricsPlusPublicLyricsProvider.parseLyrics(json)

        assertEquals("line A\nline B", result)
    }

    @Test
    fun `parseLyrics returns null for invalid json`() {
        val result = LyricsPlusPublicLyricsProvider.parseLyrics("not-json")

        assertNull(result)
    }
}
