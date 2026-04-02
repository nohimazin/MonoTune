package com.nohimazin.monotune.lyrics

import android.content.Context
import com.nohimazin.monotune.constants.EnableLyricsPlusPublicKey
import com.nohimazin.monotune.utils.dataStore
import com.nohimazin.monotune.utils.get
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLyricsPlusPublicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        val query = buildQuery(title, artist, duration)
        for (base in baseUrls) {
            val parsed = fetchLyricsFromBase(base, query)
            if (!parsed.isNullOrBlank()) {
                return Result.success(parsed)
            }
        }
        return Result.failure(LyricsNotFoundException("No result from public LyricsPlus endpoints"))
    }

    private suspend fun fetchLyricsFromBase(base: String, query: String): String? =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url("$base/v2/lyrics/get?$query")
                .get()
                .build()

            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val parsed = response.use {
                        if (!it.isSuccessful) {
                            null
                        } else {
                            parseLyrics(it.body?.string().orEmpty())
                        }
                    }
                    if (continuation.isActive) {
                        continuation.resume(parsed)
                    }
                }
            })
        }

    private fun buildQuery(title: String, artist: String, duration: Int): String {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val durationMs = (duration.coerceAtLeast(0) * 1000L).toString()
        return "title=$encodedTitle&artist=$encodedArtist&duration=$durationMs"
    }

    internal fun parseLyrics(raw: String): String? {
        if (raw.isBlank()) return null

        // Fast-path extraction that does not depend on a specific JSON implementation.
        extractQuotedValue(raw, "syncedLyrics")?.let { return it }
        extractQuotedValue(raw, "plainLyrics")?.let { return it }
        extractQuotedValue(raw, "lyrics")?.let { return it }
        extractQuotedValue(raw, "lrc")?.let { return it }
        extractQuotedValue(raw, "text")?.let { return it }

        extractLineArrayFromRaw(raw)?.let { return it }

        return runCatching {
            val root = JSONObject(raw)
            extractText(root)
        }.getOrNull()
    }

    private fun extractQuotedValue(raw: String, key: String): String? {
        val regex = Regex("\\\"$key\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
        val value = regex.find(raw)?.groupValues?.getOrNull(1) ?: return null
        val decoded = value
            .replace("\\\\n", "\n")
            .replace("\\\\r", "\r")
            .replace("\\\\t", "\t")
            .replace("\\\\\"", "\"")
            .replace("\\\\\\\\", "\\")
            .trim()
        return decoded.ifBlank { null }
    }

    private fun extractLineArrayFromRaw(raw: String): String? {
        val regex = Regex("\\\"(?:text|line)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
        val lines = regex.findAll(raw)
            .map { it.groupValues[1] }
            .map {
                it.replace("\\\\n", "\n")
                    .replace("\\\\r", "\r")
                    .replace("\\\\t", "\t")
                    .replace("\\\\\"", "\"")
                    .replace("\\\\\\\\", "\\")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
        return lines.joinToString("\n").ifBlank { null }
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