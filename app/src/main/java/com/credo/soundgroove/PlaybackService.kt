package com.credo.soundgroove

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.credo.soundgroove.notifications.NotificationChannels
import com.credo.soundgroove.ui.theme.AppTheme
import com.credo.soundgroove.util.CrossfadeController
import com.credo.soundgroove.util.EqualizerManager
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.widget.MusicAppWidgetProvider
import com.credo.soundgroove.widget.WidgetPlaybackState
import com.credo.soundgroove.widget.WidgetSkin
import com.credo.soundgroove.widget.WidgetState

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private var crossfadeController: CrossfadeController? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationChannels.ensureAll(this)

        val extractorsFactory = DefaultExtractorsFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()

        applyPlaybackPreferences(player)
        attachAudioEffects(player)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(PlaybackSessionCallback(player))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NotificationChannels.MEDIA_PLAYBACK)
                .setNotificationId(MEDIA_NOTIFICATION_ID)
                .build()
        )

        crossfadeController = CrossfadeController(this) { mediaSession?.player }.also {
            it.attach(player)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidgetFromPlayer(player)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateWidgetFromPlayer(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidgetFromPlayer(player)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateWidgetFromPlayer(player)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateWidgetFromPlayer(player)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    EqualizerManager.attach(this@PlaybackService, audioSessionId)
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
        updateWidgetFromPlayer(player)
    }

    private fun attachAudioEffects(player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            EqualizerManager.attach(this, sessionId)
        }
    }

    fun refreshPlaybackSettings() {
        crossfadeController?.refreshSettings()
        EqualizerManager.applyFromPreferences(this)
    }

    private fun applyPlaybackPreferences(player: ExoPlayer) {
        val prefs = PlaybackPreferences.prefs(this)
        val speed = prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_SPEED, 1.0f)
        val pitch = prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_PITCH, 1.0f)
        player.playbackParameters = PlaybackParameters(speed, pitch)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        instance = null
        crossfadeController?.detach()
        crossfadeController = null
        EqualizerManager.release()
        playerListener?.let { mediaSession?.player?.removeListener(it) }
        playerListener = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun resolveWidgetSkin(): WidgetSkin {
        val themeName = getSharedPreferences("soundgroove_prefs", MODE_PRIVATE)
            .getString("selected_theme", AppTheme.NOIR_ABSOLU.name)
        val theme = runCatching { AppTheme.valueOf(themeName ?: AppTheme.NOIR_ABSOLU.name) }
            .getOrDefault(AppTheme.NOIR_ABSOLU)
        return when (theme) {
            AppTheme.NOIR_ABSOLU -> WidgetSkin.CYAN
            AppTheme.GRAPHITE, AppTheme.ARGENT_CLAIR -> WidgetSkin.DARK
        }
    }

    private fun updateWidgetFromPlayer(player: Player) {
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        WidgetState.save(
            this,
            WidgetPlaybackState(
                title = title,
                artist = artist,
                albumArtUri = metadata.artworkUri,
                isPlaying = player.isPlaying,
                skin = resolveWidgetSkin()
            )
        )
        MusicAppWidgetProvider.updateAllWidgets(this)
    }

    private class PlaybackSessionCallback(
        private val player: Player
    ) : MediaSession.Callback {

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    seekPreviousTrack()
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        private fun seekPreviousTrack() {
            if (player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekTo(0)
            }
        }
    }

    companion object {
        const val MEDIA_NOTIFICATION_ID = 1001
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

        @Volatile
        var instance: PlaybackService? = null
    }
}
