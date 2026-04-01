package com.nohimazin.monotune.playback

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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

        val codec = when (format.uppercase()) {
            "AAC" -> "aac"
            "OPUS" -> "libopus"
            "MP3" -> "libmp3lame"
            "OGG" -> "libvorbis"
            else -> "aac"
        }

        // -y to overwrite output file if it exists
        val command = "-y -i \"${sourceFile.absolutePath}\" -c:a $codec -b:a ${bitrate}k \"${targetFile.absolutePath}\""
        
        Log.d(TAG, "Executing FFmpeg command: $command")
        
        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        return if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "Transcoding successful: ${targetFile.absolutePath}")
            true
        } else {
            Log.e(TAG, "Transcoding failed with state ${session.state} and rc $returnCode")
            false
        }
    }
}
