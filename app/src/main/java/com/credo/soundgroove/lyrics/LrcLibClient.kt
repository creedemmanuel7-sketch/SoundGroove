package com.credo.soundgroove.lyrics

import com.credo.soundgroove.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Client minimal pour l'API publique LRCLIB (sans clé).
 * Usage raisonnable : cache interne d'abord, puis get-cached, puis get, puis search.
 */
object LrcLibClient {

    private const val BASE_URL = "https://lrclib.net"
    private const val USER_AGENT = "SoundGroove/1.0 (com.credo.soundgroove)"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val DURATION_TOLERANCE_SEC = 2

    data class LrcLibResult(
        val syncedLyrics: String?,
        val plainLyrics: String?,
        val instrumental: Boolean
    ) {
        fun bestRawText(): String? =
            syncedLyrics?.takeIf { it.isNotBlank() }
                ?: plainLyrics?.takeIf { it.isNotBlank() }
    }

    /** Tente de récupérer les paroles en ligne. Retourne null si rien ou erreur réseau. */
    fun fetchLyrics(song: Song): LrcLibResult? {
        val durationSec = durationSeconds(song)
        if (durationSec != null) {
            fetchBySignature(song, durationSec, cachedOnly = true)?.let { return it }
            fetchBySignature(song, durationSec, cachedOnly = false)?.let { return it }
        }
        return fetchBySearch(song, durationSec)
    }

    private fun durationSeconds(song: Song): Long? {
        if (song.duration <= 0L) return null
        return (song.duration / 1_000L).coerceAtLeast(1L)
    }

    private fun fetchBySignature(
        song: Song,
        durationSec: Long,
        cachedOnly: Boolean
    ): LrcLibResult? {
        val endpoint = if (cachedOnly) "get-cached" else "get"
        val url = buildString {
            append("$BASE_URL/api/$endpoint?")
            append("track_name=${encode(song.title)}")
            append("&artist_name=${encode(song.artist)}")
            append("&album_name=${encode(song.albumName.ifBlank { "Unknown" })}")
            append("&duration=$durationSec")
        }
        val body = httpGet(url) ?: return null
        return parseRecord(body)
    }

    private fun fetchBySearch(song: Song, durationSec: Long?): LrcLibResult? {
        val url = buildString {
            append("$BASE_URL/api/search?")
            append("track_name=${encode(song.title)}")
            append("&artist_name=${encode(song.artist)}")
        }
        val body = httpGet(url) ?: return null
        return runCatching {
            val array = JSONArray(body)
            var best: JSONObject? = null
            var bestDelta = Long.MAX_VALUE
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.optBoolean("instrumental", false)) continue
                if (!hasLyrics(item)) continue

                if (durationSec != null) {
                    val itemDuration = item.optInt("duration", -1)
                    if (itemDuration <= 0) continue
                    val delta = abs(itemDuration - durationSec)
                    if (delta <= DURATION_TOLERANCE_SEC && delta < bestDelta) {
                        best = item
                        bestDelta = delta
                    }
                } else if (best == null) {
                    best = item
                }
            }
            best?.let { parseRecord(it) }
        }.getOrNull()
    }

    private fun parseRecord(body: String): LrcLibResult? = runCatching {
        parseRecord(JSONObject(body))
    }.getOrNull()

    private fun parseRecord(json: JSONObject): LrcLibResult? {
        if (json.has("code") && json.optInt("code") == 404) return null
        val result = LrcLibResult(
            syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() },
            plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() },
            instrumental = json.optBoolean("instrumental", false)
        )
        return result.takeIf { !it.instrumental && it.bestRawText() != null }
    }

    private fun hasLyrics(json: JSONObject): Boolean {
        val synced = json.optString("syncedLyrics")
        val plain = json.optString("plainLyrics")
        return synced.isNotBlank() || plain.isNotBlank()
    }

    private fun httpGet(urlString: String): String? = runCatching {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> null
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
