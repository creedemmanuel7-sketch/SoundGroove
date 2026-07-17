package com.credo.soundgroove.lyrics

import android.content.Context
import android.provider.MediaStore
import com.credo.soundgroove.data.model.Song
import java.io.File

/**
 * Recherche un fichier de paroles local (.lrc puis .txt) portant le même nom
 * de base que le fichier audio, dans le même dossier.
 *
 * v1 — local uniquement : le chemin réel du fichier audio est résolu via la
 * colonne MediaStore.DATA (déjà utilisée ailleurs dans l'app), puis on tente
 * une lecture directe du fichier voisin. Sur Android 11+ (stockage cloisonné),
 * cette lecture directe peut échouer si l'app n'a pas l'accès "Tous les
 * fichiers" — dans ce cas la recherche échoue proprement (pas de crash, pas
 * de blocage), voir la section "Limites" côté documentation.
 */
object LyricsFileResolver {

    private val SUPPORTED_EXTENSIONS = listOf("lrc", "txt")

    fun findLyricsFile(context: Context, song: Song): File? {
        val audioPath = resolveAudioFilePath(context, song) ?: return null
        val audioFile = File(audioPath)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        for (extension in SUPPORTED_EXTENSIONS) {
            val candidate = File(parent, "$baseName.$extension")
            if (runCatching { candidate.exists() && candidate.canRead() }.getOrDefault(false)) {
                return candidate
            }
        }
        return null
    }

    /**
     * Tente d'écrire un fichier de paroles à côté de l'audio si le dossier est accessible
     * en écriture. Retourne false sans erreur si l'écriture n'est pas possible.
     */
    fun tryWriteLyricsFile(context: Context, song: Song, rawText: String): Boolean {
        val audioPath = resolveAudioFilePath(context, song) ?: return false
        val audioFile = File(audioPath)
        val parent = audioFile.parentFile ?: return false
        if (!runCatching { parent.canWrite() }.getOrDefault(false)) return false

        val extension = if (LrcParser.isLikelySynced(rawText)) "lrc" else "txt"
        val target = File(parent, "${audioFile.nameWithoutExtension}.$extension")
        return runCatching {
            target.writeText(rawText)
            true
        }.getOrDefault(false)
    }

    /**
     * Tente de supprimer les fichiers `.lrc` / `.txt` voisins de l'audio.
     * Échoue silencieusement si le stockage n'est pas accessible en écriture.
     */
    fun tryDeleteLyricsFile(context: Context, song: Song): Boolean {
        val audioPath = resolveAudioFilePath(context, song) ?: return false
        val audioFile = File(audioPath)
        val parent = audioFile.parentFile ?: return false
        if (!runCatching { parent.canWrite() }.getOrDefault(false)) return false

        var deleted = false
        for (extension in SUPPORTED_EXTENSIONS) {
            val candidate = File(parent, "${audioFile.nameWithoutExtension}.$extension")
            if (runCatching { candidate.exists() && candidate.delete() }.getOrDefault(false)) {
                deleted = true
            }
        }
        return deleted
    }

    private fun resolveAudioFilePath(context: Context, song: Song): String? = runCatching {
        context.contentResolver.query(
            song.uri,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (column < 0) null else cursor.getString(column)
        }
    }.getOrNull()
}
