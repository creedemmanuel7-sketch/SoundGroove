package com.credo.soundgroove.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAllWidgets(context)
        }
    }

    companion object {
        private const val LARGE_MIN_WIDTH_DP = 280
        private const val LARGE_MIN_HEIGHT_DP = 110

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MusicAppWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { widgetId ->
                updateWidget(context, manager, widgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val state = WidgetState.read(context)
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, LARGE_MIN_WIDTH_DP)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, LARGE_MIN_HEIGHT_DP)
            val isLarge = minWidth >= LARGE_MIN_WIDTH_DP && minHeight >= LARGE_MIN_HEIGHT_DP

            val layoutRes = if (isLarge) {
                R.layout.widget_music_player_large
            } else {
                R.layout.widget_music_player
            }
            val backgroundRes = when (state.skin) {
                WidgetSkin.CYAN -> R.drawable.widget_background_cyan
                WidgetSkin.DARK -> R.drawable.widget_background_dark
            }

            val views = RemoteViews(context.packageName, layoutRes)
            views.setInt(R.id.widget_root, "setBackgroundResource", backgroundRes)

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

            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                actionPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, 0)
            )
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                actionPendingIntent(context, WidgetActionReceiver.ACTION_PREVIOUS, 1)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(context, WidgetActionReceiver.ACTION_NEXT, 2)
            )

            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    3,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, WidgetActionReceiver::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
