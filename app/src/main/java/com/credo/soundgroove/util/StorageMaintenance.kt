package com.credo.soundgroove.util

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.credo.soundgroove.lyrics.LyricsCacheStore
import java.io.File
import java.util.Locale

/**
 * Point d'entrée unique pour mesurer et vider le stockage local **non essentiel**
 * (re-téléchargeable ou re-générable) de SoundGroove. Voir `docs/STORAGE_AND_CACHE.md`
 * pour la cartographie complète de ce qui est stocké où.
 *
 * ## Ce que ce composant vide (sûr, purement du cache)
 * - Cache texte des paroles ([LyricsCacheStore]) — re-téléchargeable via LRCLIB ou
 *   re-lisible depuis le fichier `.lrc`/`.txt` voisin de l'audio.
 * - Cache disque de la WebView de recherche de paroles (pages Google visitées).
 * - Cartes de partage temporaires PNG ([ShareCardGenerator]).
 *
 * ## Ce que ce composant NE touche PAS volontairement
 * - Les pochettes personnalisées ([CoverArtStorage]) : import explicite de l'utilisateur,
 *   pas une donnée re-téléchargeable.
 * - Les fichiers `.lrc`/`.txt` posés par l'utilisateur à côté de ses fichiers audio
 *   ([com.credo.soundgroove.lyrics.LyricsFileResolver]) : ce sont ses fichiers, sur son
 *   stockage, jamais gérés par l'app.
 * - La base Room (favoris, playlists, historique) et les SharedPreferences (réglages).
 */
object StorageMaintenance {

    data class CacheBreakdown(
        val lyricsCacheBytes: Long,
        val lyricsCacheFiles: Int,
        val shareCacheBytes: Long,
        val webViewCacheBytes: Long,
        val coverOverridesBytes: Long,
        val coverOverridesFiles: Int,
    ) {
        /** Somme de ce qu'un "Vider le cache" effacerait réellement. */
        val clearableBytes: Long get() = lyricsCacheBytes + shareCacheBytes + webViewCacheBytes
    }

    fun computeBreakdown(context: Context): CacheBreakdown = CacheBreakdown(
        lyricsCacheBytes = LyricsCacheStore.sizeBytes(context),
        lyricsCacheFiles = LyricsCacheStore.fileCount(context),
        shareCacheBytes = ShareCardGenerator.shareCacheSizeBytes(context),
        webViewCacheBytes = estimateWebViewCacheSizeBytes(context),
        coverOverridesBytes = CoverArtStorage.sizeBytes(context),
        coverOverridesFiles = CoverArtStorage.fileCount(context),
    )

    /**
     * Vide tout le stockage "cache" (paroles + partage + WebView).
     * Ne supprime jamais les pochettes personnalisées ni les fichiers audio/lrc de l'utilisateur.
     */
    fun clearClearableCaches(context: Context) {
        LyricsCacheStore.clearAll(context)
        ShareCardGenerator.clearShareCache(context)
        clearWebViewCache(context)
    }

    private fun clearWebViewCache(context: Context) {
        runCatching {
            WebView(context).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
                destroy()
            }
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
    }

    /**
     * Estimation de la taille du dossier de données WebView (nom variable selon la
     * version d'Android/WebView : "app_webview", "org.chromium.android_webview", etc.).
     * Purement informatif pour l'écran Réglages — le vidage réel passe par les API
     * officielles [clearWebViewCache], pas par une suppression directe de fichiers.
     */
    private fun estimateWebViewCacheSizeBytes(context: Context): Long {
        val appRoot = context.filesDir.parentFile
        var total = 0L
        appRoot?.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name.contains("webview", ignoreCase = true)) {
                total += dirSizeBytes(f)
            }
        }
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isDirectory &&
                (f.name.contains("webview", ignoreCase = true) || f.name.contains("chromium", ignoreCase = true))
            ) {
                total += dirSizeBytes(f)
            }
        }
        return total
    }

    private fun dirSizeBytes(dir: File): Long = runCatching {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    /** Formatte un nombre d'octets en libellé lisible ("1,2 Mo", "340 Ko"). */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes o"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.FRANCE, "%.0f Ko", kb)
        val mb = kb / 1024.0
        return String.format(Locale.FRANCE, "%.1f Mo", mb)
    }
}
