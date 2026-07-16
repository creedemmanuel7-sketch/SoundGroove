package com.credo.soundgroove.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.credo.soundgroove.MainActivity
import com.credo.soundgroove.R
import com.credo.soundgroove.data.model.Song

object SmartNotificationManager {
    private const val RESUME_NOTIFICATION_ID = 2001
    private const val SESSION_SUMMARY_NOTIFICATION_ID = 2002

    fun cancelAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(RESUME_NOTIFICATION_ID)
        manager.cancel(SESSION_SUMMARY_NOTIFICATION_ID)
    }

    fun showResumeReminder(context: Context, song: Song) {
        NotificationChannels.ensureAll(context)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.SMART)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(context.getString(R.string.smart_notification_resume_title))
            .setContentText(
                context.getString(
                    R.string.smart_notification_resume_body,
                    song.title,
                    song.artist
                )
            )
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(RESUME_NOTIFICATION_ID, notification)
    }

    fun showSessionSummary(context: Context, minutes: Int) {
        NotificationChannels.ensureAll(context)
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.SMART)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(context.getString(R.string.smart_notification_summary_title))
            .setContentText(
                context.getString(R.string.smart_notification_summary_body, minutes)
            )
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(SESSION_SUMMARY_NOTIFICATION_ID, notification)
    }
}
