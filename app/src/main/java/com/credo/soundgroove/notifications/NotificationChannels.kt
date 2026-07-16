package com.credo.soundgroove.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.credo.soundgroove.R

object NotificationChannels {
    const val MEDIA_PLAYBACK = "media_playback"
    const val SMART = "smart_notifications"

    fun ensureAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val mediaChannel = NotificationChannel(
            MEDIA_PLAYBACK,
            context.getString(R.string.notification_channel_media),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_media_desc)
            setShowBadge(false)
        }

        val smartChannel = NotificationChannel(
            SMART,
            context.getString(R.string.notification_channel_smart),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_smart_desc)
        }

        manager.createNotificationChannel(mediaChannel)
        manager.createNotificationChannel(smartChannel)
    }
}
