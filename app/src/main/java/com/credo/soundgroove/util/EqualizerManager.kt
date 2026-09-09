package com.credo.soundgroove.util

import android.content.Context

/**
 * Pont entre [PlaybackService] et l'UI pour l'égaliseur système.
 */
object EqualizerManager {
    private var controller: EqualizerController? = null

    fun attach(context: Context, audioSessionId: Int) {
        controller?.release()
        controller = EqualizerController(context.applicationContext, audioSessionId)
    }

    fun release() {
        controller?.release()
        controller = null
    }

    fun applyFromPreferences(context: Context) {
        controller?.applyFromPreferences()
            ?: run {
                // Service pas encore prêt : les prefs seront relues à l'attachement.
                PlaybackPreferences.prefs(context)
            }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        PlaybackPreferences.setEqualizerEnabled(context, enabled)
        controller?.setEnabled(enabled)
    }

    /**
     * Applique un preset sur le hardware.
     * @param persistGlobal si true, écrit le preset global dans les prefs.
     *   Les overrides per-track doivent passer [persistGlobal]=false pour ne pas
     *   écraser le preset global au skip de piste.
     */
    fun applyPreset(context: Context, preset: EqualizerPreset, persistGlobal: Boolean = true) {
        if (persistGlobal) {
            PlaybackPreferences.setEqualizerPreset(context, preset)
        }
        if (preset != EqualizerPreset.CUSTOM) {
            controller?.applyPreset(preset)
        } else {
            controller?.applyFromPreferences()
        }
    }

    fun setBandLevel(context: Context, bandIndex: Int, levelMillibels: Short) {
        PlaybackPreferences.setEqualizerBandLevel(context, bandIndex, levelMillibels)
        controller?.setBandLevel(bandIndex, levelMillibels)
    }

    fun applyForTrack(context: Context, songId: Long) {
        val preset = PlaybackPreferences.getTrackEqualizerPreset(context, songId)
            ?: PlaybackPreferences.equalizerPreset(context)
        applyPreset(context, preset, persistGlobal = false)
    }

    fun effectivePresetForTrack(context: Context, songId: Long): EqualizerPreset =
        PlaybackPreferences.getTrackEqualizerPreset(context, songId)
            ?: PlaybackPreferences.equalizerPreset(context)

    fun getBandInfos(): List<EqualizerBandInfo> = controller?.getBandInfos().orEmpty()

    fun readCurrentLevels(): ShortArray = controller?.readCurrentLevels() ?: ShortArray(0)

    fun isAvailable(): Boolean = controller?.isAvailable() == true
}
