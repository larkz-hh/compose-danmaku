package xyz.larkzhh.danmaku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the delay that lets a released entry carry on from where it was held.
 *
 * Two rules carry the feature and neither is obvious from the drawing code: holding twice adds up, because a
 * reader may stop the same entry more than once; and a release that lands before the moment the entry was
 * frozen is not a release at all but a seek, so it must leave the delay alone rather than push the entry
 * backwards along the timeline.
 */
class DanmakuHoldTest {

    @Test
    fun `an entry that was never held is not delayed`() {
        assertEquals(0L, DanmakuHold().shiftMs(1L))
    }

    @Test
    fun `releasing an entry delays it by however long it was held`() {
        val hold = DanmakuHold()

        hold.settle(id = 1L, frozenAtMs = 1_000L, releasedAtMs = 4_500L)

        assertEquals(3_500L, hold.shiftMs(1L))
    }

    @Test
    fun `holding the same entry twice adds up`() {
        val hold = DanmakuHold()

        hold.settle(id = 1L, frozenAtMs = 1_000L, releasedAtMs = 2_000L)
        hold.settle(id = 1L, frozenAtMs = 9_000L, releasedAtMs = 9_500L)

        assertEquals(1_500L, hold.shiftMs(1L))
    }

    @Test
    fun `delays are kept per entry`() {
        val hold = DanmakuHold()

        hold.settle(id = 1L, frozenAtMs = 0L, releasedAtMs = 1_000L)
        hold.settle(id = 2L, frozenAtMs = 0L, releasedAtMs = 250L)

        assertEquals(1_000L, hold.shiftMs(1L))
        assertEquals(250L, hold.shiftMs(2L))
        assertEquals(0L, hold.shiftMs(3L))
    }

    @Test
    fun `a release that lands before the entry was frozen leaves it alone`() {
        val hold = DanmakuHold()
        hold.settle(id = 1L, frozenAtMs = 5_000L, releasedAtMs = 6_000L)

        hold.settle(id = 1L, frozenAtMs = 5_000L, releasedAtMs = 2_000L)

        assertEquals(1_000L, hold.shiftMs(1L))
    }

    @Test
    fun `retaining an entry set drops every other delay`() {
        val hold = DanmakuHold()
        hold.settle(id = 1L, frozenAtMs = 0L, releasedAtMs = 1_000L)
        hold.settle(id = 2L, frozenAtMs = 0L, releasedAtMs = 1_000L)

        hold.retainOnly(setOf(2L))

        assertEquals(1, hold.size)
        assertEquals(0L, hold.shiftMs(1L))
        assertEquals(1_000L, hold.shiftMs(2L))
    }

    @Test
    fun `clearing forgets every delay`() {
        val hold = DanmakuHold()
        hold.settle(id = 1L, frozenAtMs = 0L, releasedAtMs = 1_000L)

        hold.clear()

        assertEquals(0, hold.size)
        assertEquals(0L, hold.shiftMs(1L))
    }
}
