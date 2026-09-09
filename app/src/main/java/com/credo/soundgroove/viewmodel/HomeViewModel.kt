package com.credo.soundgroove.viewmodel

import androidx.lifecycle.ViewModel
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.LocalScrobbleStats
import kotlin.random.Random

data class ContinueListening(
    val song: Song,
    val isActiveSession: Boolean,
    val isPlaying: Boolean,
    val isBuffering: Boolean = false,
    val progress: Float,
    val positionLabel: String,
    val sessionLabel: String
)

data class HomeUiState(
    val continueListening: ContinueListening? = null,
    /** Pulse local : mix scrobble + favoris + récents (zéro cloud). */
    val pulseSongs: List<Song> = emptyList(),
    val mixSuggestions: List<Song> = emptyList(),
)

class HomeViewModel : ViewModel() {

    fun buildUiState(
        songs: List<Song>,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        currentSong: Song?,
        isPlaying: Boolean,
        playbackPosition: Long,
        playbackQueue: List<Song>,
        isBuffering: Boolean = false,
        isControllerConnecting: Boolean = false,
        scrobbleStats: LocalScrobbleStats? = null,
    ): HomeUiState {
        val pulse = buildLocalPulse(songs, recentlyPlayed, favoriteSongs, scrobbleStats)
        return HomeUiState(
            continueListening = buildContinueListening(
                recentlyPlayed = recentlyPlayed,
                currentSong = currentSong,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isControllerConnecting = isControllerConnecting,
                playbackPosition = playbackPosition,
                playbackQueue = playbackQueue
            ),
            pulseSongs = pulse,
            mixSuggestions = pulse,
        )
    }

    private fun buildContinueListening(
        recentlyPlayed: List<Song>,
        currentSong: Song?,
        isPlaying: Boolean,
        isBuffering: Boolean,
        isControllerConnecting: Boolean,
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
            isActiveSession && isControllerConnecting -> "Connexion…"
            isActiveSession && isBuffering -> "Chargement…"
            isActiveSession && queueRemaining > 0 -> "En pause · $queueRemaining titre(s) à suivre"
            isActiveSession -> "En pause"
            else -> "Dernière écoute"
        }
        return ContinueListening(
            song = song,
            isActiveSession = isActiveSession,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
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

    /**
     * Pulse local — heuristique 100 % offline :
     * scrobble (top tracks) + favoris + récents, seedée par le jour.
     */
    fun buildLocalPulse(
        songs: List<Song>,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>,
        scrobbleStats: LocalScrobbleStats?,
        limit: Int = 20,
    ): List<Song> {
        if (songs.isEmpty()) return emptyList()

        val calendar = java.util.Calendar.getInstance()
        val daySeed = calendar.get(java.util.Calendar.YEAR) * 400L +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val byId = songs.associateBy { it.id }
        val listenCounts = recentlyPlayed.groupingBy { it.id }.eachCount()
        val favoriteIds = favoriteSongs.map { it.id }.toSet()
        val recentUnique = recentlyPlayed.distinctBy { it.id }.take(16)
        val recentArtists = recentUnique.map { normalizeKey(it.artist) }.toSet()
        val scrobbleBoost = scrobbleStats?.topTracks
            ?.associate { it.songId to it.playCount }
            .orEmpty()
        val topScrobbleArtists = scrobbleStats?.topArtists
            ?.take(8)
            ?.map { normalizeKey(it.artist) }
            ?.toSet()
            .orEmpty()

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
            if (song.id in favoriteIds) points += 140
            points += (scrobbleBoost[song.id] ?: 0) * 35
            points += (listenCounts[song.id] ?: 0) * 22
            val recentIndex = recentUnique.indexOfFirst { it.id == song.id }
            if (recentIndex >= 0) points += 48 - recentIndex * 3
            val artistKey = normalizeKey(song.artist)
            if (artistKey in recentArtists) points += 20
            if (artistKey in topScrobbleArtists) points += 28
            if (song.albumArtUri != null) points += 8
            if (hasUsefulMetadata(song)) points += 8
            points += (stableDailyRank(song) % 17).toInt()
            return points
        }

        val ranked = songs
            .sortedWith(
                compareByDescending<Song> { score(it) }
                    .thenBy { stableDailyRank(it) }
            )
            .distinctBy { it.id }

        // Garantit un noyau scrobble/favoris/récents en tête si dispo.
        val seedIds = LinkedHashSet<Long>()
        scrobbleStats?.topTracks?.forEach { seedIds.add(it.songId) }
        favoriteSongs.take(8).forEach { seedIds.add(it.id) }
        recentUnique.take(8).forEach { seedIds.add(it.id) }
        val seeded = seedIds.mapNotNull { byId[it] }
        val rest = ranked.filter { it.id !in seedIds }
        val dayRandom = Random(daySeed)
        return (seeded.shuffled(dayRandom) + rest)
            .distinctBy { it.id }
            .take(limit)
    }

    /** @deprecated Prefer [buildLocalPulse] — conservé pour tests existants. */
    fun buildMixSuggestions(
        songs: List<Song>,
        recentlyPlayed: List<Song>,
        favoriteSongs: List<Song>
    ): List<Song> = buildLocalPulse(songs, recentlyPlayed, favoriteSongs, scrobbleStats = null)

    fun buildSimilarSongs(
        songs: List<Song>,
        seedSong: Song?,
        exclude: List<Song> = emptyList()
    ): List<Song> {
        if (songs.isEmpty() || seedSong == null) return emptyList()
        val excludeIds = exclude.map { it.id }.toSet() + seedSong.id
        val seedArtist = normalizeKey(seedSong.artist)
        val seedAlbum = normalizeKey(seedSong.albumName)
        fun rank(song: Song): Int {
            if (song.id in excludeIds) return Int.MIN_VALUE
            var points = 0
            val artist = normalizeKey(song.artist)
            val album = normalizeKey(song.albumName)
            if (artist == seedArtist) points += 100
            if (album == seedAlbum && album.isNotBlank() && album != "inconnu") points += 70
            if (artist == seedArtist && album == seedAlbum) points += 30
            if (song.albumArtUri != null) points += 5
            return points
        }
        return songs
            .filter { rank(it) > 0 }
            .sortedWith(compareByDescending<Song> { rank(it) }.thenBy { it.title.lowercase() })
            .distinctBy { it.id }
            .take(12)
    }

    fun buildLocalRadio(songs: List<Song>, seedSong: Song?): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val calendar = java.util.Calendar.getInstance()
        val dayPart = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val seedId = seedSong?.id ?: 0L
        val random = Random(dayPart.toLong() * 9973L + seedId * 7919L)
        val pool = when {
            seedSong != null -> {
                val artistKey = normalizeKey(seedSong.artist)
                val artistSongs = songs.filter { normalizeKey(it.artist) == artistKey && it.id != seedSong.id }
                if (artistSongs.size >= 6) artistSongs else {
                    val albumKey = normalizeKey(seedSong.albumName)
                    val albumSongs = songs.filter {
                        normalizeKey(it.albumName) == albumKey && it.id != seedSong.id
                    }
                    (artistSongs + albumSongs).distinctBy { it.id }
                        .ifEmpty { songs.filter { it.id != seedSong.id } }
                }
            }
            else -> songs
        }
        return pool
            .shuffled(random)
            .distinctBy { it.id }
            .let { shuffled ->
                if (seedSong != null) listOf(seedSong) + shuffled.filter { it.id != seedSong.id } else shuffled
            }
            .take(20)
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

    private fun normalizeKey(value: String): String =
        value.trim().lowercase().ifBlank { "inconnu" }
}
