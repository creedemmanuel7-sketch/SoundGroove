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
        val ids = queueIds.ifEmpty { listOf(songId) }.joinToString(",")
        prefs(context).edit()
            .putLong(KEY_SONG_ID, songId)
            .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
            .putString(KEY_QUEUE_IDS, ids)
            .apply()
    }

    fun read(context: Context): Snapshot? {
        val p = prefs(context)
        val songId = p.getLong(KEY_SONG_ID, -1L)
        if (songId < 0L) return null
        val queueIds = p.getString(KEY_QUEUE_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
            .ifEmpty { listOf(songId) }
        return Snapshot(
            songId = songId,
            positionMs = p.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
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
}
