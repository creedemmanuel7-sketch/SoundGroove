package com.credo.soundgroove.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSheetMotionTest {

    @Test
    fun dismiss_whenPastThreshold() {
        assertTrue(QueueSheetMotion.shouldDismiss(80f, 64f, 0f))
        assertFalse(QueueSheetMotion.shouldDismiss(20f, 64f, 0f))
    }

    @Test
    fun dismiss_flingNeedsPartialDrag() {
        assertTrue(QueueSheetMotion.shouldDismiss(20f, 64f, 1_200f))
        assertFalse(QueueSheetMotion.shouldDismiss(5f, 64f, 1_200f))
        assertFalse(QueueSheetMotion.shouldDismiss(20f, 64f, 200f))
    }

    @Test
    fun dragTranslation_neverNegative() {
        assertEquals(0f, QueueSheetMotion.dragTranslationY(-12f), 0.01f)
        assertEquals(40f, QueueSheetMotion.dragTranslationY(40f), 0.01f)
    }

    @Test
    fun closeProgress_clamped() {
        assertEquals(0f, QueueSheetMotion.closeProgress(-10f, 400f), 0.01f)
        assertEquals(0.5f, QueueSheetMotion.closeProgress(200f, 400f), 0.01f)
        assertEquals(1f, QueueSheetMotion.closeProgress(800f, 400f), 0.01f)
        assertEquals(0f, QueueSheetMotion.closeProgress(50f, 0f), 0.01f)
    }
}
