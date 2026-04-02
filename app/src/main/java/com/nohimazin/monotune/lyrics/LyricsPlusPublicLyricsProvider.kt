package com.nohimazin.monotune.lyrics

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Public LyricsPlus endpoint provider.
 *
 * This provider is intentionally fail-soft: any network/format issue is treated
 * as an expected miss so that downstream providers can continue immediately.
 */
object LyricsPlusPublicLyricsProvider : LyricsProvider {
    override val name = "LyricsPlus (Public)"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private val baseUrls = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyrics-plus-backend.vercel.app",
    )

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        val query = buildQuery(title, artist, duration)
        for (base in baseUrls) {
            val url = "$base/v2/lyrics/get?$query"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = try {
                httpClient.newCall(request).execute()
            } catch (_: Exception) {
                continue
            }
            val parsed = response.use {
                if (!it.isSuccessful) {
                    null
                } else {
                    val body = it.body?.string().orEmpty()
                    parseLyrics(body)
                }
            }
            if (!parsed.isNullOrBlank()) {
                return Result.success(parsed)
            }
        }
        return Result.failure(LyricsNotFoundException("No result from public LyricsPlus endpoints"))
    }

    private fun buildQuery(title: String, artist: String, duration: Int): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val durationMs = (duration.coerceAtLeast(0) * 1000L).toString()
        return "title=$encodedTitle&artist=$encodedArtist&duration=$durationMs"
    }

    private fun parseLyrics(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            extractText(root)
        }.getOrNull()
    }

    private fun extractText(json: JSONObject): String? {
        val directKeys = listOf("syncedLyrics", "plainLyrics", "lyrics", "lrc", "text")
        directKeys.forEach { key ->
            val value = json.optString(key, "").trim()
            if (value.isNotBlank()) return value
        }

        val nestedKeys = listOf("data", "result", "lyricsData")
        nestedKeys.forEach { key ->
            val nestedObj = json.optJSONObject(key)
            if (nestedObj != null) {
                val nestedText = extractText(nestedObj)
                if (!nestedText.isNullOrBlank()) return nestedText
            }
            val nestedArray = json.optJSONArray(key)
            if (nestedArray != null) {
                val nestedArrayText = extractFromArray(nestedArray)
                if (!nestedArrayText.isNullOrBlank()) return nestedArrayText
            }
        }

        json.keys().forEach { key ->
            val value = json.opt(key)
            when (value) {
                is JSONObject -> {
                    val nestedText = extractText(value)
                    if (!nestedText.isNullOrBlank()) return nestedText
                }

                is JSONArray -> {
                    val nestedArrayText = extractFromArray(value)
                    if (!nestedArrayText.isNullOrBlank()) return nestedArrayText
                }
            }
        }
        return null
    }

    private fun extractFromArray(array: JSONArray): String? {
        val lines = buildList {
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                when (item) {
                    is String -> if (item.isNotBlank()) add(item)
                    is JSONObject -> {
                        val line = item.optString("text", item.optString("line", "")).trim()
                        if (line.isNotBlank()) add(line)
                    }
                }
            }
        }
        return lines.joinToString("\n").ifBlank { null }
    }
}