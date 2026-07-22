package com.credo.soundgroove.data.repository

import android.content.Context
import com.credo.soundgroove.data.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ScrobbleEntry(
    val songId: Long,
    val title: String,
    val artist: String,
    val playCount: Int,
    val lastScrobbledAt: Long,
    val totalListenMs: Long,
)

data class TopTrackStat(
    val songId: Long,
    val title: String,
    val artist: String,
    val playCount: Int,
)

data class TopArtistStat(
    val artist: String,
    val playCount: Int,
)

data class LocalScrobbleStats(
    val totalScrobbles: Int,
    val uniqueTracks: Int,
    val topTracks: List<TopTrackStat>,
    val topArtists: List<TopArtistStat>,
)

/**
 * Scrobbling **100 % local** (SharedPreferences JSON) — aucun compte cloud.
 *
 * Une écoute compte lorsque la position dépasse [scrobbleThresholdMs] (50 % de la durée
 * ou 4 min, avec un minimum de 30 s pour les morceaux courts).
 */
class ScrobbleRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Morceaux déjà scrobblés pendant la session de lecture en cours (réinitialisé au changement de piste). */
    private var sessionScrobbledIds: MutableSet<Long> = mutableSetOf()

    fun onTrackChanged() {
        sessionScrobbledIds = mutableSetOf()
    }

    /**
     * Met à jour la progression et enregistre un scrobble si le seuil est atteint.
     * @return `true` si un nouveau scrobble vient d'être enregistré.
     */
    fun trackProgress(song: Song, positionMs: Long, durationMs: Long): Boolean {
        if (song.id in sessionScrobbledIds) return false
        if (!shouldScrobble(positionMs, durationMs)) return false

        sessionScrobbledIds.add(song.id)
        val entries = loadEntries().toMutableMap()
        val existing = entries[song.id]
        val now = System.currentTimeMillis()
        entries[song.id] = if (existing != null) {
            existing.copy(
                title = song.title.ifBlank { existing.title },
                artist = song.artist.ifBlank { existing.artist },
                playCount = existing.playCount + 1,
                lastScrobbledAt = now,
                totalListenMs = existing.totalListenMs + positionMs.coerceAtLeast(0L),
            )
        } else {
            ScrobbleEntry(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                playCount = 1,
                lastScrobbledAt = now,
                totalListenMs = positionMs.coerceAtLeast(0L),
            )
        }
        saveEntries(entries)
        return true
    }

    fun getStats(topLimit: Int = DEFAULT_TOP_LIMIT): LocalScrobbleStats {
        val entries = loadEntries().values
        val topTracks = entries
            .sortedWith(compareByDescending<ScrobbleEntry> { it.playCount }.thenByDescending { it.lastScrobbledAt })
            .take(topLimit)
            .map { TopTrackStat(it.songId, it.title, it.artist, it.playCount) }
        val topArtists = entries
            .groupBy { it.artist.ifBlank { "Artiste inconnu" } }
            .map { (artist, list) -> TopArtistStat(artist, list.sumOf { it.playCount }) }
            .sortedByDescending { it.playCount }
            .take(topLimit)
        return LocalScrobbleStats(
            totalScrobbles = entries.sumOf { it.playCount },
            uniqueTracks = entries.size,
            topTracks = topTracks,
            topArtists = topArtists,
        )
    }

    fun clearAll() {
        prefs.edit().remove(KEY_ENTRIES).apply()
        sessionScrobbledIds = mutableSetOf()
    }

    fun exportEntriesJson(): String {
        val array = JSONArray()
        loadEntries().values
            .sortedByDescending { it.lastScrobbledAt }
            .forEach { entry ->
                array.put(
                    JSONObject()
                        .put("songId", entry.songId)
                        .put("title", entry.title)
                        .put("artist", entry.artist)
                        .put("playCount", entry.playCount)
                        .put("lastScrobbledAt", entry.lastScrobbledAt)
                        .put("totalListenMs", entry.totalListenMs)
                )
            }
        return JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("entries", array)
            .toString(2)
    }

    private fun loadEntries(): Map<Long, ScrobbleEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val obj = json.optJSONObject(key) ?: return@forEach
                    val songId = key.toLongOrNull() ?: obj.optLong("songId", 0L)
                    if (songId == 0L) return@forEach
                    put(
                        songId,
                        ScrobbleEntry(
                            songId = songId,
                            title = obj.optString("title", ""),
                            artist = obj.optString("artist", ""),
                            playCount = obj.optInt("playCount", 0),
                            lastScrobbledAt = obj.optLong("lastScrobbledAt", 0L),
                            totalListenMs = obj.optLong("totalListenMs", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveEntries(entries: Map<Long, ScrobbleEntry>) {
        val json = JSONObject()
        entries.forEach { (id, entry) ->
            json.put(
                id.toString(),
                JSONObject()
                    .put("songId", entry.songId)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("playCount", entry.playCount)
                    .put("lastScrobbledAt", entry.lastScrobbledAt)
                    .put("totalListenMs", entry.totalListenMs)
            )
        }
        prefs.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "soundgroove_scrobble"
        private const val KEY_ENTRIES = "scrobble_entries_json"
        private const val DEFAULT_TOP_LIMIT = 10

        /** Seuil Last.fm-like : max(30 s, min(50 % durée, 4 min)). */
        fun scrobbleThresholdMs(durationMs: Long): Long {
            val safeDuration = durationMs.coerceAtLeast(0L)
            if (safeDuration <= 0L) return MIN_SCROBBLE_MS
            val half = safeDuration / 2
            return maxOf(MIN_SCROBBLE_MS, minOf(half, MAX_SCROBBLE_MS))
        }

        fun shouldScrobble(positionMs: Long, durationMs: Long): Boolean =
            positionMs >= scrobbleThresholdMs(durationMs)

        private const val MIN_SCROBBLE_MS = 30_000L
        private const val MAX_SCROBBLE_MS = 240_000L
    }
}
