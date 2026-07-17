package com.credo.soundgroove.util

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.credo.soundgroove.data.model.Song

data class MetadataEditResult(
    val localSaved: Boolean,
    val fileWriteAttempted: Boolean,
    val fileWriteSuccess: Boolean,
    val message: String
)

object MetadataEditor {

    /**
     * Tente de mettre à jour MediaStore (peut propager aux tags selon l'appareil).
     * Sur Android 10+, l'écriture directe des tags ID3 dans le fichier reste limitée.
     */
    fun tryWriteToMediaStore(
        context: Context,
        song: Song,
        title: String,
        artist: String,
        album: String
    ): MetadataEditResult {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
            }
            val updated = context.contentResolver.update(song.uri, values, null, null)
            if (updated > 0) {
                MetadataEditResult(
                    localSaved = true,
                    fileWriteAttempted = true,
                    fileWriteSuccess = true,
                    message = "Métadonnées mises à jour (MediaStore)."
                )
            } else {
                MetadataEditResult(
                    localSaved = true,
                    fileWriteAttempted = true,
                    fileWriteSuccess = false,
                    message = "Modification locale enregistrée. Écriture fichier non confirmée."
                )
            }
        } catch (e: SecurityException) {
            MetadataEditResult(
                localSaved = true,
                fileWriteAttempted = true,
                fileWriteSuccess = false,
                message = "Modification locale enregistrée. Permission insuffisante pour le fichier."
            )
        } catch (e: Exception) {
            MetadataEditResult(
                localSaved = true,
                fileWriteAttempted = true,
                fileWriteSuccess = false,
                message = "Modification locale enregistrée. Écriture fichier impossible."
            )
        }
    }
}
