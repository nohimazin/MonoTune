package com.nohimazin.monotune.playback

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioTranscoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AudioTranscoder"

    /**
     * Transcodes an audio file to the specified format and bitrate.
     * @param sourceFile The source audio file.
     * @param targetFile The destination audio file.
     * @param format The target format (AAC, OPUS, MP3, OGG).
     * @param bitrate The target bitrate in kbps.
     * @return True if successful, false otherwise.
     */
    fun transcode(
        sourceFile: File,
        targetFile: File,
        format: String,
        bitrate: Int
    ): Boolean {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return false
        }

        val command = buildCommand(sourceFile, targetFile, format, bitrate)

        Log.d(TAG, "Executing FFmpeg command: $command")

        return try {
            val ffmpegKitClass = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
            val returnCodeClass = Class.forName("com.arthenica.ffmpegkit.ReturnCode")
            val session = ffmpegKitClass.getMethod("execute", String::class.java).invoke(null, command)
            val returnCode = session.javaClass.getMethod("getReturnCode").invoke(session)
            val isSuccess = returnCodeClass.getMethod("isSuccess", returnCodeClass).invoke(null, returnCode) as Boolean

            if (isSuccess) {
                Log.d(TAG, "Transcoding successful: ${targetFile.absolutePath}")
                true
            } else {
                val state = session.javaClass.getMethod("getState").invoke(session)
                Log.e(TAG, "Transcoding failed with state $state and rc $returnCode")
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FFmpeg runtime not available or transcoding failed", e)
            false
        }
    }

    internal companion object {
        internal data class FormatProfile(
            val codec: String,
            val muxer: String,
            val extension: String,
        )

        internal fun profileForFormat(format: String): FormatProfile = when (format.uppercase()) {
            "AAC" -> FormatProfile(codec = "aac", muxer = "adts", extension = "aac")
            "OPUS" -> FormatProfile(codec = "libopus", muxer = "opus", extension = "opus")
            "MP3" -> FormatProfile(codec = "libmp3lame", muxer = "mp3", extension = "mp3")
            "OGG" -> FormatProfile(codec = "libvorbis", muxer = "ogg", extension = "ogg")
            else -> FormatProfile(codec = "aac", muxer = "adts", extension = "aac")
        }

        internal fun buildCommand(
            sourceFile: File,
            targetFile: File,
            format: String,
            bitrate: Int,
        ): String {
            val profile = profileForFormat(format)

            return buildString {
                append("-y -i \"")
                append(sourceFile.absolutePath)
                append("\" -map 0 -map_metadata 0 -map_chapters 0 -c:a ")
                append(profile.codec)
                append(" -b:a ")
                append(bitrate)
                append("k -c:v copy -c:s copy -f ")
                append(profile.muxer)
                append(" \"")
                append(targetFile.absolutePath)
                append("\"")
            }
        }
    }
}
