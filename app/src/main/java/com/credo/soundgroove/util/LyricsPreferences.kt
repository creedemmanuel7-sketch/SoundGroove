package com.credo.soundgroove.util

import android.content.Context
import com.credo.soundgroove.lyrics.LyricsViewModel

object LyricsPreferences {
    const val KEY_LYRICS_SYNC_OFFSET_MS = "lyrics_sync_offset_ms"

    const val MIN_OFFSET_MS = -2_000L
    const val MAX_OFFSET_MS = 2_000L
    const val OFFSET_STEP_MS = 50L

    fun syncOffsetMs(context: Context): Long =
        prefs(context)
            .getLong(KEY_LYRICS_SYNC_OFFSET_MS, LyricsViewModel.DEFAULT_SYNC_OFFSET_MS)
            .coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)

    fun setSyncOffsetMs(context: Context, offsetMs: Long) {
        prefs(context)
            .edit()
            .putLong(KEY_LYRICS_SYNC_OFFSET_MS, offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS))
            .apply()
    }

    fun formatSyncOffsetLabel(offsetMs: Long): String = when {
        offsetMs == 0L -> "0 ms (par défaut LRC)"
        offsetMs < 0L -> "${offsetMs} ms (plus tôt)"
        else -> "+${offsetMs} ms (plus tard)"
    }

    private fun prefs(context: Context) = PlaybackPreferences.prefs(context)
}
