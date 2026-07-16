package com.credo.soundgroove

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.credo.soundgroove.notifications.NotificationChannels
import com.credo.soundgroove.widget.MusicAppWidgetProvider
import com.credo.soundgroove.widget.WidgetPlaybackState
import com.credo.soundgroove.widget.WidgetState

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureAll(this)

        val player = ExoPlayer.Builder(this).build()
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

        playerListener = object : Player.Listener {
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
        player.addListener(playerListener!!)
        updateWidgetFromPlayer(player)
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
