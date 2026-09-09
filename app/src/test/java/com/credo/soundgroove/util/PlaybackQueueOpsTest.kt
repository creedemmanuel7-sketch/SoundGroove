package com.credo.soundgroove.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueOpsTest {

    @Test
    fun move_reordersWithoutTouchingPlayer() {
        val queue = listOf(10L, 20L, 30L, 40L)
        assertEquals(listOf(10L, 30L, 20L, 40L), PlaybackQueueOps.move(queue, from = 1, to = 2))
        assertEquals(queue, PlaybackQueueOps.move(queue, from = 1, to = 1))
        assertEquals(queue, PlaybackQueueOps.move(queue, from = -1, to = 2))
    }

    @Test
    fun removeAt_dropsIndex() {
        assertEquals(listOf(1L, 3L), PlaybackQueueOps.removeAt(listOf(1L, 2L, 3L), 1))
        assertEquals(listOf(1L, 2L, 3L), PlaybackQueueOps.removeAt(listOf(1L, 2L, 3L), 9))
    }

    @Test
    fun adjustCurrentIndexAfterRemove_whenRemovingBeforeCurrent() {
        assertEquals(1, PlaybackQueueOps.adjustCurrentIndexAfterRemove(currentIndex = 2, removedIndex = 0, newSize = 3))
    }

    @Test
    fun adjustCurrentIndexAfterRemove_whenRemovingCurrentKeepsSlot() {
        assertEquals(1, PlaybackQueueOps.adjustCurrentIndexAfterRemove(currentIndex = 1, removedIndex = 1, newSize = 3))
        assertEquals(0, PlaybackQueueOps.adjustCurrentIndexAfterRemove(currentIndex = 0, removedIndex = 0, newSize = 1))
    }

    @Test
    fun insertNext_and_append() {
        val base = listOf(1L, 2L, 3L)
        assertEquals(listOf(1L, 99L, 2L, 3L), PlaybackQueueOps.insertNext(base, currentIndex = 0, songId = 99L))
        assertEquals(listOf(1L, 2L, 3L, 7L), PlaybackQueueOps.append(base, 7L))
        assertEquals(listOf(5L), PlaybackQueueOps.insertNext(emptyList(), currentIndex = 0, songId = 5L))
    }

    @Test
    fun moveItems_worksForGenericLists() {
        val queue = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), PlaybackQueueOps.moveItems(queue, from = 1, to = 2))
        assertEquals(listOf("a", "c"), PlaybackQueueOps.removeItemAt(queue, 1))
    }
}
