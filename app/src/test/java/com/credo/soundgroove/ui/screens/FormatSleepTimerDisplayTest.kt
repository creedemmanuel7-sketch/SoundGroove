package com.credo.soundgroove.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSleepTimerDisplayTest {

    @Test
    fun null_meansNoTimer() {
        assertNull(formatSleepTimerDisplay(null))
    }

    @Test
    fun negative_meansEndOfTrack() {
        assertEquals("Fin de piste", formatSleepTimerDisplay(-1))
    }

    @Test
    fun positive_formatsRemaining() {
        assertEquals("2:05 restantes", formatSleepTimerDisplay(125))
        assertEquals("0:09 restantes", formatSleepTimerDisplay(9))
    }
}
