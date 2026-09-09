package com.credo.soundgroove.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import kotlin.math.max
import kotlin.math.pow

/**
 * Gère le fondu enchaîné (crossfade) et la micro-pause lorsque le gapless est désactivé.
 * Utilise [Player.volume] avec courbe ease-in-out — pas de double ExoPlayer.
 */
class CrossfadeController(
    private val context: Context,
    private val playerProvider: () -> Player?
) {
    private val handler = Handler(Looper.getMainLooper())
    private var fadeInRunnable: Runnable? = null
    private var volumeTickRunnable: Runnable? = null
    private var attachedPlayer: Player? = null
    private var fadeOutStartVolume = 1f

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // Fade / mute uniquement sur enchaînement auto.
            // SEEK / PLAYLIST_CHANGED / REPEAT = action user ou setMediaItem → jamais volume 0
            // (sinon READY sans son pendant le fade = latence audio « réelle »).
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                onTrackChanged()
            } else {
                resetVolume()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) stopVolumeTick()
            else startVolumeTick()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // Ne jamais laisser le volume à 0 après un échec mid-fade.
            cancelFadeIn()
            stopVolumeTick()
            resetVolume()
        }
    }

    fun attach(player: Player) {
        if (attachedPlayer === player) return
        detach()
        attachedPlayer = player
        player.addListener(listener)
        resetVolume()
        startVolumeTick()
    }

    fun detach() {
        stopVolumeTick()
        cancelFadeIn()
        attachedPlayer?.removeListener(listener)
        attachedPlayer = null
    }

    fun refreshSettings() {
        val player = playerProvider() ?: return
        val crossfadeMs = PlaybackPreferences.crossfadeDurationMs(context)
        if (crossfadeMs == 0 && player.volume < 1f) {
            resetVolume()
        }
    }

    private fun onTrackChanged() {
        val crossfadeMs = PlaybackPreferences.crossfadeDurationMs(context)
        val gapless = PlaybackPreferences.isGaplessEnabled(context)
        val player = playerProvider() ?: return

        cancelFadeIn()
        when {
            crossfadeMs > 0 -> fadeIn(player, crossfadeMs.toLong())
            !gapless -> {
                player.volume = 0f
                fadeIn(player, GAP_PAUSE_MS)
            }
            else -> resetVolume()
        }
    }

    /** Garantit un volume audible après un play explicite (évite un mute résiduel). */
    fun ensureAudible() {
        cancelFadeIn()
        resetVolume()
    }

    private fun startVolumeTick() {
        stopVolumeTick()
        val tick = object : Runnable {
            override fun run() {
                updateFadeOut()
                handler.postDelayed(this, VOLUME_TICK_MS)
            }
        }
        volumeTickRunnable = tick
        handler.post(tick)
    }

    private fun stopVolumeTick() {
        volumeTickRunnable?.let { handler.removeCallbacks(it) }
        volumeTickRunnable = null
    }

    private fun updateFadeOut() {
        try {
            val player = playerProvider() ?: return
            if (player.playbackState == Player.STATE_IDLE ||
                player.playerError != null
            ) {
                resetVolume()
                return
            }
            val crossfadeMs = PlaybackPreferences.crossfadeDurationMs(context)
            if (crossfadeMs <= 0 || !player.isPlaying) return

            val duration = player.duration
            if (duration <= 0) return

            val remaining = duration - player.currentPosition
            if (remaining in 1..crossfadeMs) {
                val progress = remaining.toFloat() / crossfadeMs
                val eased = easeInOut(progress.coerceIn(0f, 1f))
                player.volume = (eased * fadeOutStartVolume).coerceIn(0f, 1f)
            } else if (remaining > crossfadeMs && player.volume < fadeOutStartVolume) {
                player.volume = fadeOutStartVolume
            }
        } catch (_: Exception) {
            resetVolume()
        }
    }

    private fun fadeIn(player: Player, durationMs: Long) {
        val steps = max(1, (durationMs / VOLUME_TICK_MS).toInt())
        var step = 0
        player.volume = 0f
        fadeOutStartVolume = 1f
        val runnable = object : Runnable {
            override fun run() {
                step++
                val linear = (step.toFloat() / steps).coerceIn(0f, 1f)
                player.volume = easeInOut(linear)
                if (step < steps) {
                    handler.postDelayed(this, VOLUME_TICK_MS)
                } else {
                    player.volume = 1f
                    fadeOutStartVolume = 1f
                    fadeInRunnable = null
                }
            }
        }
        fadeInRunnable = runnable
        handler.post(runnable)
    }

    private fun cancelFadeIn() {
        fadeInRunnable?.let { handler.removeCallbacks(it) }
        fadeInRunnable = null
    }

    private fun resetVolume() {
        playerProvider()?.volume = 1f
        fadeOutStartVolume = 1f
    }

    /** Courbe douce pour des transitions moins abruptes. */
    private fun easeInOut(t: Float): Float {
        return if (t < 0.5f) {
            2f * t.pow(2)
        } else {
            1f - (-2f * t + 2f).pow(2) / 2f
        }
    }

    companion object {
        private const val VOLUME_TICK_MS = 25L
        private const val GAP_PAUSE_MS = 550L
    }
}
