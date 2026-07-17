package com.credo.soundgroove.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.google.common.util.concurrent.MoreExecutors

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                when (action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_PREVIOUS -> seekPrevious(controller)
                    ACTION_NEXT -> if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                }
            } catch (_: Exception) {
                context.startActivity(
                    Intent(context, com.credo.soundgroove.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            } finally {
                MediaController.releaseFuture(controllerFuture)
                pendingResult.finish()
            }
        }, MoreExecutors.directExecutor())
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

        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

        private val SUPPORTED_ACTIONS = setOf(
            ACTION_PLAY_PAUSE,
            ACTION_PREVIOUS,
            ACTION_NEXT
        )
    }
}
