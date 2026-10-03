package xyz.larkzhh.danmaku.engine

/**
 * Spreads entries over a fixed number of lanes so that entries sharing a lane never overlap.
 *
 * A pure function over pre-measured widths: text measurement stays out of the maths, and the result can
 * be verified on the JVM without a device.
 *
 * A lane counts as free again once the tail of the entry last assigned to it has cleared the right edge
 * of the layer, which happens `durationMillis * (width + gap) / (containerWidth + width)` after that
 * entry entered. When every lane is still busy the entry takes the one that clears first rather than
 * being dropped, so the layer may overlap two entries instead of silently losing one. That trade is
 * deliberate: a missing danmaku is a bug report, an overlapping one is a cosmetic problem.
 */
internal object LaneAllocator {

    /**
     * Assigns a lane to every entry.
     *
     * @param timesMs Entry start times in milliseconds. Must be ascending; entries are processed in
     *   this order.
     * @param widthsPx Measured width of each entry in pixels, aligned with [timesMs].
     * @param laneCount Number of lanes to spread the entries over.
     * @param containerWidthPx Width of the layer in pixels.
     * @param durationMillis Time one entry needs to travel from the right edge until its tail leaves the
     *   left edge.
     * @param gapPx Minimum horizontal gap kept between two entries sharing a lane.
     * @return The lane index of each entry, aligned with the input order.
     * @throws IllegalArgumentException if the two arrays differ in length, or if [laneCount] or
     *   [durationMillis] is not positive.
     */
    fun allocate(
        timesMs: LongArray,
        widthsPx: FloatArray,
        laneCount: Int,
        containerWidthPx: Float,
        durationMillis: Long,
        gapPx: Float,
    ): IntArray {
        require(timesMs.size == widthsPx.size) {
            "timesMs and widthsPx must have the same size, were ${timesMs.size} and ${widthsPx.size}"
        }
        require(laneCount > 0) { "laneCount must be positive, was $laneCount" }
        require(durationMillis > 0L) { "durationMillis must be positive, was $durationMillis" }

        // The moment each lane becomes usable again. Negative infinity means "never used yet".
        val laneFreeAt = FloatArray(laneCount) { Float.NEGATIVE_INFINITY }
        val lanes = IntArray(timesMs.size)

        for (index in timesMs.indices) {
            val enterAt = timesMs[index].toFloat()
            val width = widthsPx[index]
            val travel = containerWidthPx + width
            val clearsAt = if (travel > 0f) {
                enterAt + durationMillis * (width + gapPx) / travel
            } else {
                // Degenerate layer with no width: nothing can overlap, so the lane frees immediately.
                enterAt
            }

            val freeLane = laneFreeAt.indexOfFirst { it <= enterAt }
            val lane = if (freeLane >= 0) freeLane else laneFreeAt.indices.minBy { laneFreeAt[it] }

            laneFreeAt[lane] = clearsAt
            lanes[index] = lane
        }
        return lanes
    }
}
