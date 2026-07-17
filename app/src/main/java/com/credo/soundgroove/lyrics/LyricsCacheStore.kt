package com.credo.soundgroove.lyrics

import android.content.Context
import java.io.File

/**
 * Cache local des paroles saisies ou importées, indexé par [songId].
 *
 * Stocké dans `filesDir/lyrics/<songId>.txt` — un simple miroir texte destiné à éviter
 * de re-parser/re-télécharger les paroles à chaque ouverture de l'écran Paroles.
 * Ce n'est **jamais** la source de vérité : les paroles restent retrouvables via LRCLIB
 * (en ligne) ou via le fichier `.lrc`/`.txt` voisin du fichier audio ([LyricsFileResolver]).
 *
 * ## Pourquoi une limite est nécessaire
 * Sans plafond, ce dossier grossit indéfiniment (un fichier par morceau consulté).
 * Chaque fichier reste petit (quelques Ko de texte), mais l'accumulation sur une
 * bibliothèque de plusieurs centaines de morceaux n'est pas bornée dans le temps.
 * On applique donc une éviction **LRU** (least recently used) : la date de dernière
 * modification du fichier sert d'horodatage d'usage (mise à jour à chaque lecture via
 * [read], pas seulement à l'écriture), et les fichiers les moins récemment consultés
 * sont supprimés en premier dès qu'une des deux limites est dépassée.
 */
object LyricsCacheStore {

    private const val DIR_NAME = "lyrics"

    /** Nombre max de morceaux mis en cache. Généreux vu la taille unitaire (quelques Ko). */
    private const val MAX_CACHE_FILES = 150

    /** Plafond de sécurité en octets (paroles très longues collées manuellement, etc.). */
    private const val MAX_CACHE_BYTES = 5L * 1024 * 1024 // 5 Mo

    private fun cacheDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    private fun cacheFile(context: Context, songId: Long): File =
        File(cacheDir(context), "$songId.txt")

    fun read(context: Context, songId: Long): String? {
        val file = cacheFile(context, songId)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (text != null) {
            // Marque le fichier comme récemment utilisé pour l'éviction LRU.
            runCatching { file.setLastModified(System.currentTimeMillis()) }
        }
        return text
    }

    fun write(context: Context, songId: Long, rawText: String): Boolean {
        val success = runCatching {
            cacheFile(context, songId).writeText(rawText)
            true
        }.getOrDefault(false)
        if (success) enforceLimits(context)
        return success
    }

    fun delete(context: Context, songId: Long): Boolean =
        runCatching { cacheFile(context, songId).delete() }.getOrDefault(false)

    /** Taille totale actuelle du cache paroles, en octets. */
    fun sizeBytes(context: Context): Long =
        runCatching {
            cacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)

    fun fileCount(context: Context): Int =
        runCatching { cacheDir(context).listFiles()?.size ?: 0 }.getOrDefault(0)

    /** IDs des morceaux présents dans le cache (fichiers non vides). */
    fun cachedSongIds(context: Context): Set<Long> =
        runCatching {
            cacheDir(context).listFiles()
                ?.mapNotNull { file ->
                    if (!file.isFile || file.length() <= 0L) return@mapNotNull null
                    file.nameWithoutExtension.toLongOrNull()
                }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())

    /** Vide tout le cache paroles (ne touche jamais aux fichiers .lrc/.txt voisins de l'audio). */
    fun clearAll(context: Context): Int =
        runCatching {
            val files = cacheDir(context).listFiles() ?: return 0
            var deleted = 0
            for (file in files) {
                if (file.delete()) deleted++
            }
            deleted
        }.getOrDefault(0)

    /**
     * Éviction LRU : supprime les fichiers les moins récemment utilisés jusqu'à repasser
     * sous [MAX_CACHE_FILES] ET [MAX_CACHE_BYTES].
     */
    private fun enforceLimits(context: Context) {
        runCatching {
            val files = cacheDir(context).listFiles()?.toMutableList() ?: return
            if (files.size <= MAX_CACHE_FILES && files.sumOf { it.length() } <= MAX_CACHE_BYTES) return

            files.sortBy { it.lastModified() } // plus ancien accès en premier
            var totalBytes = files.sumOf { it.length() }
            var count = files.size
            var index = 0
            while (index < files.size && (count > MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES)) {
                val file = files[index]
                val length = file.length()
                if (file.delete()) {
                    totalBytes -= length
                    count--
                }
                index++
            }
        }
    }
}
