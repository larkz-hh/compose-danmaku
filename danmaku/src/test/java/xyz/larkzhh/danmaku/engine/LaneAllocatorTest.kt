package xyz.larkzhh.danmaku.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.larkzhh.danmaku.DanmakuOverflowPolicy

/**
 * Tests for [LaneAllocator].
 *
 * The allocator is pure maths, so everything here runs on the JVM without a device. The numbers are
 * chosen so each case isolates one rule: reuse, width, gap, the saturated fallback, the on-screen bound and
 * the overflow policy.
 */
class LaneAllocatorTest {

    private companion object {
        const val CONTAINER_WIDTH_PX = 1_000f
        const val DURATION_MS = 8_000L
        const val LANE_COUNT = 3
    }

    private fun allocate(
        timesMs: LongArray,
        widthsPx: FloatArray,
        laneCount: Int = LANE_COUNT,
        containerWidthPx: Float = CONTAINER_WIDTH_PX,
        durationMillis: Long = DURATION_MS,
        gapPx: Float = 0f,
        maxVisible: Int = Int.MAX_VALUE,
        overflowPolicy: DanmakuOverflowPolicy = DanmakuOverflowPolicy.Overlap,
    ): IntArray = LaneAllocator.allocate(
        timesMs = timesMs,
        widthsPx = widthsPx,
        laneCount = laneCount,
        containerWidthPx = containerWidthPx,
        durationMillis = durationMillis,
        gapPx = gapPx,
        maxVisible = maxVisible,
        overflowPolicy = overflowPolicy,
    )

    @Test
    fun `entries starting together fill the lanes in order`() {
        val lanes = allocate(
            timesMs = longArrayOf(0L, 0L, 0L),
            widthsPx = floatArrayOf(100f, 100f, 100f),
        )

        assertArrayEquals(intArrayOf(0, 1, 2), lanes)
    }

    @Test
    fun `well spaced entries all reuse the first lane`() {
        val lanes = allocate(
            timesMs = longArrayOf(0L, 5_000L, 10_000L),
            widthsPx = floatArrayOf(100f, 100f, 100f),
        )

        assertArrayEquals(intArrayOf(0, 0, 0), lanes)
    }

    @Test
    fun `a lane is reused once the previous entry has cleared it`() {
        // 100 px wide at 1000 px/s of travel needs about 727 ms, well before the second entry at 2000 ms.
        val lanes = allocate(
            timesMs = longArrayOf(0L, 2_000L),
            widthsPx = floatArrayOf(100f, 100f),
        )

        assertArrayEquals(intArrayOf(0, 0), lanes)
    }

    @Test
    fun `a wider entry holds its lane longer`() {
        val narrowFirst = allocate(
            timesMs = longArrayOf(0L, 1_000L),
            widthsPx = floatArrayOf(100f, 900f),
        )
        val wideFirst = allocate(
            timesMs = longArrayOf(0L, 1_000L),
            widthsPx = floatArrayOf(900f, 100f),
        )

        // Same timeline, same widths, only swapped: the wide entry needs its lane for about 3789 ms
        // instead of 727 ms, so the follower is pushed onto the next one.
        assertArrayEquals(intArrayOf(0, 0), narrowFirst)
        assertArrayEquals(intArrayOf(0, 1), wideFirst)
    }

    @Test
    fun `the minimum gap delays reuse`() {
        val withoutGap = allocate(
            timesMs = longArrayOf(0L, 1_000L),
            widthsPx = floatArrayOf(100f, 100f),
            gapPx = 0f,
        )
        val withGap = allocate(
            timesMs = longArrayOf(0L, 1_000L),
            widthsPx = floatArrayOf(100f, 100f),
            gapPx = 100f,
        )

        // The gap pushes the clearing moment from about 727 ms to about 1454 ms, past the second entry.
        assertArrayEquals(intArrayOf(0, 0), withoutGap)
        assertArrayEquals(intArrayOf(0, 1), withGap)
    }

