package com.credo.soundgroove.viewmodel

import androidx.lifecycle.ViewModel
import com.credo.soundgroove.data.model.Song

data class ContinueListening(
    val song: Song,
    val isActiveSession: Boolean,
    val isPlaying: Boolean,
    val progress: Float,
    val positionLabel: String,
    val sessionLabel: String
)

data class HomeUiState(
    val continueListening: ContinueListening? = null,
    val mixSuggestions: List<Song> = emptyList(),
    val newAdditions: List<Song> = emptyList()
)

class HomeViewModel : ViewModel() {

    fun buildUiState(
        songs: List<Song>,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        currentSong: Song?,
        isPlaying: Boolean,
        playbackPosition: Long,
        playbackQueue: List<Song>
    ): HomeUiState = HomeUiState(
        continueListening = buildContinueListening(
            recentlyPlayed = recentlyPlayed,
            currentSong = currentSong,
            isPlaying = isPlaying,
            playbackPosition = playbackPosition,
            playbackQueue = playbackQueue
        ),
        mixSuggestions = buildMixSuggestions(songs, recentlyPlayed, favoriteSongs),
        newAdditions = buildNewAdditions(songs)
    )

    private fun buildContinueListening(
        recentlyPlayed: List<Song>,
        currentSong: Song?,
        isPlaying: Boolean,
        playbackPosition: Long,
        playbackQueue: List<Song>
    ): ContinueListening? {
        val song = currentSong ?: recentlyPlayed.firstOrNull() ?: return null
        val isActiveSession = currentSong != null
        val duration = song.duration.takeIf { it > 0L } ?: 1L
        val progress = if (isActiveSession) {
            (playbackPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val positionLabel = if (isActiveSession && playbackPosition > 0L) {
            formatProgressLabel(playbackPosition, duration)
        } else {
            ""
        }
        val queueRemaining = if (isActiveSession && playbackQueue.size > 1) {
            val currentIndex = playbackQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            playbackQueue.size - currentIndex - 1
        } else {
            0
        }
        val sessionLabel = when {
            isActiveSession && isPlaying -> "En lecture"
            isActiveSession && queueRemaining > 0 -> "En pause · $queueRemaining titre(s) à suivre"
            isActiveSession -> "En pause"
            else -> "Dernière écoute"
        }
        return ContinueListening(
            song = song,
            isActiveSession = isActiveSession,
            isPlaying = isPlaying,
            progress = progress,
            positionLabel = positionLabel,
            sessionLabel = sessionLabel
        )
    }

    private fun formatProgressLabel(positionMs: Long, durationMs: Long): String {
        fun fmt(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return "%d:%02d".format(min, sec)
        }
        return "${fmt(positionMs)} / ${fmt(durationMs)}"
    }

    fun buildMixSuggestions(
        songs: List<Song>,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>
    ): List<Song> {
        if (songs.isEmpty()) return emptyList()

        val calendar = java.util.Calendar.getInstance()
        val daySeed = calendar.get(java.util.Calendar.YEAR) * 400L +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val listenCounts = recentlyPlayed.groupingBy { it.id }.eachCount()
        val favoriteIds = favoriteSongs.map { it.id }.toSet()
        val recentUnique = recentlyPlayed.distinctBy { it.id }.take(8)

        fun hasUsefulMetadata(song: Song): Boolean {
            val unknownValues = setOf("", "inconnu", "unknown", "<unknown>")
            return song.title.trim().lowercase() !in unknownValues &&
                song.artist.trim().lowercase() !in unknownValues
        }

        fun stableDailyRank(song: Song): Long {
            val mixed = song.id * 1103515245L + daySeed * 12345L
            return if (mixed == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(mixed)
        }

        fun score(song: Song): Int {
            var points = 0
            if (song.id in favoriteIds) points += 100
            points += (listenCounts[song.id] ?: 0) * 20
            val recentIndex = recentUnique.indexOfFirst { it.id == song.id }
            if (recentIndex >= 0) points += 30 - recentIndex * 4
            if (song.albumArtUri != null) points += 5
            if (hasUsefulMetadata(song)) points += 5
            return points
        }

        return songs
            .sortedWith(
                compareByDescending<Song> { score(it) }
                    .thenBy { stableDailyRank(it) }
            )
            .distinctBy { it.id }
            .take(10)
    }

    fun buildNewAdditions(songs: List<Song>, limit: Int = 8): List<Song> {
        val thirtyDaysAgoSec = (System.currentTimeMillis() / 1000L) - (30L * 24L * 60L * 60L)
        return songs
            .filter { it.dateAdded > 0L }
            .filter { it.dateAdded >= thirtyDaysAgoSec }
            .sortedByDescending { it.dateAdded }
            .distinctBy { it.id }
            .take(limit)
    }
}
