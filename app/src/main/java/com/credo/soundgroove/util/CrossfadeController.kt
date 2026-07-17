package com.credo.soundgroove.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import kotlin.math.max

/**
 * Gère le fondu enchaîné (crossfade) et la micro-pause lorsque le gapless est désactivé.
 * Utilise [Player.volume] — pas de double ExoPlayer.
 */
class CrossfadeController(
    private val context: Context,
    private val playerProvider: () -> Player?
) {
    private val handler = Handler(Looper.getMainLooper())
    private var fadeInRunnable: Runnable? = null
    private var volumeTickRunnable: Runnable? = null
    private var attachedPlayer: Player? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            ) {
                resetVolume()
                return
            }
            onTrackChanged()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) stopVolumeTick()
        }
    }

    fun attach(player: Player) {
        if (attachedPlayer === player) return
        detach()
        attachedPlayer = player
        player.addListener(listener)
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
        if (!PlaybackPreferences.isGaplessEnabled(context) &&
            PlaybackPreferences.crossfadeDurationMs(context) == 0
        ) {
            // Pas de crossfade : micro-pause simulée au changement de piste
        }
        if (player.volume < 1f && PlaybackPreferences.crossfadeDurationMs(context) == 0) {
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
        val player = playerProvider() ?: return
        val crossfadeMs = PlaybackPreferences.crossfadeDurationMs(context)
        if (crossfadeMs <= 0 || !player.isPlaying) return

        val duration = player.duration
        if (duration <= 0) return

        val remaining = duration - player.currentPosition
        if (remaining in 1..crossfadeMs) {
            player.volume = (remaining.toFloat() / crossfadeMs).coerceIn(0f, 1f)
        }
    }

    private fun fadeIn(player: Player, durationMs: Long) {
        val steps = max(1, (durationMs / VOLUME_TICK_MS).toInt())
        var step = 0
        player.volume = 0f
        val runnable = object : Runnable {
            override fun run() {
                step++
                player.volume = (step.toFloat() / steps).coerceIn(0f, 1f)
                if (step < steps) {
                    handler.postDelayed(this, VOLUME_TICK_MS)
                } else {
                    player.volume = 1f
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
    }

    companion object {
        private const val VOLUME_TICK_MS = 50L
        private const val GAP_PAUSE_MS = 400L
    }
}
