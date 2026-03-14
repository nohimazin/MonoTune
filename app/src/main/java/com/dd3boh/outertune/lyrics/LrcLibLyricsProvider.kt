package com.dd3boh.outertune.lyrics

import android.content.Context
import com.dd3boh.lrclib.LrcLib
import com.nohimazin.monotune.constants.EnableLrcLibKey
import com.nohimazin.monotune.utils.dataStore
import com.nohimazin.monotune.utils.get

/**
 * Source: https://github.com/Malopieds/InnerTune
 */
object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
