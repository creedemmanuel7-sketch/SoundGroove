package com.credo.soundgroove.remote

/**
 * Contrat protocole remote v0 (JSON / WebSocket) — miroir desktop
 * `desktop/.../remote/RemoteProtocol.kt` et `docs/desktop/phase-1-spec.md`.
 *
 * Host = téléphone Android · Client = desktop.
 * Garder les deux fichiers synchronisés jusqu’à extraction `:shared`.
 */
object RemoteProtocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 3847
    const val PIN_TTL_MS = 2 * 60 * 1000L
}

enum class RemoteCommandAction {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    SEEK,
    SET_VOLUME,
    ;

    fun wireName(): String = when (this) {
        PLAY -> "play"
        PAUSE -> "pause"
        NEXT -> "next"
        PREVIOUS -> "previous"
        SEEK -> "seek"
        SET_VOLUME -> "setVolume"
    }

    companion object {
        fun fromWire(value: String): RemoteCommandAction? = when (value) {
            "play" -> PLAY
            "pause" -> PAUSE
            "next" -> NEXT
            "previous" -> PREVIOUS
            "seek" -> SEEK
            "setVolume" -> SET_VOLUME
            else -> null
        }
    }
}

data class RemoteSongState(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artUrl: String? = null,
)

data class RemotePlaybackState(
    val song: RemoteSongState?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val volume: Float,
    val queueSize: Int,
)

sealed interface RemoteClientMessage {
    data class Pair(val pin: String) : RemoteClientMessage
    data class Auth(val token: String) : RemoteClientMessage
    data class Subscribe(val token: String) : RemoteClientMessage
    data class Command(
        val token: String,
        val action: RemoteCommandAction,
        val positionMs: Long? = null,
        val volume: Float? = null,
    ) : RemoteClientMessage
}

sealed interface RemoteHostMessage {
    data class PairResult(val ok: Boolean, val token: String?) : RemoteHostMessage
    data class State(val state: RemotePlaybackState) : RemoteHostMessage
    data class Error(val code: String, val message: String) : RemoteHostMessage
}

object RemoteJson {
    fun encodePair(pin: String): String =
        """{"v":${RemoteProtocol.VERSION},"type":"pair","pin":"${esc(pin)}"}"""

    fun encodeAuth(token: String): String =
        """{"v":${RemoteProtocol.VERSION},"type":"auth","token":"${esc(token)}"}"""

    fun encodeSubscribe(token: String): String =
        """{"v":${RemoteProtocol.VERSION},"type":"subscribe","token":"${esc(token)}"}"""

    fun encodeCommand(
        token: String,
        action: RemoteCommandAction,
        positionMs: Long? = null,
        volume: Float? = null,
    ): String {
        val payload = buildString {
            append('{')
            var first = true
            if (positionMs != null) {
                append("\"positionMs\":").append(positionMs)
                first = false
            }
            if (volume != null) {
                if (!first) append(',')
                append("\"volume\":").append(volume)
            }
            append('}')
        }
        return """{"v":${RemoteProtocol.VERSION},"type":"command","token":"${esc(token)}","action":"${action.wireName()}","payload":$payload}"""
    }

    fun encodePairResult(ok: Boolean, token: String?): String {
        val tokenPart = if (token != null) ",\"token\":\"${esc(token)}\"" else ",\"token\":null"
        return """{"v":${RemoteProtocol.VERSION},"type":"pairResult","ok":$ok$tokenPart}"""
    }

    fun encodeError(code: String, message: String): String =
        """{"v":${RemoteProtocol.VERSION},"type":"error","code":"${esc(code)}","message":"${esc(message)}"}"""

    fun encodeState(state: RemotePlaybackState): String {
        val songJson = state.song?.let { s ->
            val art = s.artUrl?.let { "\"${esc(it)}\"" } ?: "null"
            """{"id":"${esc(s.id)}","title":"${esc(s.title)}","artist":"${esc(s.artist)}","album":"${esc(s.album)}","durationMs":${s.durationMs},"artUrl":$art}"""
        } ?: "null"
        return """{"v":${RemoteProtocol.VERSION},"type":"state","song":$songJson,"isPlaying":${state.isPlaying},"positionMs":${state.positionMs},"volume":${state.volume},"queueSize":${state.queueSize}}"""
    }

