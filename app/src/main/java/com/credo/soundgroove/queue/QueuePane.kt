package com.credo.soundgroove.queue

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.playback.QueuePresentation
import com.credo.soundgroove.ui.screens.QueueScreen
import com.credo.soundgroove.util.displayArtist
import com.credo.soundgroove.util.displayTitle
import org.json.JSONArray
import org.json.JSONObject

/**
 * File d'attente : Flutter si le moteur est préchauffé, sinon Compose.
 */
@Composable
fun QueuePane(
    playlist: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    accentColor: Color,
    morphProgress: Float,
    playbackPositionMs: Long,
    onClose: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onClearUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flutterReady = QueueFlutterRuntime.isReady()
    if (flutterReady) {
        LaunchedEffect(playlist, currentIndex, isPlaying, playbackPositionMs, accentColor) {
            QueueFlutterRuntime.publishSnapshot(
                buildQueueSnapshotJson(playlist, currentIndex, isPlaying, playbackPositionMs, accentColor),
            )
        }
        DisposableEffect(onPlaySong, onRemoveSong, onMoveSong, onClearUpcoming, onClose) {
            QueueFlutterRuntime.bindHandlers(onPlaySong, onRemoveSong, onMoveSong, onClearUpcoming, onClose)
            onDispose { }
        }
        AndroidView(
            modifier = modifier,
            factory = { context ->
                QueueFlutterRuntime.createFlutterView(context) ?: View(context)
            },
            onRelease = { view -> QueueFlutterRuntime.detachFlutterView(view) },
        )
    } else {
        QueueScreen(
            playlist = playlist,
            currentIndex = currentIndex,
            isPlaying = isPlaying,
            accentColor = accentColor,
            morphProgress = morphProgress,
            playbackPositionMs = playbackPositionMs,
            onClose = onClose,
            onPlaySong = onPlaySong,
            onRemoveSong = onRemoveSong,
            onMoveSong = onMoveSong,
            onClearUpcoming = onClearUpcoming,
            modifier = modifier,
        )
    }
}

internal fun buildQueueSnapshotJson(
    playlist: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    accentColor: Color,
): String {
    val keys = QueuePresentation.stableKeys(playlist.map { it.id })
    val durations = LongArray(playlist.size) { playlist[it].duration }
    val sections = QueuePresentation.split(playlist.size, currentIndex, durations, playbackPositionMs)
    fun row(index: Int): JSONObject {
        val song = playlist[index]
        return JSONObject()
            .put("index", index)
            .put("id", song.id)
            .put("key", keys[index])
            .put("title", song.displayTitle())
            .put("artist", song.displayArtist())
            .put("duration", QueuePresentation.formatRemaining(song.duration))
            .put("art", song.albumArtUri?.toString() ?: "")
    }
    val history = JSONArray()
    for (i in sections.historyStart until sections.historyEndExclusive) history.put(row(i))
    val upcoming = JSONArray()
    for (i in sections.upcomingStart until sections.upcomingEndExclusive) upcoming.put(row(i))
    return JSONObject()
        .put("history", history)
        .put("nowPlaying", if (sections.hasNowPlaying()) row(sections.nowPlaying) else JSONObject.NULL)
        .put("upcoming", upcoming)
        .put(
            "remainingLabel",
            QueuePresentation.remainingLabel(sections.remainingMs, sections.upcomingCount, sections.totalCount),
        )
        .put("isPlaying", isPlaying)
        .put("accent", accentColor.toArgb().toLong() and 0xFFFFFFFFL)
        .toString()
}
