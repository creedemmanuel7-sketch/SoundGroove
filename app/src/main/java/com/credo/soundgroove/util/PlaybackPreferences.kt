package com.credo.soundgroove.util

import android.content.Context
import android.content.SharedPreferences

object PlaybackPreferences {
    const val PREFS_NAME = "soundgroove_prefs"

    const val KEY_GAPLESS_ENABLED = "gapless_enabled"
    const val KEY_CROSSFADE_MS = "crossfade_duration_ms"
    const val KEY_PLAYBACK_SPEED = "playback_speed"
    const val KEY_PLAYBACK_PITCH = "playback_pitch"

    val CROSSFADE_OPTIONS_MS = listOf(0, 2_000, 5_000, 8_000)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGaplessEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GAPLESS_ENABLED, true)

    fun crossfadeDurationMs(context: Context): Int =
        prefs(context).getInt(KEY_CROSSFADE_MS, 0)

    fun playbackSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_PLAYBACK_SPEED, 1.0f)

    fun playbackPitch(context: Context): Float =
        prefs(context).getFloat(KEY_PLAYBACK_PITCH, 1.0f)

    fun crossfadeLabel(ms: Int): String = when (ms) {
        0 -> "Désactivé"
        2_000 -> "2 secondes"
        5_000 -> "5 secondes"
        8_000 -> "8 secondes"
        else -> "${ms / 1000} s"
    }
}