    @Test
    fun `when every lane is busy the one clearing earliest is taken`() {
        val lanes = allocate(
            timesMs = longArrayOf(0L, 0L, 0L, 10L),
            widthsPx = floatArrayOf(1_000f, 500f, 200f, 100f),
        )

        // The three entries clear at about 4000 ms, 2667 ms and 1333 ms, so the fourth takes lane 2.
        assertArrayEquals(intArrayOf(0, 1, 2, 2), lanes)
    }

    @Test
    fun `every entry gets a lane inside the range even when saturated`() {
        val count = 200
        val lanes = allocate(
            timesMs = LongArray(count) { it * 7L },
            widthsPx = FloatArray(count) { 80f + it % 40 },
        )

        assertEquals(count, lanes.size)
        assertTrue("lanes were ${lanes.toList()}", lanes.all { it in 0 until LANE_COUNT })
    }

    @Test
    fun `an empty input produces an empty result`() {
        assertEquals(0, allocate(timesMs = longArrayOf(), widthsPx = floatArrayOf()).size)
    }

    @Test
    fun `mismatched input lengths are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            allocate(timesMs = longArrayOf(0L, 1L), widthsPx = floatArrayOf(10f))
        }
    }

    @Test
    fun `a non positive lane count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            allocate(timesMs = longArrayOf(0L), widthsPx = floatArrayOf(10f), laneCount = 0)
        }
    }

    @Test
    fun `a non positive duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            allocate(timesMs = longArrayOf(0L), widthsPx = floatArrayOf(10f), durationMillis = 0L)
        }
    }

    @Test
    fun `maxVisible bounds how many entries are on screen at once`() {
        // The first two stay on screen for a full 8000 ms, so everything arriving in between is dropped.
        val lanes = allocate(
            timesMs = longArrayOf(0L, 100L, 200L, 300L, 400L),
            widthsPx = floatArrayOf(100f, 100f, 100f, 100f, 100f),
            maxVisible = 2,
        )

        assertArrayEquals(
            intArrayOf(0, 1, LaneAllocator.DROPPED, LaneAllocator.DROPPED, LaneAllocator.DROPPED),
            lanes,
        )
    }

    @Test
    fun `entries that have left the screen make room again`() {
        // The first two come in at 0 ms and 100 ms and leave at 8000 ms and 8100 ms, so the third at 9000 ms fits.
        val lanes = allocate(
            timesMs = longArrayOf(0L, 100L, 9_000L),
            widthsPx = floatArrayOf(100f, 100f, 100f),
            maxVisible = 2,
        )

        assertArrayEquals(intArrayOf(0, 1, 0), lanes)
    }

    @Test
    fun `dropping on overflow is opt in`() {
        val times = longArrayOf(0L, 0L, 0L, 10L)
        val widths = floatArrayOf(1_000f, 500f, 200f, 100f)

        val overlap = allocate(timesMs = times, widthsPx = widths)
        val drop = allocate(
            timesMs = times,
            widthsPx = widths,
            overflowPolicy = DanmakuOverflowPolicy.Drop,
        )

        // Overlap is the default and keeps the fourth entry on the lane that clears first.
        assertArrayEquals(intArrayOf(0, 1, 2, 2), overlap)
        assertArrayEquals(intArrayOf(0, 1, 2, LaneAllocator.DROPPED), drop)
    }

    @Test
    fun `dropped entries do not count towards maxVisible`() {
        // Two lanes only, and the cap sits above the lane count, so the pair arriving after the layer is full
        // is dropped by the overflow policy while still leaving the window empty.
        val lanes = allocate(
            timesMs = longArrayOf(0L, 0L, 10L, 20L, 9_000L),
            widthsPx = floatArrayOf(1_000f, 500f, 100f, 100f, 100f),
            laneCount = 2,
            maxVisible = 4,
            overflowPolicy = DanmakuOverflowPolicy.Drop,
        )

        assertArrayEquals(
            intArrayOf(0, 1, LaneAllocator.DROPPED, LaneAllocator.DROPPED, 0),
            lanes,
        )
    }

    @Test
    fun `a non positive maxVisible is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            allocate(timesMs = longArrayOf(0L), widthsPx = floatArrayOf(10f), maxVisible = 0)
        }
    }
}
