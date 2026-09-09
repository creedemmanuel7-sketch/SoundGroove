package com.credo.soundgroove.util

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGuardsErrorMessageTest {

    @Test
    fun fileNotFound_hasClearFrenchMessage() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            "ERROR_CODE_IO_FILE_NOT_FOUND",
        )
        assertTrue(msg.contains("introuvable", ignoreCase = true))
    }

    @Test
    fun networkTimeout_mentionsConnexion() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
        )
        assertTrue(msg.contains("Connexion", ignoreCase = true))
    }

    @Test
    fun decoderFailure_mentionsFormat() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            "ERROR_CODE_DECODING_FAILED",
        )
        assertTrue(msg.contains("Format", ignoreCase = true) || msg.contains("supporté", ignoreCase = true))
    }

    @Test
    fun malformedContainer_mentionsCorrompu() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            "ERROR_CODE_PARSING_CONTAINER_MALFORMED",
        )
        assertTrue(msg.contains("corrompu", ignoreCase = true))
    }

    @Test
    fun unsupportedManifest_mentionsCorrompu() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED",
        )
        assertTrue(msg.contains("corrompu", ignoreCase = true))
    }

    @Test
    fun permissionDenied_mentionsPermission() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            "ERROR_CODE_IO_NO_PERMISSION",
        )
        assertTrue(msg.contains("Permission", ignoreCase = true))
    }

    @Test
    fun unknownCode_includesErrorName() {
        val msg = PlayerGuards.userMessageForPlaybackError(
            PlaybackException.ERROR_CODE_UNSPECIFIED,
            "ERROR_CODE_UNSPECIFIED",
        )
        assertEquals("Erreur de lecture (ERROR_CODE_UNSPECIFIED)", msg)
    }
}
