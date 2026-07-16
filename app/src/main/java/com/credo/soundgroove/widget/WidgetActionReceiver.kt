package com.credo.soundgroove.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.credo.soundgroove.PlaybackService
import com.google.common.util.concurrent.MoreExecutors

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PLAY_PAUSE) return

        val pendingResult = goAsync()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
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

    companion object {
        const val ACTION_PLAY_PAUSE = "com.credo.soundgroove.widget.PLAY_PAUSE"
    }
}
