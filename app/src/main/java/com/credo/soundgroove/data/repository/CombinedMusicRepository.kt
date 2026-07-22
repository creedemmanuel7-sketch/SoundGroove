package com.credo.soundgroove.data.repository

import android.content.Context
import com.credo.soundgroove.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fusionne MediaStore et dossiers SAF, avec déduplication par URI.
 */
class CombinedMusicRepository(context: Context) {

    private val mediaStoreRepository = MusicRepository(context)
    private val folderLibraryRepository = FolderLibraryRepository(context)

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val mediaStore = mediaStoreRepository.getSongs()
        val folderSongs = folderLibraryRepository.scanAllFolders()
        mergeByUri(mediaStore, folderSongs)
    }

    fun getFolderUris() = folderLibraryRepository.getFolderUris()

    fun addFolderUri(uri: android.net.Uri) = folderLibraryRepository.addFolderUri(uri)

    fun removeFolderUri(uri: android.net.Uri) = folderLibraryRepository.removeFolderUri(uri)

    companion object {
        fun mergeByUri(primary: List<Song>, secondary: List<Song>): List<Song> {
            val byUri = LinkedHashMap<String, Song>()
            primary.forEach { song -> byUri[song.uri.toString()] = song }
            secondary.forEach { song ->
                byUri.putIfAbsent(song.uri.toString(), song)
            }
            return byUri.values.sortedBy { it.title.lowercase() }
        }
    }
}
