package com.credo.soundgroove.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.credo.soundgroove.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MusicRepository(private val context: Context) {

    /**
     * Scan MediaStore avec déduplication sûre :
     * - même `_ID` → une entrée ;
     * - même chemin absolu (`DATA`) ou même `RELATIVE_PATH`+`DISPLAY_NAME` (+ taille) →
     *   une entrée (MediaStore peut indexer deux fois le même fichier).
     *
     * Ne fusionne **pas** deux fichiers distincts qui partagent seulement titre/artiste
     * (faux positifs type covers / remixes). Voir `docs/QUALITY_DATA_UI_FIXES.md`.
     */
    suspend fun getSongs(): List<Song> = withContext(Dispatchers.IO) {
        val byId = LinkedHashMap<Long, Song>()
        val pathOwner = HashMap<String, Long>()

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                if (byId.containsKey(id)) continue

                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                val albumId = cursor.getLong(albumIdCol)
                val albumName = cursor.getString(albumNameCol).orEmpty()
                val dataPath = cursor.getString(dataCol).orEmpty()
                val duration = cursor.getLong(durationCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val displayName = cursor.getString(displayNameCol).orEmpty()
                val size = cursor.getLong(sizeCol)
                val relativePath = if (relativePathCol >= 0) {
                    cursor.getString(relativePathCol).orEmpty()
                } else {
                    ""
                }

                val folderPath = when {
                    dataPath.isNotBlank() -> dataPath.substringBeforeLast("/", "")
                    relativePath.isNotBlank() -> relativePath.trimEnd('/')
                    else -> ""
                }

                val pathKey = dedupePathKey(dataPath, relativePath, displayName, size)
                if (pathKey != null) {
                    val existingId = pathOwner[pathKey]
                    if (existingId != null && existingId != id) {
                        val existing = byId[existingId]
                        if (existing != null && !preferIncoming(title, artist, dateAdded, existing)) {
                            continue
                        }
                        byId.remove(existingId)
                    }
                    pathOwner[pathKey] = id
                }

                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )
                byId[id] = Song(
                    id, title, artist, uri, albumArtUri, albumName, folderPath, duration, dateAdded
                )
            }
        }
        byId.values.toList()
    }

    /**
     * Clé de dédup : chemin absolu normalisé, sinon relative+nom+taille
     * (Android 10+ où `DATA` est souvent vide).
     */
    private fun dedupePathKey(
        dataPath: String,
        relativePath: String,
        displayName: String,
        size: Long,
    ): String? {
        val absolute = normalizePath(dataPath)
        if (absolute.isNotBlank()) return "abs:$absolute"
        val relative = normalizePath(relativePath)
        val name = displayName.trim().lowercase(Locale.US)
        if (relative.isNotBlank() && name.isNotBlank()) {
            return "rel:$relative|$name|$size"
        }
        return null
    }

    private fun normalizePath(path: String): String =
        path.trim().lowercase(Locale.US).replace('\\', '/').trimEnd('/')

    /** Préfère l'entrée avec métadonnées plus riches / plus récente. */
    private fun preferIncoming(
        title: String,
        artist: String,
        dateAdded: Long,
        existing: Song,
    ): Boolean {
        val incomingScore = metadataScore(title, artist)
        val existingScore = metadataScore(existing.title, existing.artist)
        if (incomingScore != existingScore) return incomingScore > existingScore
        return dateAdded >= existing.dateAdded
    }

    private fun metadataScore(title: String, artist: String): Int {
        var score = 0
        if (title.isNotBlank() && !title.equals("<unknown>", ignoreCase = true)) score += 2
        if (artist.isNotBlank() && !artist.equals("<unknown>", ignoreCase = true)) score += 2
        if (title.contains(' ')) score += 1
        return score
    }
}
