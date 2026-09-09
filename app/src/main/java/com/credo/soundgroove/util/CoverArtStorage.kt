package com.credo.soundgroove.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Pochettes personnalisées choisies explicitement par l'utilisateur (galerie photo).
 *
 * Stocké dans `filesDir/cover_overrides/<songId>.jpg`.
 *
 * ## Import volontaire, pas un cache
 * À la différence de [com.credo.soundgroove.lyrics.LyricsCacheStore], ce dossier n'est
 * **jamais** vidé automatiquement ni par éviction LRU : c'est un choix explicite de
 * l'utilisateur (import volontaire), pas une donnée re-téléchargeable. Le bouton
 * « Vider le cache » des Réglages ne touche donc pas ce dossier.
 *
 * ## Pourquoi ça pouvait faire grossir l'app rapidement
 * Avant ce correctif, l'image sélectionnée dans la galerie était copiée **telle quelle**
 * (`copyTo`), sans redimensionnement ni recompression. Une photo de galerie moderne pèse
 * facilement 3 à 8 Mo (voire plus en haute résolution) : quelques pochettes personnalisées
 * suffisaient à expliquer une grande partie d'une hausse de stockage. On downscale et
 * recompresse désormais systématiquement en JPEG borné à [MAX_DIMENSION_PX] px de côté,
 * ce qui ramène chaque pochette à quelques dizaines/centaines de Ko au lieu de plusieurs Mo.
 *
 * ## Formats acceptés
 * JPEG / JPG, PNG, WebP (MIME `image/jpeg`, `image/jpg`, `image/png`, `image/webp`).
 * Les URI `content://` sans extension s'appuient sur le MIME du [ContentResolver].
 */
object CoverArtStorage {
    private const val COVER_DIR = "cover_overrides"

    /** Une pochette n'a jamais besoin d'être affichée au-delà de cette résolution. */
    private const val MAX_DIMENSION_PX = 1024
    private const val JPEG_QUALITY = 85

    private val SUPPORTED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    private fun coverFile(context: Context, songId: Long): File =
        File(File(context.filesDir, COVER_DIR), "$songId.jpg")

    /**
     * Résout le MIME via ContentResolver, puis extension du chemin si besoin
     * (les URI galerie n'ont souvent pas d'extension).
     */
    fun resolveMimeType(context: Context, uri: Uri): String? {
        context.contentResolver.getType(uri)?.lowercase()?.let { return it }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.lowercase().orEmpty()
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }
    }

    /**
     * `true` si le type est clairement supporté, ou inconnu (on tentera le décodage).
     * `false` seulement pour un MIME non-image explicite (ex. vidéo, PDF).
     */
    fun isAcceptableImageSource(context: Context, uri: Uri): Boolean {
        val mime = resolveMimeType(context, uri) ?: return true
        if (mime in SUPPORTED_MIME_TYPES) return true
        if (mime == "image/*" || mime.startsWith("image/")) return true
        // Certaines galeries renvoient octet-stream / */* pour des JPEG valides.
        if (mime == "application/octet-stream" || mime == "*/*") return true
        return false
    }

    /**
     * @return URI locale de la pochette, ou `null` si l'image est illisible
     * (ne lance plus d'exception fatale — évite un crash process).
     */
    suspend fun saveFromUri(context: Context, songId: Long, sourceUri: Uri): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isAcceptableImageSource(context, sourceUri)) return@runCatching null

                val dir = File(context.filesDir, COVER_DIR).apply { mkdirs() }
                val dest = File(dir, "$songId.jpg")

                val bitmap = decodeDownscaled(context, sourceUri) ?: return@runCatching null
                try {
                    FileOutputStream(dest).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                    }
                } finally {
                    bitmap.recycle()
                }
                Uri.fromFile(dest)
            }.getOrNull()
        }

    /**
     * Décode l'image en la sous-échantillonnant directement pour éviter tout pic mémoire.
     *
     * Important : avec `inJustDecodeBounds = true`, [BitmapFactory.decodeStream] renvoie
     * **toujours** `null` même en succès (il ne remplit que [BitmapFactory.Options]).
     * Ne jamais enchaîner `?: return null` sur ce résultat — sinon toutes les images
     * (JPG/PNG inclus) sont rejetées à tort.
     */
    private fun decodeDownscaled(context: Context, sourceUri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        val sampleSize = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        } else {
            // Bounds illisibles (certains providers) : on tente un décodage plein, puis cap.
            1
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        val decoded = decodeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        return capToMaxDimension(decoded)
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun capToMaxDimension(bitmap: Bitmap): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= MAX_DIMENSION_PX) return bitmap
        val scale = MAX_DIMENSION_PX.toFloat() / largestSide
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    fun getStoredUri(context: Context, songId: Long): Uri? {
        val file = coverFile(context, songId)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun delete(context: Context, songId: Long) {
        coverFile(context, songId).delete()
    }

    /**
     * Supprime les fichiers `cover_overrides/<songId>.jpg` orphelins.
     * No-op si [validSongIds] est vide.
     */
    fun deleteOrphans(context: Context, validSongIds: Set<Long>) {
        if (validSongIds.isEmpty()) return
        val dir = File(context.filesDir, COVER_DIR)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            val id = file.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (id !in validSongIds) {
                file.delete()
            }
        }
    }

    /** Taille totale actuelle des pochettes personnalisées, en octets. */
    fun sizeBytes(context: Context): Long =
        runCatching {
            File(context.filesDir, COVER_DIR).listFiles()?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)

    fun fileCount(context: Context): Int =
        runCatching { File(context.filesDir, COVER_DIR).listFiles()?.size ?: 0 }.getOrDefault(0)
}
