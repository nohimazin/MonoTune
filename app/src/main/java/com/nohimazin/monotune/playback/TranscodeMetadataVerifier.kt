package com.nohimazin.monotune.playback

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

data class TranscodeMetadataSnapshot(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val date: String?,
    val trackNumber: String?,
    val discNumber: String?,
    val lyrics: String?,
    val hasEmbeddedPicture: Boolean,
)

data class TranscodeMetadataMismatch(
    val field: String,
    val expected: String,
    val actual: String?,
)

data class TranscodeMetadataVerificationResult(
    val mismatches: List<TranscodeMetadataMismatch>,
) {
    val isSuccess: Boolean
        get() = mismatches.isEmpty()

    fun describe(): String = if (mismatches.isEmpty()) {
        "metadata preserved"
    } else {
        mismatches.joinToString(separator = "; ") { mismatch ->
            "${mismatch.field}: expected=${mismatch.expected}, actual=${mismatch.actual.orEmpty()}"
        }
    }
}

object TranscodeMetadataVerifier {
    private const val TAG = "TranscodeMetadataVerifier"

    fun snapshotFrom(file: File): TranscodeMetadataSnapshot? {
        if (!file.exists()) return null

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            TranscodeMetadataSnapshot(
                title = extractMetadata(retriever, "METADATA_KEY_TITLE"),
                artist = extractMetadata(retriever, "METADATA_KEY_ARTIST"),
                album = extractMetadata(retriever, "METADATA_KEY_ALBUM"),
                albumArtist = extractMetadata(retriever, "METADATA_KEY_ALBUMARTIST"),
                genre = extractMetadata(retriever, "METADATA_KEY_GENRE"),
                date = extractMetadata(retriever, "METADATA_KEY_DATE"),
                trackNumber = extractMetadata(retriever, "METADATA_KEY_CD_TRACK_NUMBER")
                    ?: extractMetadata(retriever, "METADATA_KEY_TRACK_NUMBER"),
                discNumber = extractMetadata(retriever, "METADATA_KEY_DISC_NUMBER"),
                lyrics = extractMetadata(retriever, "METADATA_KEY_LYRIC")
                    ?: extractMetadata(retriever, "METADATA_KEY_LYRICS"),
                hasEmbeddedPicture = retriever.embeddedPicture != null,
            )
        } catch (throwable: Throwable) {
            Log.w(TAG, "Failed to inspect metadata for ${file.absolutePath}: ${throwable.message}")
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // Ignore release failures.
            }
        }
    }

    fun verify(expected: TranscodeMetadataSnapshot, actual: TranscodeMetadataSnapshot): TranscodeMetadataVerificationResult {
        val mismatches = buildList {
            compareTextField("title", expected.title, actual.title)
            compareTextField("artist", expected.artist, actual.artist)
            compareTextField("album", expected.album, actual.album)
            compareTextField("albumArtist", expected.albumArtist, actual.albumArtist)
            compareTextField("genre", expected.genre, actual.genre)
            compareTextField("date", expected.date, actual.date)
            compareTextField("trackNumber", expected.trackNumber, actual.trackNumber)
            compareTextField("discNumber", expected.discNumber, actual.discNumber)
            compareTextField("lyrics", expected.lyrics, actual.lyrics)
            if (expected.hasEmbeddedPicture && !actual.hasEmbeddedPicture) {
                add(
                    TranscodeMetadataMismatch(
                        field = "embeddedPicture",
                        expected = "present",
                        actual = "missing",
                    )
                )
            }
        }

        return TranscodeMetadataVerificationResult(mismatches)
    }

    private fun MutableList<TranscodeMetadataMismatch>.compareTextField(
        field: String,
        expected: String?,
        actual: String?,
    ) {
        val normalizedExpected = expected?.trim().orEmpty()
        if (normalizedExpected.isBlank()) return

        val normalizedActual = actual?.trim().orEmpty()
        if (normalizedExpected != normalizedActual) {
            add(
                TranscodeMetadataMismatch(
                    field = field,
                    expected = normalizedExpected,
                    actual = normalizedActual.ifBlank { null },
                )
            )
        }
    }

    private fun extractMetadata(retriever: MediaMetadataRetriever, fieldName: String): String? {
        val key = runCatching {
            MediaMetadataRetriever::class.java.getField(fieldName).getInt(null)
        }.getOrNull() ?: return null

        return retriever.extractMetadata(key)?.trim()?.ifBlank { null }
    }
}