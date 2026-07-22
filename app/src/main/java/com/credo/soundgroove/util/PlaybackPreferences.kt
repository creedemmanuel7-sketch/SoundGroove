package com.credo.soundgroove.util

import android.content.Context
import android.content.SharedPreferences

object PlaybackPreferences {
    const val PREFS_NAME = "soundgroove_prefs"

    const val KEY_GAPLESS_ENABLED = "gapless_enabled"
    const val KEY_CROSSFADE_MS = "crossfade_duration_ms"
    const val KEY_PLAYBACK_SPEED = "playback_speed"
    const val KEY_PLAYBACK_PITCH = "playback_pitch"
    const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
    const val KEY_EQUALIZER_PRESET = "equalizer_preset"
    const val KEY_EQUALIZER_BAND_LEVELS = "equalizer_band_levels"
    const val KEY_TRACK_EQ_PRESETS = "track_eq_presets_json"
    const val KEY_VINYL_MODE_ENABLED = "vinyl_mode_enabled"

    val CROSSFADE_OPTIONS_MS = listOf(0, 1_500, 3_000, 5_000, 8_000, 12_000)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isGaplessEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GAPLESS_ENABLED, true)

    fun isVinylModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VINYL_MODE_ENABLED, false)

    fun crossfadeDurationMs(context: Context): Int =
        prefs(context).getInt(KEY_CROSSFADE_MS, 0)

    fun playbackSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_PLAYBACK_SPEED, 1.0f)

    fun playbackPitch(context: Context): Float =
        prefs(context).getFloat(KEY_PLAYBACK_PITCH, 1.0f)

    fun crossfadeLabel(ms: Int): String = when (ms) {
        0 -> "Désactivé"
        1_500 -> "1,5 seconde"
        3_000 -> "3 secondes"
        5_000 -> "5 secondes"
        8_000 -> "8 secondes"
        12_000 -> "12 secondes"
        else -> "${ms / 1000} s"
    }

    fun isEqualizerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EQUALIZER_ENABLED, true)

    fun setEqualizerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled).apply()
    }

    fun equalizerPreset(context: Context): EqualizerPreset =
        EqualizerPreset.fromStored(prefs(context).getString(KEY_EQUALIZER_PRESET, EqualizerPreset.NORMAL.name))

    fun setEqualizerPreset(context: Context, preset: EqualizerPreset) {
        prefs(context).edit().putString(KEY_EQUALIZER_PRESET, preset.name).apply()
    }

    fun equalizerBandLevels(context: Context): List<Short> {
        val raw = prefs(context).getString(KEY_EQUALIZER_BAND_LEVELS, null) ?: return emptyList()
        return raw.split(",").mapNotNull { it.trim().toShortOrNull() }
    }

    fun setEqualizerBandLevels(context: Context, levels: List<Short>) {
        prefs(context).edit()
            .putString(KEY_EQUALIZER_BAND_LEVELS, levels.joinToString(","))
            .apply()
    }

    fun setEqualizerBandLevel(context: Context, bandIndex: Int, levelMillibels: Short) {
        val current = equalizerBandLevels(context).toMutableList()
        while (current.size <= bandIndex) {
            current.add(0)
        }
        current[bandIndex] = levelMillibels
        setEqualizerBandLevels(context, current)
        setEqualizerPreset(context, EqualizerPreset.CUSTOM)
    }

    fun getTrackEqualizerPreset(context: Context, songId: Long): EqualizerPreset? {
        if (songId == 0L) return null
        return loadTrackEqMap(context)[songId]?.let { EqualizerPreset.fromStored(it) }
    }

    fun setTrackEqualizerPreset(context: Context, songId: Long, preset: EqualizerPreset) {
        if (songId == 0L) return
        val map = loadTrackEqMap(context).toMutableMap()
        map[songId] = preset.name
        saveTrackEqMap(context, map)
    }

    fun clearTrackEqualizerPreset(context: Context, songId: Long) {
        if (songId == 0L) return
        val map = loadTrackEqMap(context).toMutableMap()
        map.remove(songId)
        saveTrackEqMap(context, map)
    }

    fun getAllTrackEqualizerPresets(context: Context): Map<Long, EqualizerPreset> =
        loadTrackEqMap(context).mapValues { EqualizerPreset.fromStored(it.value) }

    fun replaceTrackEqualizerPresets(context: Context, presets: Map<Long, EqualizerPreset>) {
        saveTrackEqMap(context, presets.mapValues { it.value.name })
    }

    private fun loadTrackEqMap(context: Context): Map<Long, String> {
        val raw = prefs(context).getString(KEY_TRACK_EQ_PRESETS, null) ?: return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    key.toLongOrNull()?.let { id ->
                        put(id, json.optString(key, EqualizerPreset.NORMAL.name))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveTrackEqMap(context: Context, map: Map<Long, String>) {
        val json = org.json.JSONObject()
        map.forEach { (id, preset) -> json.put(id.toString(), preset) }
        prefs(context).edit().putString(KEY_TRACK_EQ_PRESETS, json.toString()).apply()
    }

    fun playbackModeLabel(gapless: Boolean, crossfadeMs: Int): String = when {
        crossfadeMs > 0 -> "Fondu ${crossfadeLabel(crossfadeMs).lowercase()}"
        gapless -> "Enchaînement sans coupure"
        else -> "Pause entre pistes"
    }
}
