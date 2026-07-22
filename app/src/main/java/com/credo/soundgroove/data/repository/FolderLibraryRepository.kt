package com.credo.soundgroove.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.credo.soundgroove.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Dossiers SAF (Storage Access Framework) ajoutés manuellement comme sources
 * bibliothèque, en complément du scan MediaStore.
 */
class FolderLibraryRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolderUris(): Set<Uri> =
        prefs.getStringSet(KEY_FOLDER_URIS, emptySet())
            .orEmpty()
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .toSet()

    fun addFolderUri(treeUri: Uri) {
        val flags = IntentFlagsCompat.takePersistableReadPermission(context, treeUri)
        if (!flags) return
        val updated = getFolderUriStrings().toMutableSet()
        updated.add(treeUri.toString())
        prefs.edit().putStringSet(KEY_FOLDER_URIS, updated).apply()
    }

    fun removeFolderUri(treeUri: Uri) {
        val updated = getFolderUriStrings().toMutableSet()
        updated.remove(treeUri.toString())
        prefs.edit().putStringSet(KEY_FOLDER_URIS, updated).apply()
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    suspend fun scanAllFolders(): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        val seenUris = HashSet<String>()
        getFolderUris().forEach { treeUri ->
            scanTree(treeUri, seenUris, results)
        }
        results
    }

    private fun scanTree(treeUri: Uri, seenUris: MutableSet<String>, out: MutableList<Song>) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        walkDocument(root, root.name ?: treeUri.lastPathSegment ?: "Dossier", seenUris, out)
    }

    private fun walkDocument(
        file: DocumentFile,
        folderLabel: String,
        seenUris: MutableSet<String>,
        out: MutableList<Song>,
    ) {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                val childFolder = if (child.isDirectory) {
                    "${folderLabel}/${child.name ?: ""}".trimEnd('/')
                } else {
                    folderLabel
                }
                walkDocument(child, childFolder, seenUris, out)
            }
            return
        }
        if (!isAudioMime(file.type, file.name)) return
        val uri = file.uri
        val uriKey = uri.toString()
        if (!seenUris.add(uriKey)) return

        val displayName = file.name ?: uri.lastPathSegment ?: "Morceau"
        val title = displayName.substringBeforeLast('.').ifBlank { displayName }
        out.add(
            Song(
                id = safSongId(uri),
                title = title,
                artist = "Artiste inconnu",
                uri = uri,
                albumArtUri = null,
                albumName = folderLabel.substringAfterLast('/').ifBlank { folderLabel },
                folderPath = folderLabel,
                duration = 0L,
                dateAdded = file.lastModified().coerceAtLeast(0L) / 1000L,
            )
        )
    }

    private fun isAudioMime(mime: String?, name: String?): Boolean {
        if (mime?.startsWith("audio/") == true) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return ext in AUDIO_EXTENSIONS
    }

    private fun getFolderUriStrings(): Set<String> =
        prefs.getStringSet(KEY_FOLDER_URIS, emptySet())?.toSet().orEmpty()

    companion object {
        private const val PREFS_NAME = "soundgroove_folder_library"
        private const val KEY_FOLDER_URIS = "library_folder_uris"

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "3gp", "amr"
        )

        /** ID négatif stable dérivé de l'URI (distinct des IDs MediaStore positifs). */
        fun safSongId(uri: Uri): Long {
            val hash = uri.toString().hashCode().toLong()
            return if (hash == Long.MIN_VALUE) -1L else -kotlin.math.abs(hash)
        }
    }
}

/** Isole la persistance SAF pour faciliter les tests unitaires. */
internal object IntentFlagsCompat {
    fun takePersistableReadPermission(context: Context, treeUri: Uri): Boolean =
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        }.getOrDefault(false)
}
