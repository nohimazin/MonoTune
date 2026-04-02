package com.nohimazin.monotune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranscodeMetadataVerifierTest {

    @Test
    fun `verify succeeds when metadata is preserved`() {
        val source = TranscodeMetadataSnapshot(
            title = "Title",
            artist = "Artist",
            album = "Album",
            albumArtist = "Album Artist",
            genre = "Genre",
            date = "2026-04-02",
            trackNumber = "3",
            discNumber = "1",
            lyrics = "[00:01.00] line 1",
            hasEmbeddedPicture = true,
        )
        val actual = source.copy()

        val result = TranscodeMetadataVerifier.verify(source, actual)

        assertTrue(result.isSuccess)
        assertEquals(0, result.mismatches.size)
    }

    @Test
    fun `verify reports missing metadata and cover art`() {
        val source = TranscodeMetadataSnapshot(
            title = "Title",
            artist = "Artist",
            album = "Album",
            albumArtist = null,
            genre = "Genre",
            date = null,
            trackNumber = "3",
            discNumber = null,
            lyrics = "[00:01.00] line 1",
            hasEmbeddedPicture = true,
        )
        val actual = source.copy(
            artist = "Different Artist",
            lyrics = null,
            hasEmbeddedPicture = false,
        )

        val result = TranscodeMetadataVerifier.verify(source, actual)

        assertFalse(result.isSuccess)
        assertTrue(result.mismatches.any { it.field == "artist" })
        assertTrue(result.mismatches.any { it.field == "lyrics" })
        assertTrue(result.mismatches.any { it.field == "embeddedPicture" })
    }

    @Test
    fun `buildCommand keeps metadata and attached stream flags`() {
        val sourceFile = File("/tmp/source.m4a")
        val targetFile = File("/tmp/target.mp3")

        val command = AudioTranscoder.buildCommand(sourceFile, targetFile, "MP3", 192)

        assertTrue(command.contains("-map 0"))
        assertTrue(command.contains("-map_metadata 0"))
        assertTrue(command.contains("-map_chapters 0"))
        assertTrue(command.contains("-c:a libmp3lame"))
        assertTrue(command.contains("-b:a 192k"))
        assertTrue(command.contains("-c:v copy"))
        assertTrue(command.contains("-c:s copy"))
        assertTrue(command.contains(sourceFile.absolutePath))
        assertTrue(command.contains(targetFile.absolutePath))
    }
}