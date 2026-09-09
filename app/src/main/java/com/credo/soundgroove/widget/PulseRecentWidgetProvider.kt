package com.credo.soundgroove.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.credo.soundgroove.MainActivity
import com.credo.soundgroove.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget « Pulse » : continuer la dernière session + 3 jaquettes récentes (Room / prefs).
 */
class PulseRecentWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refreshAsync(context)
    }

    override fun onEnabled(context: Context) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == ACTION_REFRESH
        ) {
            refreshAsync(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.credo.soundgroove.widget.PULSE_REFRESH"
        private const val TAG = "SG_WIDGET"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            refreshAsync(context.applicationContext)
        }

        private fun refreshAsync(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                val continueTrack = runCatching { WidgetOfflineStore.continueListening(appContext) }
                    .onFailure { Log.e(TAG, "continueListening failed", it) }
                    .getOrNull()
                val recent = runCatching { WidgetOfflineStore.recentPulse(appContext, 3) }
                    .onFailure { Log.e(TAG, "recentPulse failed", it) }
                    .getOrDefault(emptyList())
                launch(Dispatchers.Main.immediate) {
                    applyViews(appContext, continueTrack, recent)
                }
            }
        }

        private fun applyViews(
            context: Context,
            continueTrack: WidgetOfflineStore.PulseTrack?,
            recent: List<WidgetOfflineStore.PulseTrack>,
        ) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, PulseRecentWidgetProvider::class.java),
                )
                if (ids.isEmpty()) return

                val views = RemoteViews(context.packageName, R.layout.widget_pulse_recent)
                runCatching {
                    views.setInt(R.id.widget_pulse_root, "setBackgroundResource", R.drawable.widget_background_dark)
                }
                runCatching {
                    views.setOnClickPendingIntent(R.id.widget_pulse_root, openApp(context, 100))
                }

                if (continueTrack != null) {
                    runCatching { views.setViewVisibility(R.id.widget_continue_row, View.VISIBLE) }
                    runCatching {
                        views.setTextViewText(
                            R.id.widget_continue_track,
                            listOf(continueTrack.title, continueTrack.artist)
                                .filter { it.isNotBlank() }
                                .joinToString(" — "),
                        )
                    }
                    runCatching {
                        if (continueTrack.albumArtUri != null) {
                            views.setImageViewUri(R.id.widget_continue_art, continueTrack.albumArtUri)
                        } else {
                            views.setImageViewResource(R.id.widget_continue_art, R.drawable.ic_songs)
                        }
                    }
                    runCatching {
                        views.setOnClickPendingIntent(
                            R.id.widget_continue_row,
                            playUriPendingIntent(context, continueTrack.uri.toString(), 101),
                        )
                    }
                    runCatching {
                        views.setOnClickPendingIntent(
                            R.id.widget_continue_play,
                            playUriPendingIntent(context, continueTrack.uri.toString(), 102),
                        )
                    }
                } else {
                    runCatching { views.setViewVisibility(R.id.widget_continue_row, View.GONE) }
                }

                bindSlot(
                    context, views, 0, recent.getOrNull(0),
                    R.id.widget_pulse_slot_1, R.id.widget_pulse_art_1, R.id.widget_pulse_label_1, 110,
                )
                bindSlot(
                    context, views, 1, recent.getOrNull(1),
                    R.id.widget_pulse_slot_2, R.id.widget_pulse_art_2, R.id.widget_pulse_label_2, 120,
                )
                bindSlot(
                    context, views, 2, recent.getOrNull(2),
                    R.id.widget_pulse_slot_3, R.id.widget_pulse_art_3, R.id.widget_pulse_label_3, 130,
                )

                runCatching {
                    views.setViewVisibility(
                        R.id.widget_pulse_empty,
                        if (continueTrack == null && recent.isEmpty()) View.VISIBLE else View.GONE,
                    )
                }

                ids.forEach { id ->
                    runCatching { manager.updateAppWidget(id, views) }
                        .onFailure { Log.e(TAG, "Pulse updateAppWidget failed id=$id", it) }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Pulse applyViews failed", t)
            }
        }

        private fun bindSlot(
            context: Context,
            views: RemoteViews,
            index: Int,
            track: WidgetOfflineStore.PulseTrack?,
            slotId: Int,
            artId: Int,
            labelId: Int,
            requestCode: Int,
        ) {
            runCatching {
                if (track == null) {
                    views.setViewVisibility(slotId, if (index == 0) View.VISIBLE else View.GONE)
                    if (index == 0) {
                        views.setImageViewResource(artId, R.drawable.ic_songs)
                        views.setTextViewText(labelId, context.getString(R.string.widget_pulse_empty))
                    }
                    return
                }
                views.setViewVisibility(slotId, View.VISIBLE)
                if (track.albumArtUri != null) {
                    views.setImageViewUri(artId, track.albumArtUri)
                } else {
                    views.setImageViewResource(artId, R.drawable.ic_songs)
                }
                views.setTextViewText(labelId, track.title.ifBlank { track.artist })
                val play = playUriPendingIntent(context, track.uri.toString(), requestCode)
                views.setOnClickPendingIntent(slotId, play)
                views.setOnClickPendingIntent(artId, play)
            }.onFailure { Log.w(TAG, "bindSlot $index skipped", it) }
        }

        private fun playUriPendingIntent(context: Context, uri: String, requestCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, WidgetActionReceiver::class.java).apply {
                    action = WidgetActionReceiver.ACTION_PLAY_URI
                    putExtra(WidgetActionReceiver.EXTRA_MEDIA_URI, uri)
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openApp(context: Context, requestCode: Int): PendingIntent {
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
    }
}
