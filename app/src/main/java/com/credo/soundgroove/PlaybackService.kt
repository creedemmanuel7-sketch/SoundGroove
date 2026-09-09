package com.credo.soundgroove

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.credo.soundgroove.auto.AutoLibraryCatalog
import com.credo.soundgroove.auto.AutoLibrarySessionCallback
import com.credo.soundgroove.notifications.NotificationChannels
import com.credo.soundgroove.ui.theme.AppTheme
import com.credo.soundgroove.util.CrossfadeController
import com.credo.soundgroove.util.EqualizerManager
import com.credo.soundgroove.util.PlaybackPreferences
import com.credo.soundgroove.widget.MusicAppWidgetProvider
import com.credo.soundgroove.widget.PulseRecentWidgetProvider
import com.credo.soundgroove.widget.WidgetOfflineStore
import com.credo.soundgroove.widget.WidgetPlaybackState
import com.credo.soundgroove.widget.WidgetQueueItem
import com.credo.soundgroove.widget.WidgetSkin
import com.credo.soundgroove.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var playerListener: Player.Listener? = null
    private var crossfadeController: CrossfadeController? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var libraryCatalog: AutoLibraryCatalog
    private val widgetProgressHandler = Handler(Looper.getMainLooper())
    private val widgetProgressTick = object : Runnable {
        override fun run() {
            mediaSession?.player?.let { updateWidgetFromPlayer(it, refreshPulse = false) }
            widgetProgressHandler.postDelayed(this, WIDGET_PROGRESS_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val createdAt = SystemClock.elapsedRealtime()
        instance = this
        NotificationChannels.ensureAll(this)
        // Après pause : garder le FGS un peu plus longtemps que le défaut (~10 min)
        // pour limiter les morts précoces sous Doze / Standby OEM.
        setForegroundServiceTimeoutMs(FOREGROUND_SERVICE_TIMEOUT_MS)
        libraryCatalog = AutoLibraryCatalog(this)
        serviceScope.launch(Dispatchers.IO) {
            runCatching { libraryCatalog.refresh() }
        }

        // DefaultExtractorsFactory couvre MP3 / AAC / FLAC / OGG / Opus / WAV…
        // CBR seeking : seek plus fiable sur MP3/AAC sans index VBR.
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)

        // First-play très agressif (locaux) + headroom pour enchaîner la piste
        // suivante déjà en file Media3 (min/max plus généreux).
        // targetBufferBytes plafonne la RAM sur sessions longues (pas de SimpleCache disque).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ MIN_BUFFER_MS,
                /* maxBufferMs = */ MAX_BUFFER_MS,
                /* bufferForPlaybackMs = */ BUFFER_FOR_PLAYBACK_MS,
                /* bufferForPlaybackAfterRebufferMs = */ BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setTargetBufferBytes(TARGET_BUFFER_BYTES)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        applyPlaybackPreferences(player)
        // EQ après première session audio réelle (onAudioSessionIdChanged) —
        // l'attacher trop tôt sur session 0 peut retarder le premier rendu.
        attachAudioEffects(player)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            AutoLibrarySessionCallback(player, libraryCatalog, serviceScope),
        )
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
                if (isPlaying) {
                    Log.i(TAG_AUDIO, "isPlaying=true state=${player.playbackState} pos=${player.currentPosition}")
                    startWidgetProgressTicks()
                } else {
                    stopWidgetProgressTicks()
                }
                updateWidgetFromPlayer(player)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateWidgetFromPlayer(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.i(
                    TAG_AUDIO,
                    "state=${playbackStateLabel(playbackState)} playWhenReady=${player.playWhenReady} " +
                        "isPlaying=${player.isPlaying} pos=${player.currentPosition} " +
                        "dur=${player.duration} buffered=${player.bufferedPercentage}%"
                )
                updateWidgetFromPlayer(player)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateWidgetFromPlayer(player, refreshPulse = true)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateWidgetFromPlayer(player)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    EqualizerManager.attach(this@PlaybackService, audioSessionId)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG_AUDIO, "player error code=${error.errorCodeName}", error)
                // Remet le volume audible si un crossfade avait muté mid-erreur.
                ensureAudibleVolume()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                PlaybackPreferences.setShuffleEnabled(this@PlaybackService, shuffleModeEnabled)
                updateWidgetFromPlayer(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                PlaybackPreferences.setRepeatMode(this@PlaybackService, repeatMode)
                updateWidgetFromPlayer(player)
            }
        }
        playerListener = listener
        player.addListener(listener)
        updateWidgetFromPlayer(player, refreshPulse = true)
        Log.i(TAG_AUDIO, "PlaybackService ready in ${SystemClock.elapsedRealtime() - createdAt}ms")
    }

    private fun attachAudioEffects(player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
            EqualizerManager.attach(this, sessionId)
        }
    }

    fun refreshPlaybackSettings() {
        crossfadeController?.refreshSettings()
        EqualizerManager.applyFromPreferences(this)
    }

    fun ensureAudibleVolume() {
        crossfadeController?.ensureAudible()
        mediaSession?.player?.let { player ->
            if (player.volume < 0.99f) player.volume = 1f
        }
    }

    private fun applyPlaybackPreferences(player: ExoPlayer) {
        val prefs = PlaybackPreferences.prefs(this)
        val speed = prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_SPEED, 1.0f)
        val pitch = prefs.getFloat(PlaybackPreferences.KEY_PLAYBACK_PITCH, 1.0f)
        player.playbackParameters = PlaybackParameters(speed, pitch)
        player.shuffleModeEnabled = prefs.getBoolean(PlaybackPreferences.KEY_SHUFFLE_ENABLED, false)
        player.repeatMode = prefs.getInt(
            PlaybackPreferences.KEY_REPEAT_MODE,
            Player.REPEAT_MODE_OFF,
        ).coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
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
        stopWidgetProgressTicks()
        serviceJob.cancel()
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

    private fun startWidgetProgressTicks() {
        widgetProgressHandler.removeCallbacks(widgetProgressTick)
        widgetProgressHandler.postDelayed(widgetProgressTick, WIDGET_PROGRESS_INTERVAL_MS)
    }

    private fun stopWidgetProgressTicks() {
        widgetProgressHandler.removeCallbacks(widgetProgressTick)
    }

    private fun updateWidgetFromPlayer(player: Player, refreshPulse: Boolean = false) {
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        val mediaUri = player.currentMediaItem?.localConfiguration?.uri
            ?: player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val mediaUriStr = mediaUri?.toString().orEmpty()
        val songId = WidgetOfflineStore.resolveSongId(mediaUri)
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        val upNext = buildUpNext(player)
        val previousFav = WidgetState.read(this).isFavorite

        WidgetState.save(
            this,
            WidgetPlaybackState(
                title = title,
                artist = artist,
                albumArtUri = metadata.artworkUri,
                mediaUri = mediaUriStr,
                songId = songId,
                isPlaying = player.isPlaying,
                isFavorite = previousFav,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                positionMs = position,
                durationMs = duration,
                upNext = upNext,
                skin = resolveWidgetSkin(),
            )
        )
        MusicAppWidgetProvider.updateAllWidgets(this)
        if (refreshPulse) {
            PulseRecentWidgetProvider.updateAllWidgets(this)
        }

        if (mediaUriStr.isNotBlank() || songId >= 0L) {
            serviceScope.launch {
                val fav = runCatching {
                    WidgetOfflineStore.isFavorite(this@PlaybackService, songId, mediaUriStr)
                }.getOrDefault(false)
                if (fav != previousFav) {
                    WidgetState.updateFavorite(this@PlaybackService, fav)
                    withContext(Dispatchers.Main.immediate) {
                        MusicAppWidgetProvider.updateAllWidgets(this@PlaybackService)
                    }
                }
            }
        }
    }

    private fun buildUpNext(player: Player): List<WidgetQueueItem> {
        val count = player.mediaItemCount
        if (count <= 1) return emptyList()
        val current = player.currentMediaItemIndex
        if (current < 0) return emptyList()
        val result = ArrayList<WidgetQueueItem>(2)
        var steps = 0
        var index = current
        while (result.size < 2 && steps < count) {
            steps++
            index = (index + 1) % count
            if (index == current && player.repeatMode == Player.REPEAT_MODE_OFF) break
            val item = player.getMediaItemAt(index)
            val meta = item.mediaMetadata
            val nextTitle = meta.title?.toString().orEmpty()
            result += WidgetQueueItem(
                title = nextTitle.ifBlank { "Titre suivant" },
                artist = meta.artist?.toString().orEmpty(),
                albumArtUri = meta.artworkUri,
            )
            if (index == current) break
        }
        return result
    }

    companion object {
        const val MEDIA_NOTIFICATION_ID = 1001
        private const val TAG_AUDIO = "SG_AUDIO"
        private const val WIDGET_PROGRESS_INTERVAL_MS = 4_000L
        /** Seuil bas pour démarrer le rendu audio local rapidement (~cible < 500 ms). */
        private const val BUFFER_FOR_PLAYBACK_MS = 150
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 500
        /** Assez de headroom pour précharger la suite de la période courante / transition. */
        private const val MIN_BUFFER_MS = 20_000
        private const val MAX_BUFFER_MS = 45_000
        /** Plafond RAM buffers (audio local haute résolution). */
        private const val TARGET_BUFFER_BYTES = 24 * 1024 * 1024
        /** 15 min après pause/stop avant sortie FGS (Media3 default ≈ 10 min). */
        private const val FOREGROUND_SERVICE_TIMEOUT_MS = 15 * 60 * 1000L

        @Volatile
        var instance: PlaybackService? = null

        /**
         * Démarre [PlaybackService] avant un bind [androidx.media3.session.MediaController]
         * depuis un widget (PendingIntent → BroadcastReceiver).
         *
         * Le geste widget est une interaction utilisateur : [Context.startForegroundService] est
         * généralement autorisé ; on tombe en [Context.startService] si l'OEM refuse le FGS.
         */
        fun ensureStartedForWidget(context: Context): Boolean {
            val app = context.applicationContext
            val intent = Intent(app, PlaybackService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        app.startForegroundService(intent)
                    } catch (e: Exception) {
                        // Inclut ForegroundServiceStartNotAllowedException (API 31+) et IllegalStateException.
                        Log.w(TAG_AUDIO, "startForegroundService failed; falling back to startService", e)
                        app.startService(intent)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    app.startService(intent)
                }
                true
            } catch (t: Throwable) {
                Log.e(TAG_AUDIO, "ensureStartedForWidget failed", t)
                false
            }
        }

        private fun playbackStateLabel(state: Int): String = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
    }
}
