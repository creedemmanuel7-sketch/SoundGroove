package com.credo.soundgroove.remote

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Host WebSocket LAN — port [RemoteProtocol.DEFAULT_PORT].
 * Pairing PIN + token session ; commandes relayées via [onCommand].
 */
class RemoteHostServer(
    port: Int = RemoteProtocol.DEFAULT_PORT,
    private val onCommand: (RemoteCommandAction, positionMs: Long?, volume: Float?) -> Unit,
    private val getState: () -> RemotePlaybackState,
    private val onClientCountChanged: (Int) -> Unit = {},
) : WebSocketServer(InetSocketAddress(port)) {

    @Volatile
    private var pin: String? = null

    @Volatile
    private var pinExpiresAtMs: Long = 0L

    @Volatile
    private var sessionToken: String? = null

    private val subscribed = ConcurrentHashMap.newKeySet<WebSocket>()
    private val pinAttempts = AtomicInteger(0)

    init {
        isReuseAddr = true
        connectionLostTimeout = 30
    }

    fun currentPin(): String? = pin

    fun pinExpiresAt(): Long = pinExpiresAtMs

    /** Génère un PIN 6 chiffres (TTL [RemoteProtocol.PIN_TTL_MS]). */
    fun rotatePin(): String {
        val next = Random.nextInt(100_000, 1_000_000).toString()
        pin = next
        pinExpiresAtMs = System.currentTimeMillis() + RemoteProtocol.PIN_TTL_MS
        pinAttempts.set(0)
        // Nouveau PIN invalide l’ancienne session
        sessionToken = null
        subscribed.clear()
        onClientCountChanged(0)
        return next
    }

    fun pushState() {
        if (subscribed.isEmpty()) return
        val json = RemoteJson.encodeState(getState())
        for (socket in subscribed) {
            try {
                if (socket.isOpen) socket.send(json)
            } catch (e: Exception) {
                Log.w(TAG, "pushState failed", e)
            }
        }
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "client open ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        subscribed.remove(conn)
        onClientCountChanged(subscribed.size)
        Log.i(TAG, "client close code=$code reason=$reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val parsed = RemoteJson.parseClientMessage(message)
        if (parsed == null) {
            safeSend(conn, RemoteJson.encodeError("UNSUPPORTED", "Message invalide"))
            return
        }
        when (parsed) {
            is RemoteClientMessage.Pair -> handlePair(conn, parsed.pin)
            is RemoteClientMessage.Auth -> handleAuth(conn, parsed.token)
            is RemoteClientMessage.Subscribe -> handleSubscribe(conn, parsed.token)
            is RemoteClientMessage.Command -> handleCommand(conn, parsed)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "ws error", ex)
    }

    override fun onStart() {
        Log.i(TAG, "Remote host listening on port $port")
    }

    private fun handlePair(conn: WebSocket, offeredPin: String) {
        val expected = pin
        val now = System.currentTimeMillis()
        if (expected == null || now > pinExpiresAtMs) {
            safeSend(conn, RemoteJson.encodePairResult(ok = false, token = null))
            safeSend(conn, RemoteJson.encodeError("INVALID_PIN", "PIN expiré — régénérez sur le téléphone"))
            return
        }
        if (pinAttempts.incrementAndGet() > 8) {
            safeSend(conn, RemoteJson.encodePairResult(ok = false, token = null))
            safeSend(conn, RemoteJson.encodeError("INVALID_PIN", "Trop de tentatives"))
            return
        }
        if (offeredPin != expected) {
            safeSend(conn, RemoteJson.encodePairResult(ok = false, token = null))
            safeSend(conn, RemoteJson.encodeError("INVALID_PIN", "PIN incorrect"))
            return
        }
        val token = UUID.randomUUID().toString()
        sessionToken = token
        pinAttempts.set(0)
        // PIN à usage unique après pair réussi
        pinExpiresAtMs = 0L
        safeSend(conn, RemoteJson.encodePairResult(ok = true, token = token))
    }

    private fun handleAuth(conn: WebSocket, token: String) {
        if (!tokenValid(token)) {
            safeSend(conn, RemoteJson.encodeError("UNAUTHORIZED", "Token invalide"))
            return
        }
        // Auth seule = OK silencieux ; le client enchaîne souvent sur subscribe
        safeSend(conn, RemoteJson.encodeState(getState()))
    }

    private fun handleSubscribe(conn: WebSocket, token: String) {
        if (!tokenValid(token)) {
            safeSend(conn, RemoteJson.encodeError("UNAUTHORIZED", "Token invalide"))
            return
        }
        subscribed.add(conn)
        onClientCountChanged(subscribed.size)
        safeSend(conn, RemoteJson.encodeState(getState()))
    }

    private fun handleCommand(conn: WebSocket, command: RemoteClientMessage.Command) {
        if (!tokenValid(command.token)) {
            safeSend(conn, RemoteJson.encodeError("UNAUTHORIZED", "Token invalide"))
            return
        }
        try {
            onCommand(command.action, command.positionMs, command.volume)
        } catch (e: Exception) {
            Log.e(TAG, "command failed", e)
            safeSend(conn, RemoteJson.encodeError("UNSUPPORTED", e.message ?: "Erreur commande"))
            return
        }
        // État immédiat après commande
        safeSend(conn, RemoteJson.encodeState(getState()))
        pushState()
    }

    private fun tokenValid(token: String): Boolean {
        val current = sessionToken
        return current != null && current == token
    }

    private fun safeSend(conn: WebSocket, payload: String) {
        try {
            if (conn.isOpen) conn.send(payload)
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
        }
    }

    companion object {
        private const val TAG = "RemoteHost"
    }
}
