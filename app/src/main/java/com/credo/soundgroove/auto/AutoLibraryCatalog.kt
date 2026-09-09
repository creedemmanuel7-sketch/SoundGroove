package com.credo.soundgroove.auto

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.SmartPlaylistBuilder
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.CombinedMusicRepository
import com.credo.soundgroove.data.repository.DatabaseRepository
import com.credo.soundgroove.lyrics.LyricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Snapshot bibliothèque pour le browse Android Auto.
 * Rafraîchi hors UI ; lecture synchrone via futures Guava côté callback.
 */
class AutoLibraryCatalog(context: Context) {

    val applicationContext: Context = context.applicationContext
    private val musicRepository = CombinedMusicRepository(applicationContext)
    private val db = SoundGrooveDatabase.getInstance(applicationContext)
    private val databaseRepository = DatabaseRepository(db)

    @Volatile
    private var songs: List<Song> = emptyList()

    @Volatile
    private var playlists: List<Playlist> = emptyList()

    @Volatile
    private var favorites: List<Song> = emptyList()

    @Volatile
    private var hiddenFolders: Set<String> = emptySet()

    @Volatile
    private var lastSearchQuery: String = ""

    @Volatile
    private var lastSearchResults: List<MediaItem> = emptyList()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("soundgroove_prefs", Context.MODE_PRIVATE)
        hiddenFolders = prefs.getStringSet("hidden_folders", emptySet())?.toSet().orEmpty()
        val allSongs = musicRepository.getAllSongs().filter { song ->
            val key = song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"
            key !in hiddenFolders
        }
        songs = allSongs
        favorites = databaseRepository.getFavorites().first()
        val manual = databaseRepository.getAllPlaylists().first().filter { !it.isSmart }
        val recent = databaseRepository.getRecentlyPlayed().first()
        val often = databaseRepository.getOftenPlayed().first()
        val withLyrics = LyricsRepository.filterSongsWithLyrics(applicationContext, allSongs)
        playlists = SmartPlaylistBuilder.merge(
            manualPlaylists = manual,
            recentlyPlayed = recent,
            oftenPlayed = often,
            withLyrics = withLyrics,
        )
    }

    fun rootChildren(): List<MediaItem> = listOf(
        browsable(AutoMediaIds.PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
        browsable(AutoMediaIds.FOLDERS, "Dossiers", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        browsable(AutoMediaIds.FAVORITES, "Favoris", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
    )

    fun childrenOf(parentId: String): List<MediaItem> = when (parentId) {
        AutoMediaIds.ROOT -> rootChildren()
        AutoMediaIds.PLAYLISTS -> playlists.map { playlist ->
            browsable(
                mediaId = AutoMediaIds.playlist(playlist.id),
                title = playlist.name,
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                subtitle = "${playlist.songs.size} titres",
            )
        }
        AutoMediaIds.FOLDERS -> folderGroups().map { (path, count) ->
            browsable(
                mediaId = AutoMediaIds.folder(path),
                title = path.substringAfterLast('/').ifBlank { path },
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                subtitle = "$count titres",
            )
        }
        AutoMediaIds.FAVORITES -> favorites.map { songItem(it) }
        else -> {
            AutoMediaIds.parsePlaylistId(parentId)?.let { id ->
                playlists.find { it.id == id }?.songs?.map { songItem(it) }
            } ?: AutoMediaIds.parseFolderPath(parentId)?.let { path ->
                songs.filter { folderKey(it) == path }.map { songItem(it) }
            } ?: emptyList()
        }
    }

    /**
     * Recherche titres / artistes / albums (insensible à la casse).
     * Met en cache le dernier résultat pour [onGetSearchResult].
     */
    fun search(query: String): List<MediaItem> {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            lastSearchQuery = ""
            lastSearchResults = emptyList()
            return emptyList()
        }
        val results = songs
            .asSequence()
            .filter { AutoSearchMatcher.matches(it, normalized) }
            .take(MAX_SEARCH_RESULTS)
            .map { songItem(it) }
            .toList()
        lastSearchQuery = normalized
        lastSearchResults = results
        return results
    }

    fun searchResultPage(query: String, page: Int, pageSize: Int): List<MediaItem> {
        val normalized = query.trim()
        val source = if (
            normalized.equals(lastSearchQuery, ignoreCase = true) &&
            lastSearchResults.isNotEmpty()
        ) {
            lastSearchResults
        } else {
            search(normalized)
        }
        if (pageSize <= 0) return emptyList()
        return source.drop(page.coerceAtLeast(0) * pageSize).take(pageSize)
    }

    fun resolvePlayable(mediaId: String): List<MediaItem> {
        AutoMediaIds.parseSongUri(mediaId)?.let { uri ->
            val song = songs.find { it.uri.toString() == uri }
                ?: favorites.find { it.uri.toString() == uri }
            return song?.let { listOf(songItem(it)) }.orEmpty()
        }
        AutoMediaIds.parsePlaylistId(mediaId)?.let { id ->
            return playlists.find { it.id == id }?.songs?.map { songItem(it) }.orEmpty()
        }
        AutoMediaIds.parseFolderPath(mediaId)?.let { path ->
            return songs.filter { folderKey(it) == path }.map { songItem(it) }
        }
        if (mediaId == AutoMediaIds.FAVORITES) {
            return favorites.map { songItem(it) }
        }
        return emptyList()
    }

    /**
     * Construit la file pour [MediaSession.Callback.onPlaybackResumption]
     * à partir de [PlaybackSessionStore] (IDs Room).
     */
    fun sessionResumptionPlaylist(
        songId: Long,
        queueIds: List<Long>,
        positionMs: Long,
        forPlayback: Boolean,
    ): MediaSession.MediaItemsWithStartPosition? {
        val byId = HashMap<Long, Song>(songs.size + favorites.size)
        for (song in songs) byId[song.id] = song
        for (song in favorites) byId.putIfAbsent(song.id, song)

        val current = byId[songId] ?: return null
        if (!forPlayback) {
            return MediaSession.MediaItemsWithStartPosition(
                listOf(songItem(current)),
                /* startIndex = */ 0,
                positionMs.coerceAtLeast(0L),
            )
        }

        val queue = queueIds.mapNotNull { byId[it] }
            .ifEmpty { listOf(current) }
            .distinctBy { it.id }
            .take(MAX_RESUMPTION_QUEUE)
        val index = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        return MediaSession.MediaItemsWithStartPosition(
            queue.map { songItem(it) },
            index,
            positionMs.coerceAtLeast(0L),
        )
    }

    fun item(mediaId: String): MediaItem? {
        if (mediaId == AutoMediaIds.ROOT) {
            return browsable(AutoMediaIds.ROOT, "SoundGroove", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        }
        rootChildren().find { it.mediaId == mediaId }?.let { return it }
        childrenOf(AutoMediaIds.PLAYLISTS).find { it.mediaId == mediaId }?.let { return it }
        childrenOf(AutoMediaIds.FOLDERS).find { it.mediaId == mediaId }?.let { return it }
        resolvePlayable(mediaId).firstOrNull()?.let { return it }
        return null
    }

    private fun folderGroups(): List<Pair<String, Int>> =
        songs.groupingBy { folderKey(it) }
            .eachCount()
            .entries
            .sortedBy { it.key.lowercase() }
            .map { it.key to it.value }

    private fun folderKey(song: Song): String =
        song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"

    private fun songItem(song: Song): MediaItem {
        val base = MediaItem.Builder()
            .setMediaId(AutoMediaIds.song(song.uri.toString()))
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(com.credo.soundgroove.util.SongDisplay.title(song.title, song.folderPath))
                    .setArtist(
                        com.credo.soundgroove.util.SongDisplay.artist(
                            song.artist,
                            song.title,
                            song.folderPath,
                        )
                    )
                    .setAlbumTitle(com.credo.soundgroove.util.SongDisplay.album(song.albumName).let {
                        if (it == "Album inconnu") folderKey(song) else it
                    })
                    .setArtworkUri(song.albumArtUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
        return AutoSessionExtras.enrichMediaItem(base, song)
    }

    private fun browsable(
        mediaId: String,
        title: String,
        mediaType: Int,
        subtitle: String? = null,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()

    companion object {
        const val MAX_SEARCH_RESULTS = 50
        /** File système limitée pour une reprise rapide (BT / notif). */
        const val MAX_RESUMPTION_QUEUE = 64
    }
}
