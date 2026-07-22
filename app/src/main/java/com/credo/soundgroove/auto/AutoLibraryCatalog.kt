package com.credo.soundgroove.auto

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.credo.soundgroove.SoundGrooveDatabase
import com.credo.soundgroove.data.model.Playlist
import com.credo.soundgroove.data.model.Song
import com.credo.soundgroove.data.repository.CombinedMusicRepository
import com.credo.soundgroove.data.repository.DatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Snapshot bibliothèque pour le browse Android Auto.
 * Rafraîchi hors UI ; lecture synchrone via futures Guava côté callback.
 */
class AutoLibraryCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val musicRepository = CombinedMusicRepository(appContext)
    private val db = SoundGrooveDatabase.getInstance(appContext)
    private val databaseRepository = DatabaseRepository(
        favoriteDao = db.favoriteDao(),
        recentlyPlayedDao = db.recentlyPlayedDao(),
        playlistDao = db.playlistDao(),
        metadataOverrideDao = db.metadataOverrideDao(),
    )

    @Volatile
    private var songs: List<Song> = emptyList()

    @Volatile
    private var playlists: List<Playlist> = emptyList()

    @Volatile
    private var favorites: List<Song> = emptyList()

    @Volatile
    private var hiddenFolders: Set<String> = emptySet()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("soundgroove_prefs", Context.MODE_PRIVATE)
        hiddenFolders = prefs.getStringSet("hidden_folders", emptySet())?.toSet().orEmpty()
        val allSongs = musicRepository.getAllSongs().filter { song ->
            val key = song.folderPath.takeIf { it.isNotBlank() } ?: "Dossier inconnu"
            key !in hiddenFolders
        }
        songs = allSongs
        favorites = databaseRepository.getFavorites().first()
        playlists = databaseRepository.getAllPlaylists().first().filter { !it.isSmart }
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
                    .setTitle(song.title.ifBlank { song.uri.lastPathSegment ?: "Titre" })
                    .setArtist(song.artist.ifBlank { "Artiste inconnu" })
                    .setAlbumTitle(song.albumName.ifBlank { folderKey(song) })
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
}
