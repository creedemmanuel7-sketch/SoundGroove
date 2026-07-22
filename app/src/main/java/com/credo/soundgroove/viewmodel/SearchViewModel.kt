package com.credo.soundgroove.viewmodel

import androidx.lifecycle.ViewModel
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song

enum class SearchFilter(val label: String) {
    Songs("Chansons"),
    Albums("Albums"),
    Artists("Artistes"),
    Playlists("Playlists"),
    Folders("Dossiers"),
    Lyrics("Paroles")
}

data class SearchSuggestion(
    val label: String,
    val subtitle: String? = null,
    val kind: String
)

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Pair<String, List<Song>>> = emptyList(),
    val artists: List<Pair<String, Int>> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val folders: List<Pair<String, List<Song>>> = emptyList(),
    val lyricsMatches: List<Song> = emptyList()
) {
    val hasAnyResults: Boolean
        get() = songs.isNotEmpty() ||
            albums.isNotEmpty() ||
            artists.isNotEmpty() ||
            playlists.isNotEmpty() ||
            folders.isNotEmpty() ||
            lyricsMatches.isNotEmpty()
}

class SearchViewModel : ViewModel() {

    fun folderKey(song: Song): String =
        song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"

    fun folderLabel(folderPath: String): String =
        folderPath.substringAfterLast('/').ifBlank { "Dossier inconnu" }

    fun buildSuggestions(
        query: String,
        allSongs: List<Song>,
        playlists: List<Playlist>,
        recentSearches: List<String> = emptyList()
    ): List<SearchSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val suggestions = mutableListOf<SearchSuggestion>()
        val seen = mutableSetOf<String>()

        fun add(label: String, subtitle: String?, kind: String) {
            val key = "$kind:$label"
            if (seen.add(key)) {
                suggestions += SearchSuggestion(label = label, subtitle = subtitle, kind = kind)
            }
        }

        recentSearches
            .filter { it.contains(trimmed, ignoreCase = true) }
            .take(2)
            .forEach { add(it, "Recherche récente", "history") }

        allSongs
            .filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true)
            }
            .take(4)
            .forEach { song ->
                add(song.title, song.artist, "song")
            }

        allSongs.groupBy { it.albumName }
            .filterKeys { it.contains(trimmed, ignoreCase = true) }
            .keys
            .sorted()
            .take(2)
            .forEach { add(it, "Album", "album") }

        playlists
            .filter { it.name.contains(trimmed, ignoreCase = true) }
            .take(2)
            .forEach { add(it.name, "Playlist", "playlist") }

        allSongs.groupBy { folderKey(it) }
            .filterKeys { path ->
                path.contains(trimmed, ignoreCase = true) ||
                    folderLabel(path).contains(trimmed, ignoreCase = true)
            }
            .keys
            .sortedBy { folderLabel(it) }
            .take(2)
            .forEach { path ->
                add(folderLabel(path), path.substringBeforeLast('/').ifBlank { "Dossier" }, "folder")
            }

        return suggestions.take(8)
    }

    fun buildResults(
        query: String,
        allSongs: List<Song>,
        playlists: List<Playlist>,
        songsWithLyrics: List<Song> = emptyList()
    ): SearchResults {
        if (query.isBlank()) return SearchResults()

        return SearchResults(
            songs = allSongs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.albumName.contains(query, ignoreCase = true)
            }.take(20),
            albums = allSongs.groupBy { it.albumName }
                .filterKeys { it.contains(query, ignoreCase = true) }
                .toList()
                .sortedBy { it.first }
                .take(8),
            artists = allSongs.groupBy { it.artist }
                .filterKeys { it.contains(query, ignoreCase = true) }
                .map { it.key to it.value.size }
                .sortedBy { it.first }
                .take(8),
            playlists = playlists.filter { it.name.contains(query, ignoreCase = true) }.take(8),
            folders = allSongs.groupBy { folderKey(it) }
                .filterKeys { path ->
                    path.contains(query, ignoreCase = true) ||
                        folderLabel(path).contains(query, ignoreCase = true)
                }
                .toList()
                .sortedBy { folderLabel(it.first) }
                .take(8),
            lyricsMatches = songsWithLyrics.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }.take(12)
        )
    }

    fun filterResults(results: SearchResults, selectedFilter: SearchFilter?): SearchResults {
        if (selectedFilter == null) return results
        return when (selectedFilter) {
            SearchFilter.Songs -> results.copy(
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                folders = emptyList(),
                lyricsMatches = emptyList()
            )
            SearchFilter.Albums -> results.copy(
                songs = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                folders = emptyList(),
                lyricsMatches = emptyList()
            )
            SearchFilter.Artists -> results.copy(
                songs = emptyList(),
                albums = emptyList(),
                playlists = emptyList(),
                folders = emptyList(),
                lyricsMatches = emptyList()
            )
            SearchFilter.Playlists -> results.copy(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                folders = emptyList(),
                lyricsMatches = emptyList()
            )
            SearchFilter.Folders -> results.copy(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                lyricsMatches = emptyList()
            )
            SearchFilter.Lyrics -> results.copy(
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                folders = emptyList()
            )
        }
    }
}
