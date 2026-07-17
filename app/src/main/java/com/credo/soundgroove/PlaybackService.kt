package com.credo.soundgroove

import android.app.PendingIntent
import android.content.Intent
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
import com.credo.soundgroove.notifications.NotificationChannels
import com.credo.soundgroove.util.CrossfadeController
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.widget.MusicAppWidgetProvider
import com.credo.soundgroove.widget.WidgetPlaybackState
import com.credo.soundgroove.widget.WidgetState

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null
    private var crossfadeController: CrossfadeController? = null

    override fun onCreate() {
        super.onCreate()
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
        }
        playerListener = listener
        player.addListener(listener)
        updateWidgetFromPlayer(player)
    }

    private fun applyPlaybackPreferences(player: ExoPlayer) {
        val prefs = PlaybackPreferences.prefs(this)
        val speed = prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_SPEED, 1.0f)
        val pitch = prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_PITCH, 1.0f)
        player.playbackParameters = PlaybackParameters(speed, pitch)
        // Gapless : ExoPlayer gère la lecture enchaînée ; le toggle désactive la micro-pause via CrossfadeController
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
        crossfadeController?.detach()
        crossfadeController = null
        playerListener?.let { mediaSession?.player?.removeListener(it) }
        playerListener = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
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
                isPlaying = player.isPlaying
            )
        )
        MusicAppWidgetProvider.updateAllWidgets(this)
    }

    companion object {
        const val MEDIA_NOTIFICATION_ID = 1001
    }
}