    fun parseHostMessage(raw: String): RemoteHostMessage? {
        val type = extractString(raw, "type") ?: return null
        return when (type) {
            "pairResult" -> {
                val ok = extractBoolean(raw, "ok") == true
                val token = extractString(raw, "token")?.takeIf { it.isNotBlank() && it != "null" }
                RemoteHostMessage.PairResult(ok = ok, token = token)
            }
            "state" -> RemoteHostMessage.State(parsePlaybackState(raw))
            "error" -> RemoteHostMessage.Error(
                code = extractString(raw, "code") ?: "UNSUPPORTED",
                message = extractString(raw, "message") ?: "",
            )
            else -> null
        }
    }

    fun parseClientMessage(raw: String): RemoteClientMessage? {
        val type = extractString(raw, "type") ?: return null
        return when (type) {
            "pair" -> RemoteClientMessage.Pair(extractString(raw, "pin").orEmpty())
            "auth" -> RemoteClientMessage.Auth(extractString(raw, "token").orEmpty())
            "subscribe" -> RemoteClientMessage.Subscribe(extractString(raw, "token").orEmpty())
            "command" -> {
                val action = RemoteCommandAction.fromWire(extractString(raw, "action").orEmpty())
                    ?: return null
                RemoteClientMessage.Command(
                    token = extractString(raw, "token").orEmpty(),
                    action = action,
                    positionMs = extractLong(raw, "positionMs"),
                    volume = extractFloat(raw, "volume"),
                )
            }
            else -> null
        }
    }

    private fun parsePlaybackState(raw: String): RemotePlaybackState {
        val songBlock = extractObject(raw, "song")
        val song = if (songBlock == null || songBlock == "null") {
            null
        } else {
            RemoteSongState(
                id = extractString(songBlock, "id").orEmpty(),
                title = extractString(songBlock, "title").orEmpty(),
                artist = extractString(songBlock, "artist").orEmpty(),
                album = extractString(songBlock, "album").orEmpty(),
                durationMs = extractLong(songBlock, "durationMs") ?: 0L,
                artUrl = extractString(songBlock, "artUrl")?.takeIf { it != "null" },
            )
        }
        return RemotePlaybackState(
            song = song,
            isPlaying = extractBoolean(raw, "isPlaying") == true,
            positionMs = extractLong(raw, "positionMs") ?: 0L,
            volume = extractFloat(raw, "volume") ?: 1f,
            queueSize = extractLong(raw, "queueSize")?.toInt() ?: 0,
        )
    }

    private fun esc(value: String): String =
        buildString(value.length + 8) {
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }

    private fun extractString(json: String, key: String): String? {
        val quoted = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(json)
        if (quoted != null) return unescape(quoted.groupValues[1])
        if (Regex("\"$key\"\\s*:\\s*null").containsMatchIn(json)) return null
        return null
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val m = Regex("\"$key\"\\s*:\\s*(true|false)").find(json) ?: return null
        return m.groupValues[1] == "true"
    }

    private fun extractLong(json: String, key: String): Long? {
        val m = Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    private fun extractFloat(json: String, key: String): Float? {
        val m = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }

    private fun extractObject(json: String, key: String): String? {
        val marker = Regex("\"$key\"\\s*:\\s*").find(json) ?: return null
        val start = marker.range.last + 1
        if (start >= json.length) return null
        if (json.startsWith("null", startIndex = start)) return "null"
        if (json[start] != '{') return null
        var depth = 0
        var i = start
        while (i < json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(start, i + 1)
                }
                '"' -> {
                    i++
                    while (i < json.length && json[i] != '"') {
                        if (json[i] == '\\') i++
                        i++
                    }
                }
            }
            i++
        }
        return null
    }

    private fun unescape(value: String): String =
        buildString(value.length) {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        '\\' -> append('\\')
                        '"' -> append('"')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        else -> append(value[i + 1])
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
}
