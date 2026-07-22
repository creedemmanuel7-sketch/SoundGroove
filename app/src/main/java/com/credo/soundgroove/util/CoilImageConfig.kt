package com.credo.soundgroove.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

/**
 * Configuration Coil partagée : limite mémoire + disque pour des listes longues fluides.
 */
object CoilImageConfig {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val loader = ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("coil_album_art"))
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
        coil.Coil.setImageLoader(loader)
    }
}
