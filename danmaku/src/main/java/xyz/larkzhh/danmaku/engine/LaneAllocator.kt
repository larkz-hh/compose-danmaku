package xyz.larkzhh.danmaku.engine

import xyz.larkzhh.danmaku.DanmakuOverflowPolicy

/**
 * Spreads entries over a fixed number of lanes so that entries sharing a lane never overlap.
 *
 * A pure function over pre-measured widths: text measurement stays out of the maths, and the result can
 * be verified on the JVM without a device.
 *
 * A lane counts as free again once the tail of the entry last assigned to it has cleared the right edge
 * of the layer, which happens `durationMillis * (width + gap) / (containerWidth + width)` after that entry
 * entered.
 *
 * Two rules can keep an entry off the screen, and they answer different questions:
 * - [maxVisible] bounds how many entries may be on screen at once. An entry arriving while the layer is
 *   already full is dropped, and being dropped never counts towards the bound itself.
 * - When every lane is still busy but the layer is not full, [overflowPolicy] decides between drawing on
 *   the lane that clears first and dropping the entry.
 */
internal object LaneAllocator {

    /** Lane index reported for an entry that was dropped instead of placed. */
    const val DROPPED: Int = -1

    /**
     * Assigns a lane to every entry, or drops it.
     *
     * @param timesMs Entry start times in milliseconds. Must be ascending; entries are processed in this
     *   order.
     * @param widthsPx Measured width of each entry in pixels, aligned with [timesMs].
     * @param laneCount Number of lanes to spread the entries over.
     * @param containerWidthPx Width of the layer in pixels.
     * @param durationMillis Time one entry needs to travel from the right edge until its tail leaves the
     *   left edge.
     * @param gapPx Minimum horizontal gap kept between two entries sharing a lane.
     * @param maxVisible Upper bound on how many entries may be on screen at the same moment.
     * @param overflowPolicy What to do with an entry that arrives while every lane is busy.
     * @return The lane index of each entry, aligned with the input order, or [DROPPED] for an entry that
     *   was not placed.
     * @throws IllegalArgumentException if the two arrays differ in length, or if [laneCount],
     *   [durationMillis] or [maxVisible] is not positive.
     */
    fun allocate(
        timesMs: LongArray,
        widthsPx: FloatArray,
        laneCount: Int,
        containerWidthPx: Float,
        durationMillis: Long,
        gapPx: Float,
        maxVisible: Int = Int.MAX_VALUE,
        overflowPolicy: DanmakuOverflowPolicy = DanmakuOverflowPolicy.Overlap,
    ): IntArray {
        require(timesMs.size == widthsPx.size) {
            "timesMs and widthsPx must have the same size, were ${timesMs.size} and ${widthsPx.size}"
        }
        require(laneCount > 0) { "laneCount must be positive, was $laneCount" }
        require(durationMillis > 0L) { "durationMillis must be positive, was $durationMillis" }
        require(maxVisible > 0) { "maxVisible must be positive, was $maxVisible" }

        // The moment each lane becomes usable again. Negative infinity means "never used yet".
        val laneFreeAt = FloatArray(laneCount) { Float.NEGATIVE_INFINITY }
        val lanes = IntArray(timesMs.size) { DROPPED }

        // Start times of the entries on screen at the entry being placed, in ascending order. A dropped
        // entry never enters this window, so it does not count towards maxVisible either.
        val onScreenAt = LongArray(timesMs.size)
        var windowStart = 0
        var windowEnd = 0

        for (index in timesMs.indices) {
            val enterAt = timesMs[index]

            // An entry leaves the screen once its tail has crossed the left edge, one duration after it came in.
            while (windowStart < windowEnd && onScreenAt[windowStart] + durationMillis <= enterAt) {
                windowStart++
            }
            if (windowEnd - windowStart >= maxVisible) continue

            val width = widthsPx[index]
            val travel = containerWidthPx + width
            val clearsAt = if (travel > 0f) {
                enterAt + durationMillis * (width + gapPx) / travel
            } else {
                // Degenerate layer with no width: nothing can overlap, so the lane frees immediately.
                enterAt.toFloat()
            }

            val freeLane = laneFreeAt.indexOfFirst { it <= enterAt }
            val lane = if (freeLane >= 0) {
                freeLane
            } else {
                if (overflowPolicy == DanmakuOverflowPolicy.Drop) continue
                laneFreeAt.indices.minBy { laneFreeAt[it] }
            }

            laneFreeAt[lane] = clearsAt
            lanes[index] = lane
            onScreenAt[windowEnd] = enterAt
            windowEnd++
        }
        return lanes
    }
}