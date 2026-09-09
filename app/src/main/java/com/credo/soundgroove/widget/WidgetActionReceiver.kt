package com.credo.soundgroove.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.credo.soundgroove.util.PlaybackPreferences
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Actions widget (transport, modes, Pulse PLAY_URI, favori).
 *
 * Crash-proof : [goAsync] + [pendingResult.finish] toujours, service démarré avant
 * MediaController, exécuteur hors binder, try/catch partout.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) {
                runCatching { pendingResult.finish() }
            }
        }

        val appContext = context.applicationContext
        val action = intent?.action
        if (action == null || action !in SUPPORTED_ACTIONS) {
            finishOnce()
            return
        }

        if (action == ACTION_TOGGLE_FAVORITE) {
            toggleFavoriteAsync(appContext) { finishOnce() }
            return
        }

        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI)
        backgroundExecutor.execute {
            var controllerFuture: ListenableFuture<MediaController>? = null
            try {
                PlaybackService.ensureStartedForWidget(appContext)

                val sessionToken = SessionToken(
                    appContext,
                    ComponentName(appContext, PlaybackService::class.java),
                )
                controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
                val controller = controllerFuture.get(CONTROLLER_TIMEOUT_SEC, TimeUnit.SECONDS)

                mainHandler.post {
                    try {
                        dispatchPlayerAction(appContext, controller, action, mediaUri)
                    } catch (t: Throwable) {
                        Log.e(TAG, "dispatchPlayerAction failed action=$action", t)
                    } finally {
                        runCatching { MediaController.releaseFuture(controllerFuture) }
                        finishOnce()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Widget MediaController path failed action=$action", t)
                runCatching {
                    controllerFuture?.let { MediaController.releaseFuture(it) }
                }
                finishOnce()
            }
        }
    }

    private fun dispatchPlayerAction(
        context: Context,
        controller: MediaController,
        action: String,
        mediaUri: String?,
    ) {
        when (action) {
            ACTION_PLAY_PAUSE -> {
                if (controller.isPlaying) controller.pause() else controller.play()
            }
            ACTION_PREVIOUS -> seekPrevious(controller)
            ACTION_NEXT -> {
                if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
            }
            ACTION_SHUFFLE -> {
                controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                PlaybackPreferences.setShuffleEnabled(context, controller.shuffleModeEnabled)
            }
            ACTION_REPEAT -> {
                val next = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                controller.repeatMode = next
                PlaybackPreferences.setRepeatMode(context, next)
            }
            ACTION_PLAY_URI -> {
                val uriStr = mediaUri.orEmpty()
                if (uriStr.isNotBlank()) playUri(controller, uriStr)
            }
            else -> Unit
        }
    }

    private fun toggleFavoriteAsync(context: Context, finishOnce: () -> Unit) {
        scope.launch {
            try {
                val state = WidgetState.read(context)
                val result = runCatching {
                    WidgetOfflineStore.toggleFavoriteFromState(context, state)
                }.onFailure { Log.e(TAG, "toggleFavorite failed", it) }.getOrNull()
                if (result != null) {
                    WidgetState.updateFavorite(context, result)
                    launch(Dispatchers.Main.immediate) {
                        runCatching { MusicAppWidgetProvider.updateAllWidgets(context) }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "toggleFavoriteAsync crashed", t)
            } finally {
                finishOnce()
            }
        }
    }

    private fun playUri(controller: Player, uriStr: String) {
        val uri = Uri.parse(uriStr)
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uriStr)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    private fun seekPrevious(controller: Player) {
        if (controller.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
            controller.seekTo(0)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.credo.soundgroove.widget.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.credo.soundgroove.widget.PREVIOUS"
        const val ACTION_NEXT = "com.credo.soundgroove.widget.NEXT"
        const val ACTION_SHUFFLE = "com.credo.soundgroove.widget.SHUFFLE"
        const val ACTION_REPEAT = "com.credo.soundgroove.widget.REPEAT"
        const val ACTION_TOGGLE_FAVORITE = "com.credo.soundgroove.widget.TOGGLE_FAVORITE"
        const val ACTION_PLAY_URI = "com.credo.soundgroove.widget.PLAY_URI"
        const val EXTRA_MEDIA_URI = "extra_media_uri"

        private const val TAG = "SG_WIDGET"
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
        private const val CONTROLLER_TIMEOUT_SEC = 8L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val backgroundExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "sg-widget-action").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())

        private val SUPPORTED_ACTIONS = setOf(
            ACTION_PLAY_PAUSE,
            ACTION_PREVIOUS,
            ACTION_NEXT,
            ACTION_SHUFFLE,
            ACTION_REPEAT,
            ACTION_TOGGLE_FAVORITE,
            ACTION_PLAY_URI,
        )
    }
}
