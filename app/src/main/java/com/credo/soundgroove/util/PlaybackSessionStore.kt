package com.credo.soundgroove.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistance légère de la dernière session de lecture (piste + file + position)
 * pour restaurer le mini-player au cold start sans relancer une lecture.
 */
object PlaybackSessionStore {
    private const val PREFS = "soundgroove_prefs"
    private const val KEY_SONG_ID = "session_song_id"
    private const val KEY_POSITION_MS = "session_position_ms"
    private const val KEY_QUEUE_IDS = "session_queue_ids"

    /** Évite des prefs énormes / restore lents après kill. */
    const val MAX_QUEUE_IDS = 256
    const val MAX_POSITION_MS = 24L * 60L * 60L * 1000L // 24 h

    data class Snapshot(
        val songId: Long,
        val positionMs: Long,
        val queueIds: List<Long>,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(
        context: Context,
        songId: Long,
        positionMs: Long,
        queueIds: List<Long>,
    ) {
        if (songId < 0L) return
        val normalized = normalizeQueueIds(queueIds, songId)
        val ids = normalized.joinToString(",")
        prefs(context).edit()
            .putLong(KEY_SONG_ID, songId)
            .putLong(KEY_POSITION_MS, positionMs.coerceIn(0L, MAX_POSITION_MS))
            .putString(KEY_QUEUE_IDS, ids)
            .apply()
    }

    fun read(context: Context): Snapshot? {
        val p = prefs(context)
        val songId = p.getLong(KEY_SONG_ID, -1L)
        if (songId < 0L) return null
        val queueIds = parseQueueIds(p.getString(KEY_QUEUE_IDS, null), songId)
        return Snapshot(
            songId = songId,
            positionMs = p.getLong(KEY_POSITION_MS, 0L).coerceIn(0L, MAX_POSITION_MS),
            queueIds = queueIds,
        )
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_SONG_ID)
            .remove(KEY_POSITION_MS)
            .remove(KEY_QUEUE_IDS)
            .apply()
    }

    /** Parsing pur — testable sans Android. */
    fun parseQueueIds(raw: String?, fallbackSongId: Long): List<Long> {
        val parsed = raw
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it >= 0L }
            .orEmpty()
        return normalizeQueueIds(parsed, fallbackSongId)
    }

    fun normalizeQueueIds(queueIds: List<Long>, songId: Long): List<Long> {
        val base = queueIds.filter { it >= 0L }.ifEmpty { listOf(songId) }
        val withCurrent = if (songId in base) base else listOf(songId) + base
        return withCurrent.distinct().take(MAX_QUEUE_IDS)
    }
}
