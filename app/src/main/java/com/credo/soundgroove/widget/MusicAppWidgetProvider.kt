package com.credo.soundgroove.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.credo.soundgroove.MainActivity
import com.credo.soundgroove.R

class MusicAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
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
        private const val TAG = "SG_WIDGET"
        private const val LARGE_MIN_WIDTH_DP = 250
        private const val LARGE_MIN_HEIGHT_DP = 120
        private const val COMPACT_MAX_HEIGHT_DP = 90

        private const val ACCENT = 0xFFC084FC.toInt()
        private const val ACCENT_SOFT = 0xFFA855F7.toInt()
        private const val MUTED = 0x66FFFFFF

        fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, MusicAppWidgetProvider::class.java)
                manager.getAppWidgetIds(component).forEach { widgetId ->
                    updateWidget(context, manager, widgetId)
                }
            }.onFailure { Log.e(TAG, "updateAllWidgets failed", it) }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
        ) {
            try {
                val state = WidgetState.read(context)
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, LARGE_MIN_WIDTH_DP)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, LARGE_MIN_HEIGHT_DP)
                val isLarge = minWidth >= LARGE_MIN_WIDTH_DP && minHeight >= LARGE_MIN_HEIGHT_DP
                val isCompact = minHeight <= COMPACT_MAX_HEIGHT_DP

                val layoutRes = when {
                    isLarge -> R.layout.widget_music_player_large
                    isCompact -> R.layout.widget_music_player
                    else -> R.layout.widget_music_player
                }
                val backgroundRes = when (state.skin) {
                    WidgetSkin.CYAN -> R.drawable.widget_background_cyan
                    WidgetSkin.DARK -> R.drawable.widget_background_dark
                }

                val views = RemoteViews(context.packageName, layoutRes)
                safeSetInt(views, R.id.widget_root, "setBackgroundResource", backgroundRes)

                safeSetText(
                    views,
                    R.id.widget_title,
                    state.title.ifBlank { context.getString(R.string.widget_no_track) },
                )
                safeSetText(
                    views,
                    R.id.widget_artist,
                    state.artist.ifBlank { context.getString(R.string.app_name) },
                )

                runCatching {
                    if (state.albumArtUri != null) {
                        views.setImageViewUri(R.id.widget_album_art, state.albumArtUri)
                    } else {
                        views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_songs)
                    }
                }

                val playPauseIcon = if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                runCatching { views.setImageViewResource(R.id.widget_play_pause, playPauseIcon) }
                safeSetColorFilter(views, R.id.widget_play_pause, ACCENT)

                runCatching {
                    views.setProgressBar(R.id.widget_progress, 100, state.progressPercent, false)
                }

                bindTransport(context, views)
                bindModeButtons(context, views, state, isLarge)

                if (isLarge) {
                    bindLargeExtras(context, views, state)
                }

                val openApp = openAppPendingIntent(context, requestCode = 30)
                runCatching { views.setOnClickPendingIntent(R.id.widget_root, openApp) }
                runCatching { views.setOnClickPendingIntent(R.id.widget_album_art, openApp) }

                appWidgetManager.updateAppWidget(widgetId, views)
            } catch (t: Throwable) {
                Log.e(TAG, "updateWidget failed id=$widgetId", t)
            }
        }

        private fun bindTransport(context: Context, views: RemoteViews) {
            runCatching {
                views.setOnClickPendingIntent(
                    R.id.widget_play_pause,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, 0),
                )
            }
            runCatching {
                views.setOnClickPendingIntent(
                    R.id.widget_previous,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_PREVIOUS, 1),
                )
            }
            runCatching {
                views.setOnClickPendingIntent(
                    R.id.widget_next,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_NEXT, 2),
                )
            }
        }

        private fun bindModeButtons(
            context: Context,
            views: RemoteViews,
            state: WidgetPlaybackState,
            isLarge: Boolean,
        ) {
            runCatching { views.setViewVisibility(R.id.widget_shuffle, View.VISIBLE) }
            runCatching {
                views.setOnClickPendingIntent(
                    R.id.widget_shuffle,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_SHUFFLE, 4),
                )
            }
            safeSetColorFilter(
                views,
                R.id.widget_shuffle,
                if (state.shuffleEnabled) ACCENT_SOFT else MUTED,
            )

            runCatching { views.setViewVisibility(R.id.widget_repeat, View.VISIBLE) }
            runCatching {
                views.setOnClickPendingIntent(
                    R.id.widget_repeat,
                    actionPendingIntent(context, WidgetActionReceiver.ACTION_REPEAT, 5),
                )
            }
            val repeatIcon = when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                else -> R.drawable.ic_repeat
            }
            runCatching { views.setImageViewResource(R.id.widget_repeat, repeatIcon) }
            safeSetColorFilter(
                views,
                R.id.widget_repeat,
                if (state.repeatMode != Player.REPEAT_MODE_OFF) ACCENT_SOFT else MUTED,
            )

            if (isLarge) {
                runCatching { views.setViewVisibility(R.id.widget_favorite, View.VISIBLE) }
                runCatching {
                    views.setImageViewResource(
                        R.id.widget_favorite,
                        if (state.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline,
                    )
                }
                safeSetColorFilter(views, R.id.widget_favorite, 0xFFF472B6.toInt())
                runCatching {
                    views.setOnClickPendingIntent(
                        R.id.widget_favorite,
                        actionPendingIntent(context, WidgetActionReceiver.ACTION_TOGGLE_FAVORITE, 6),
                    )
                }
            }
        }

        private fun bindLargeExtras(
            context: Context,
            views: RemoteViews,
            state: WidgetPlaybackState,
        ) {
            runCatching {
                val up = state.upNext
                if (up.isEmpty()) {
                    views.setViewVisibility(R.id.widget_upnext_block, View.GONE)
                    return
                }
                views.setViewVisibility(R.id.widget_upnext_block, View.VISIBLE)
                val first = up[0]
                views.setTextViewText(
                    R.id.widget_upnext_1,
                    listOf(first.title, first.artist).filter { it.isNotBlank() }.joinToString(" — "),
                )
                if (up.size > 1) {
                    val second = up[1]
                    views.setViewVisibility(R.id.widget_upnext_2, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_upnext_2,
                        listOf(second.title, second.artist).filter { it.isNotBlank() }.joinToString(" — "),
                    )
                } else {
                    views.setViewVisibility(R.id.widget_upnext_2, View.GONE)
                }
            }.onFailure { Log.w(TAG, "bindLargeExtras skipped", it) }
        }

        private fun safeSetText(views: RemoteViews, viewId: Int, text: CharSequence) {
            runCatching { views.setTextViewText(viewId, text) }
        }

        private fun safeSetInt(views: RemoteViews, viewId: Int, method: String, value: Int) {
            runCatching { views.setInt(viewId, method, value) }
        }

        /** setColorFilter via RemoteViews est fragile sur certains OEM — ne jamais faire planter l'update. */
        private fun safeSetColorFilter(views: RemoteViews, viewId: Int, color: Int) {
            runCatching { views.setInt(viewId, "setColorFilter", color) }
                .onFailure { Log.w(TAG, "setColorFilter skipped for $viewId", it) }
        }

        private fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, WidgetActionReceiver::class.java).apply {
                    this.action = action
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
