package com.credo.soundgroove.util

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import android.net.Uri
import com.credo.soundgroove.Song

/** Garde-fous Media3 pour éviter les crashs (index invalides, file vide, controller absent). */
object PlayerGuards {

    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    fun safeSeekToNext(player: Player?): Boolean {
        if (player == null || player.mediaItemCount <= 0) return false
        return try {
            if (!player.hasNextMediaItem()) {
                // REPEAT_MODE_ALL : seekToNext gère le wrap ; sinon no-op.
                if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                    player.seekToNextMediaItem()
                    true
                } else {
                    false
                }
            } else {
                // hasNextMediaItem / seekToNext respectent shuffleModeEnabled.
                player.seekToNextMediaItem()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Skip suivant en ignorant les [invalidMediaIds] déjà en échec (évite boucle
     * sur un fichier corrompu voisin).
     */
    fun safeSeekToNextSkippingInvalid(
        player: Player?,
        invalidMediaIds: Set<String>,
        maxAttempts: Int = 8,
    ): Boolean {
        if (player == null || player.mediaItemCount <= 0) return false
        if (invalidMediaIds.isEmpty()) return safeSeekToNext(player)
        return try {
            var attempts = 0
            while (attempts < maxAttempts) {
                if (!safeSeekToNext(player)) return false
                attempts++
                val id = player.currentMediaItem?.mediaId
                if (id == null || id !in invalidMediaIds) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun safeSeekToPrevious(player: Player?): Boolean {
        if (player == null || player.mediaItemCount <= 0) return false
        return try {
            if (player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                player.seekTo(0)
                true
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                true
            } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 1) {
                player.seekTo(player.mediaItemCount - 1, 0L)
                true
            } else {
                player.seekTo(0)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Applique un delta net de skip (positifs = next, négatifs = previous).
     * Utilisé après coalesce de taps rapides — un seul seek, pas N transitions.
     *
     * **Important** : marche la timeline via [androidx.media3.common.Timeline.getNextWindowIndex]
     * / [getPreviousWindowIndex] pour respecter `shuffleModeEnabled` (l’arithmétique
     * linéaire `current + delta` cassait le mode aléatoire).
     */
    fun applySkipDelta(player: Player?, delta: Int): Boolean {
        if (player == null || player.mediaItemCount <= 0 || delta == 0) return false
        return try {
            val timeline = player.currentTimeline
            if (timeline.isEmpty) return false
            val current = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
            // Skip utilisateur sort de REPEAT_ONE (sinon next resterait sur la même fenêtre).
            val repeatForSkip = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> player.repeatMode
            }
            val shuffle = player.shuffleModeEnabled

            when {
                delta > 0 -> {
                    var target = current
                    repeat(delta) {
                        val next = timeline.getNextWindowIndex(target, repeatForSkip, shuffle)
                        if (next == C.INDEX_UNSET) return@repeat
                        target = next
                    }
                    if (target == current) return false
                    player.seekToDefaultPosition(target)
                    player.playWhenReady = true
                    true
                }
                else -> {
                    // Premier previous : si > 3 s dans le titre, restart uniquement (delta=-1).
                    if (delta == -1 && player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                        player.seekTo(0)
                        return true
                    }
                    var steps = -delta
                    // Si on était > seuil, le premier « previous » a déjà restarté ;
                    // pour delta < -1 (coalesce), on saute aussi des pistes.
                    if (player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                        steps -= 1
                        if (steps <= 0) {
                            player.seekTo(0)
                            return true
                        }
                    }
                    var target = current
                    repeat(steps) {
                        val prev = timeline.getPreviousWindowIndex(target, repeatForSkip, shuffle)
                        if (prev == C.INDEX_UNSET) return@repeat
                        target = prev
                    }
                    if (target == current && player.currentPosition <= PREVIOUS_RESTART_THRESHOLD_MS) {
                        return false
                    }
                    player.seekToDefaultPosition(target)
                    player.playWhenReady = true
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun safeSeekToIndex(player: Player?, index: Int): Boolean {
        if (player == null || player.mediaItemCount <= 0 || index !in 0 until player.mediaItemCount) {
            return false
        }
        return try {
            player.seekToDefaultPosition(index)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Seek robuste utilisé par l'écran Paroles (tap sur une ligne synchronisée) :
     * protège contre un player sans média chargé et borne la position à la durée
     * connue pour éviter un seek hors plage sur certains décodeurs.
     */
    fun safeSeekToPosition(player: Player?, positionMs: Long): Boolean {
        if (player == null || player.mediaItemCount <= 0) return false
        return try {
            val duration = player.duration
            val clamped = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            player.seekTo(clamped)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun safeRemoveMediaItem(player: Player?, index: Int): Boolean {
        if (player == null || index !in 0 until player.mediaItemCount) return false
        return try {
            player.removeMediaItem(index)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun safeMoveMediaItem(player: Player?, from: Int, to: Int): Boolean {
        if (player == null || player.mediaItemCount <= 0) return false
        if (from !in 0 until player.mediaItemCount || to !in 0 until player.mediaItemCount || from == to) {
            return false
        }
        return try {
            player.moveMediaItem(from, to)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Bascule [Player.shuffleModeEnabled]. Autorisé même file courte / mono-piste
     * (fast-start) : le flag reste actif quand la file s’étend.
     * @return nouvel état, ou `null` si le player refuse la commande.
     */
    fun safeToggleShuffle(player: Player?): Boolean? {
        if (player == null) return null
        return try {
            if (!player.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) return null
            val next = !player.shuffleModeEnabled
            player.shuffleModeEnabled = next
            next
        } catch (_: Exception) {
            null
        }
    }

    /** Cycle OFF → ALL → ONE → OFF. Retourne le nouveau mode. */
    fun safeCycleRepeat(player: Player?): Int {
        if (player == null) return Player.REPEAT_MODE_OFF
        return try {
            if (!player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
                return player.repeatMode
            }
            val next = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            player.repeatMode = next
            next
        } catch (_: Exception) {
            Player.REPEAT_MODE_OFF
        }
    }

    fun resolveSongFromMediaItem(item: MediaItem, songs: List<Song>): Song? {
        val mediaId = item.mediaId
        return songs.find { it.uri.toString() == mediaId }
            ?: item.localConfiguration?.uri?.let { uri -> songs.find { it.uri == uri } }
    }

    fun rebuildPlaylistFromPlayer(player: Player, songs: List<Song>): List<Song> {
        if (player.mediaItemCount == 0) return emptyList()
        val byMediaId = HashMap<String, Song>(songs.size)
        val byUri = HashMap<Uri, Song>(songs.size)
        for (song in songs) {
            byMediaId[song.uri.toString()] = song
            byUri[song.uri] = song
        }
        return buildList(player.mediaItemCount) {
            for (index in 0 until player.mediaItemCount) {
                val item = player.getMediaItemAt(index)
                val resolved = byMediaId[item.mediaId]
                    ?: item.localConfiguration?.uri?.let { uri -> byUri[uri] }
                if (resolved != null) add(resolved)
            }
        }
    }

    fun safeCurrentIndex(player: Player): Int =
        if (player.mediaItemCount <= 0) 0
        else player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)

    fun userMessageForPlaybackError(errorCode: Int, errorCodeName: String): String {
        return when (errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            -> "Fichier audio introuvable ou illisible"

            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            -> "Permission refusée pour ce fichier"

            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            -> "Connexion indisponible"

            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> "Format audio non supporté"

            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> "Fichier audio corrompu"

            else -> "Erreur de lecture ($errorCodeName)"
        }
    }
}
