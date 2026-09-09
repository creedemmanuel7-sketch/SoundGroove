package com.credo.soundgroove.queue

import android.app.Application
import android.content.Context
import android.view.View
import com.credo.soundgroove.BuildConfig

/**
 * Pont Flutter optionnel — reflection pour compiler sans le module Flutter.
 */
object QueueFlutterRuntime {
    const val ENGINE_ID = "soundgroove_queue"
    private const val BOOTSTRAP = "com.credo.soundgroove.queue.FlutterQueueBootstrap"

    fun prewarm(application: Application) {
        if (!BuildConfig.FLUTTER_QUEUE) return
        runCatching {
            Class.forName(BOOTSTRAP)
                .getMethod("prewarm", Application::class.java)
                .invoke(null, application)
        }
    }

    fun isReady(): Boolean {
        if (!BuildConfig.FLUTTER_QUEUE) return false
        return runCatching {
            Class.forName(BOOTSTRAP).getMethod("isReady").invoke(null) as Boolean
        }.getOrDefault(false)
    }

    fun publishSnapshot(json: String) {
        if (!BuildConfig.FLUTTER_QUEUE) return
        runCatching {
            Class.forName(BOOTSTRAP).getMethod("publishSnapshot", String::class.java).invoke(null, json)
        }
    }

    fun bindHandlers(
        onPlay: (Int) -> Unit,
        onRemove: (Int) -> Unit,
        onMove: (Int, Int) -> Unit,
        onClearUpcoming: () -> Unit,
        onClose: () -> Unit,
    ) {
        if (!BuildConfig.FLUTTER_QUEUE) return
        runCatching {
            Class.forName(BOOTSTRAP).getMethod(
                "bindHandlers",
                Function1::class.java,
                Function1::class.java,
                Function2::class.java,
                Function0::class.java,
                Function0::class.java,
            ).invoke(null, onPlay, onRemove, onMove, onClearUpcoming, onClose)
        }
    }

    fun createFlutterView(context: Context): View? {
        if (!isReady()) return null
        return runCatching {
            val engine = Class.forName(BOOTSTRAP).getMethod("engineOrNull").invoke(null) ?: return null
            val viewClass = Class.forName("io.flutter.embedding.android.FlutterView")
            val view = viewClass.getConstructor(Context::class.java).newInstance(context) as View
            val engineClass = Class.forName("io.flutter.embedding.engine.FlutterEngine")
            viewClass.getMethod("attachToFlutterEngine", engineClass).invoke(view, engine)
            view
        }.getOrNull()
    }

    fun detachFlutterView(view: View) {
        runCatching {
            view.javaClass.getMethod("detachFromFlutterEngine").invoke(view)
        }
    }
}
