package com.credo.soundgroove.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWindowOpsTest {

    @Test
    fun compute_centersWindowAndClampsEdges() {
        val mid = PlaybackWindowOps.compute(100, 200, 32)
        assertEquals(68, mid.start)
        assertEquals(133, mid.endExclusive)
        assertEquals(100, mid.logicalIndex)
        assertEquals(32, mid.playerIndex)
        assertEquals(65, mid.size())

        val head = PlaybackWindowOps.compute(0, 200, 32)
        assertEquals(0, head.start)
        assertEquals(33, head.endExclusive)
        assertEquals(0, head.playerIndex)

        val tail = PlaybackWindowOps.compute(199, 200, 32)
        assertEquals(167, tail.start)
        assertEquals(200, tail.endExclusive)
        assertEquals(32, tail.playerIndex)
    }

    @Test
    fun mapping_logicalAndPlayerAreInverses() {
        val window = PlaybackWindowOps.compute(50, 400, 32)
        assertEquals(32, window.toPlayerIndex(50))
        assertEquals(50, window.toLogicalIndex(32))
        assertEquals(-1, window.toPlayerIndex(10))
        assertEquals(50, PlaybackWindowOps.logicalIndexFromPlayer(window, 32))
        assertEquals(32, PlaybackWindowOps.playerIndexForLogical(window, 50))
    }

    @Test
    fun shouldReExpand_usesHysteresisAndSkipsLibraryEdges() {
        val window = PlaybackWindowOps.compute(100, 400, 32)
        assertFalse(PlaybackWindowOps.shouldReExpand(window, 100, 400, 32, 8))
        assertFalse(PlaybackWindowOps.shouldReExpand(window, 95, 400, 32, 8))
        assertTrue(PlaybackWindowOps.shouldReExpand(window, 68, 400, 32, 8))
        assertTrue(PlaybackWindowOps.shouldReExpand(window, 132, 400, 32, 8))

        val head = PlaybackWindowOps.compute(4, 400, 32)
        assertFalse(PlaybackWindowOps.shouldReExpand(head, 1, 400, 32, 8))

        val tail = PlaybackWindowOps.compute(396, 400, 32)
        assertFalse(PlaybackWindowOps.shouldReExpand(tail, 399, 400, 32, 8))
    }

    @Test
    fun canFastSeekWindow_isO1FingerprintNotFullScan() {
        val ids = (0 until 500).map { "id-$it" }
        val window = PlaybackWindowOps.compute(100, ids.size, 32)
        val first = ids[window.start]
        val last = ids[window.endExclusive - 1]
        val current = ids[100]
        assertTrue(
            PlaybackWindowOps.canFastSeekWindow(
                window.size(),
                first,
                last,
                current,
                window.playerIndex,
                ids,
                100,
                32,
            ),
        )
        assertFalse(
            PlaybackWindowOps.canFastSeekWindow(
                window.size(),
                first,
                last,
                current,
                0,
                ids,
                100,
                32,
            ),
        )
        assertFalse(
            PlaybackWindowOps.canFastSeekWindow(
                500,
                ids.first(),
                ids.last(),
                current,
                100,
                ids,
                100,
                32,
            ),
        )
    }

    @Test
    fun resolveLogicalIndex_prefersHintThenWindow() {
        val ids = listOf("a", "b", "c", "b", "d")
        assertEquals(3, PlaybackWindowOps.resolveLogicalIndex(ids, "b", 3))
        assertEquals(1, PlaybackWindowOps.resolveLogicalIndex(ids, "b", 1))
        assertEquals(4, PlaybackWindowOps.resolveLogicalIndex(ids, "d", 0))
        assertEquals(2, PlaybackWindowOps.resolveLogicalIndex(ids, "missing", 2))
    }

    @Test
    fun isPlayerHoldingWindow_checksBoundsNotEveryItem() {
        val ids = (0 until 200).map { "id-$it" }
        val window = PlaybackWindowOps.compute(80, ids.size, 32)
        assertTrue(
            PlaybackWindowOps.isPlayerHoldingWindow(
                window.size(),
                ids[window.start],
                ids[window.endExclusive - 1],
                ids,
                window,
            ),
        )
        assertFalse(
            PlaybackWindowOps.isPlayerHoldingWindow(
                window.size(),
                "wrong",
                ids[window.endExclusive - 1],
                ids,
                window,
            ),
        )
    }
}
