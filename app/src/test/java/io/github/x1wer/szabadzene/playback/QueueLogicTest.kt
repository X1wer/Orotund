package io.github.x1wer.szabadzene.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueLogicTest {

    private fun saved(
        ids: List<Long>,
        current: Int,
        position: Long = 12_345,
        shuffle: Boolean = false,
        order: List<Int> = emptyList(),
    ) = SavedQueue(ids, current, position, repeatMode = 2, shuffleEnabled = shuffle, shuffleOrder = order)

    private fun available(vararg ids: Long) = ids.associateWith { "T$it" }

    @Test
    fun restoresEverythingWhenAllTracksExist() {
        val r = QueueLogic.restore(saved(listOf(1, 2, 3), current = 1), available(1, 2, 3))!!
        assertEquals(listOf("T1", "T2", "T3"), r.items)
        assertEquals(1, r.startIndex)
        assertEquals(12_345L, r.startPositionMs)
    }

    @Test
    fun missingTracksAreDroppedAndIndexFollows() {
        val r = QueueLogic.restore(saved(listOf(1, 2, 3, 4), current = 3), available(1, 3, 4))!!
        assertEquals(listOf("T1", "T3", "T4"), r.items)
        assertEquals(2, r.startIndex)
        assertEquals(12_345L, r.startPositionMs)
    }

    @Test
    fun missingCurrentTrackContinuesWithNextFromStart() {
        val r = QueueLogic.restore(saved(listOf(1, 2, 3), current = 1), available(1, 3))!!
        assertEquals(listOf("T1", "T3"), r.items)
        assertEquals(1, r.startIndex) // a 3-as szám
        assertEquals(0L, r.startPositionMs)
    }

    @Test
    fun missingLastTrackWrapsToFirstAvailable() {
        val r = QueueLogic.restore(saved(listOf(1, 2, 3), current = 2), available(1, 2))!!
        assertEquals(0, r.startIndex)
        assertEquals(0L, r.startPositionMs)
    }

    @Test
    fun missingCurrentTrackUsesShuffleOrderWhenShuffling() {
        // Kevert sorrend: 2 → 0 → 3 → 1; az aktuális (0) eltűnt, a kevert sorrendben a 3-as jön.
        val r = QueueLogic.restore(
            saved(listOf(10, 11, 12, 13), current = 0, shuffle = true, order = listOf(2, 0, 3, 1)),
            available(11, 12, 13),
        )!!
        assertEquals(listOf("T11", "T12", "T13"), r.items)
        assertEquals(2, r.startIndex) // T13 az új listában
        assertEquals(listOf(1, 2, 0), r.shuffleOrder) // 2→1, 3→2, 1→0
    }

    @Test
    fun shuffleOrderIsKeptAndRemapped() {
        val r = QueueLogic.restore(
            saved(listOf(1, 2, 3, 4), current = 0, shuffle = true, order = listOf(3, 1, 0, 2)),
            available(1, 2, 3, 4),
        )!!
        assertEquals(listOf(3, 1, 0, 2), r.shuffleOrder)
    }

    @Test
    fun invalidShuffleOrderIsIgnored() {
        val r = QueueLogic.restore(
            saved(listOf(1, 2, 3), current = 0, shuffle = true, order = listOf(0, 0, 1)),
            available(1, 2, 3),
        )!!
        assertNull(r.shuffleOrder)
    }

    @Test
    fun nothingAvailableMeansNothingToRestore() {
        assertNull(QueueLogic.restore(saved(listOf(1, 2), current = 0), available(7)))
    }

    @Test
    fun playNextGoesRightAfterCurrentInShuffleOrder() {
        // Eredeti kevert sorrend: 3, 0, 2, 1 (aktuális: 0); beszúrt elemek: 4, 5.
        val order = listOf(3, 0, 4, 2, 5, 1)
        assertEquals(listOf(3, 0, 4, 5, 2, 1), QueueLogic.playNextOrder(order, currentIndex = 0, inserted = listOf(4, 5)))
    }

    @Test
    fun appendGoesToEndOfShuffleOrder() {
        assertEquals(listOf(2, 0, 1, 3), QueueLogic.appendOrder(listOf(2, 3, 0, 1), inserted = listOf(3)))
    }

    @Test
    fun orderEncodingRoundTrips() {
        assertEquals("3,0,2", QueueLogic.encodeOrder(listOf(3, 0, 2)))
        assertEquals(listOf(3, 0, 2), QueueLogic.decodeOrder("3,0,2"))
        assertEquals(emptyList<Int>(), QueueLogic.decodeOrder(""))
        assertEquals(listOf(1, 2), QueueLogic.decodeOrder("1,x,2"))
    }
}
