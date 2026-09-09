package com.credo.soundgroove.queue

import android.app.Application
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pré-chauffe le FlutterEngine file d'attente (comme PlaybackService) et expose
 * MethodChannel / EventChannel vers Dart.
 */
object FlutterQueueBootstrap {
    const val METHOD_CHANNEL = "com.credo.soundgroove/queue"
    const val EVENT_CHANNEL = "com.credo.soundgroove/queue_events"

    @Volatile private var engine: FlutterEngine? = null
    @Volatile private var eventSink: EventChannel.EventSink? = null
    @Volatile private var latestSnapshot: String = "{}"

    @JvmStatic
    fun prewarm(application: Application) {
        if (engine != null) return
        runCatching {
            val created = FlutterEngine(application)
            created.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
            attachChannels(created)
            FlutterEngineCache.getInstance().put(QueueFlutterRuntime.ENGINE_ID, created)
            engine = created
        }
    }

    @JvmStatic
    fun isReady(): Boolean = engine?.dartExecutor?.isExecutingDart == true

    @JvmStatic
    fun engineOrNull(): FlutterEngine? = engine

    fun publishSnapshot(json: String) {
        latestSnapshot = json
        eventSink?.success(json)
    }

    fun bindHandlers(
        onPlay: (Int) -> Unit,
        onRemove: (Int) -> Unit,
        onMove: (Int, Int) -> Unit,
        onClearUpcoming: () -> Unit,
        onClose: () -> Unit,
    ) {
        val messenger = engine?.dartExecutor?.binaryMessenger ?: return
        MethodChannel(messenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "play" -> {
                    onPlay(call.argument<Int>("index") ?: return@setMethodCallHandler result.error("arg", "index", null))
                    result.success(null)
                }
                "remove" -> {
                    onRemove(call.argument<Int>("index") ?: return@setMethodCallHandler result.error("arg", "index", null))
                    result.success(null)
                }
                "move" -> {
                    val from = call.argument<Int>("from") ?: return@setMethodCallHandler result.error("arg", "from", null)
                    val to = call.argument<Int>("to") ?: return@setMethodCallHandler result.error("arg", "to", null)
                    onMove(from, to)
                    result.success(null)
                }
                "clearUpcoming" -> {
                    onClearUpcoming()
                    result.success(null)
                }
                "close" -> {
                    onClose()
                    result.success(null)
                }
                "latest" -> result.success(latestSnapshot)
                else -> result.notImplemented()
            }
        }
    }

    fun snapshotJson(
        history: List<Map<String, Any?>>,
        nowPlaying: Map<String, Any?>?,
        upcoming: List<Map<String, Any?>>,
        remainingLabel: String,
        isPlaying: Boolean,
        accentArgb: Long,
    ): String {
        val root = JSONObject()
        root.put("history", toArray(history))
        root.put("nowPlaying", nowPlaying?.let { JSONObject(it) } ?: JSONObject.NULL)
        root.put("upcoming", toArray(upcoming))
        root.put("remainingLabel", remainingLabel)
        root.put("isPlaying", isPlaying)
        root.put("accent", accentArgb)
        return root.toString()
    }

    private fun attachChannels(created: FlutterEngine) {
        EventChannel(created.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    events?.success(latestSnapshot)
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            },
        )
        MethodChannel(created.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "latest") result.success(latestSnapshot) else result.notImplemented()
        }
    }

    private fun toArray(rows: List<Map<String, Any?>>): JSONArray {
        val array = JSONArray()
        for (row in rows) array.put(JSONObject(row))
        return array
    }
}
