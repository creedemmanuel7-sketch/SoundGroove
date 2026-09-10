package com.credo.soundgroove

import android.app.Application
import android.content.Intent
import android.os.StrictMode
import android.util.Log
import com.credo.soundgroove.BuildConfig
import com.credo.soundgroove.queue.QueueFlutterRuntime

/**
 * Point d'entrée application — StrictMode **debug only** (disk/network main thread).
 * Release : aucun overhead (branches éliminées / no-op).
 * Pré-chauffe [PlaybackService] dès le process start (avant premier tap play).
 */
class SoundGrooveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Pré-bind agressif : démarre le service + ExoPlayer avant ViewModel / premier tap.
        runCatching {
            startService(Intent(this, PlaybackService::class.java))
        }.onFailure {
            Log.w("SG_AUDIO", "PlaybackService prewarm failed", it)
        }
        // FlutterEngine hors du chemin du premier play (concurrence binder / ExoPlayer).
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            QueueFlutterRuntime.prewarm(this)
        }, 4_000L)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
