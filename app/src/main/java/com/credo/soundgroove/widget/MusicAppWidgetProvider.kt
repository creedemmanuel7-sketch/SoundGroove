package com.credo.soundgroove.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.credo.soundgroove.MainActivity
import com.credo.soundgroove.R

class MusicAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAllWidgets(context)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MusicAppWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { widgetId ->
                updateWidget(context, manager, widgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val state = WidgetState.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            views.setTextViewText(
                R.id.widget_title,
                state.title.ifBlank { context.getString(R.string.widget_no_track) }
            )
            views.setTextViewText(
                R.id.widget_artist,
                state.artist.ifBlank { context.getString(R.string.app_name) }
            )

            if (state.albumArtUri != null) {
                views.setImageViewUri(R.id.widget_album_art, state.albumArtUri)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_songs)
            }

            val playPauseIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            val playPauseIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, WidgetActionReceiver::class.java).apply {
                    action = WidgetActionReceiver.ACTION_PLAY_PAUSE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause, playPauseIntent)

            val openAppIntent = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
