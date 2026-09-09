package com.credo.soundgroove.ui.player

import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.util.EqualizerBandInfo
import com.credo.soundgroove.util.EqualizerPreset

/**
 * Snapshot UI lecture — réduit le prop drilling AppNavigation → Player / Mini
 * sans découper le God ViewModel. Offline-first : état local Media3 / prefs uniquement.
 */
data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isControllerConnecting: Boolean = false,
    val playbackPosition: Long = 0L,
    val playbackDuration: Long = 0L,
    val playbackQueue: List<Song> = emptyList(),
    val playbackQueueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val gaplessEnabled: Boolean = true,
    val crossfadeDurationMs: Int = 0,
    val sleepTimerRemainingSeconds: Int? = null,
    val vinylModeEnabled: Boolean = false,
    val equalizerEnabled: Boolean = true,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.NORMAL,
    val equalizerBands: List<EqualizerBandInfo> = emptyList(),
    val currentTrackEqPinned: Boolean = false,
    val lyricsSyncOffsetMs: Long = 0L,
    val albumCoverAccentEnabled: Boolean = false,
    val playbackError: String? = null,
)
