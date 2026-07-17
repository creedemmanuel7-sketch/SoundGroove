package com.credo.soundgroove.util

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import kotlin.math.roundToInt

data class EqualizerBandInfo(
    val index: Short,
    val centerFrequencyHz: Int,
    val levelMillibels: Short,
    val minLevelMillibels: Short,
    val maxLevelMillibels: Short
)

/**
 * Encapsule [android.media.audiofx.Equalizer] pour une session audio ExoPlayer.
 */
class EqualizerController(
    private val context: Context,
    audioSessionId: Int
) {
    private var equalizer: Equalizer? = null
    private var bandCount: Int = 0
    private var minLevel: Short = 0
    private var maxLevel: Short = 0

    init {
        attach(audioSessionId)
    }

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        try {
            val fx = Equalizer(0, audioSessionId)
            fx.enabled = PlaybackPreferences.isEqualizerEnabled(context)
            equalizer = fx
            bandCount = fx.numberOfBands.toInt()
            val range = fx.bandLevelRange
            minLevel = range[0]
            maxLevel = range[1]
            applyFromPreferences()
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer non disponible pour session $audioSessionId", e)
            equalizer = null
            bandCount = 0
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bandCount = 0
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    fun applyPreset(preset: EqualizerPreset) {
        val fx = equalizer ?: return
        if (preset == EqualizerPreset.CUSTOM) {
            applyCustomLevelsFromPrefs()
            return
        }
        val gains = preset.relativeGains(bandCount)
        for (i in 0 until bandCount) {
            fx.setBandLevel(i.toShort(), relativeGainToMillibels(gains[i]))
        }
    }

    fun setBandLevel(bandIndex: Int, levelMillibels: Short) {
        val fx = equalizer ?: return
        if (bandIndex !in 0 until bandCount) return
        fx.setBandLevel(bandIndex.toShort(), levelMillibels.coerceIn(minLevel, maxLevel))
    }

    fun applyFromPreferences() {
        val fx = equalizer ?: return
        fx.enabled = PlaybackPreferences.isEqualizerEnabled(context)
        val preset = PlaybackPreferences.equalizerPreset(context)
        if (preset == EqualizerPreset.CUSTOM) {
            applyCustomLevelsFromPrefs()
        } else {
            applyPreset(preset)
        }
    }

    private fun applyCustomLevelsFromPrefs() {
        val fx = equalizer ?: return
        val stored = PlaybackPreferences.equalizerBandLevels(context)
        for (i in 0 until bandCount) {
            val level = stored.getOrNull(i)?.coerceIn(minLevel, maxLevel)
                ?: ((minLevel + maxLevel) / 2).toShort()
            fx.setBandLevel(i.toShort(), level)
        }
    }

    fun getBandInfos(): List<EqualizerBandInfo> {
        val fx = equalizer ?: return emptyList()
        return (0 until bandCount).map { index ->
            EqualizerBandInfo(
                index = index.toShort(),
                centerFrequencyHz = fx.getCenterFreq(index.toShort()) / 1_000,
                levelMillibels = fx.getBandLevel(index.toShort()),
                minLevelMillibels = minLevel,
                maxLevelMillibels = maxLevel
            )
        }
    }

    fun readCurrentLevels(): ShortArray {
        val fx = equalizer ?: return ShortArray(0)
        return ShortArray(bandCount) { fx.getBandLevel(it.toShort()) }
    }

    fun relativeGainToMillibels(relative: Float): Short {
        val center = (minLevel + maxLevel) / 2f
        val halfSpan = (maxLevel - minLevel) / 2f
        return (center + relative.coerceIn(-1f, 1f) * halfSpan).roundToInt()
            .coerceIn(minLevel.toInt(), maxLevel.toInt())
            .toShort()
    }

    fun isAvailable(): Boolean = equalizer != null

    companion object {
        private const val TAG = "EqualizerController"
    }
}
