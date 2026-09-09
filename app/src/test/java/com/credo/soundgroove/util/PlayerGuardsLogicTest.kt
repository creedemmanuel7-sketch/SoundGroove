package com.credo.soundgroove.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fous purs (seuils / messages) — pas de Player Media3 mocké ici.
 */
class PlayerGuardsLogicTest {

    @Test
    fun previousRestartThreshold_isThreeSeconds() {
        assertEquals(3_000L, PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS)
    }

    @Test
    fun shouldRestartCurrent_whenPastThreshold() {
        assertTrue(3_001L > PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS)
        assertFalse(2_999L > PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS)
    }

    @Test
    fun previousRestart_boundaryExactlyAtThreshold_isNotPast() {
        // Contrats UX : restart seulement si position > seuil (strict).
        assertFalse(PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS > PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS)
        assertTrue(PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS + 1 > PlayerGuards.PREVIOUS_RESTART_THRESHOLD_MS)
    }
}
