package xyz.larkzhh.danmaku.engine

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import xyz.larkzhh.danmaku.DanmakuItem
import xyz.larkzhh.danmaku.DanmakuStyle

/**
 * An entry that has been measured once and given a lane.
 *
 * Holding the measured [TextLayoutResult] is what keeps the frame loop free of measurement: the layout
 * survives every frame and is only recomputed when the entry list or the layer width changes.
 */
internal class PlacedDanmaku(
    val item: DanmakuItem,
    val textLayout: TextLayoutResult,
    val widthPx: Float,
    val lane: Int,
    private val durationMillis: Long,
) {
    /**
     * Fraction of the travel completed at [nowMs].
     *
     * Below `0f` before the entry enters and above `1f` after it has left, which is how the frame loop
     * decides whether the entry is on screen at all.
     */
    fun progressAt(nowMs: Long): Float = (nowMs - item.timeMs).toFloat() / durationMillis

    /** Left edge of the entry at [progress], in pixels. It travels the layer width plus its own width. */
    fun xAt(progress: Float, containerWidthPx: Float): Float =
        containerWidthPx - progress * (containerWidthPx + widthPx)

    /** Top edge of the text, vertically centred inside its lane. */
    fun topLeftY(laneHeightPx: Float): Float =
        lane * laneHeightPx + (laneHeightPx - textLayout.size.height) / 2f
}

/**
 * Measures [items] and assigns each of them a lane.
 *
 * Entries are sorted by [DanmakuItem.timeMs] first, because [LaneAllocator] walks them in ascending
 * time order. Measurement happens here and only here.
 *
 * @param items Entries to place, in any order.
 * @param containerWidthPx Current width of the layer in pixels.
 * @param gapPx Minimum horizontal gap between two entries sharing a lane, in pixels.
 * @param measurer Measurer used to lay the text out.
 * @param style Style supplying the lane count, the duration and the text style to measure with.
 * @return The entries ready to draw, ordered by start time.
 */
internal fun placeDanmaku(
    items: List<DanmakuItem>,
    containerWidthPx: Float,
    gapPx: Float,
    measurer: TextMeasurer,
    style: DanmakuStyle,
): List<PlacedDanmaku> {
    if (items.isEmpty()) return emptyList()

    val sorted = items.sortedBy { it.timeMs }
    val layouts = sorted.map { item ->
        measurer.measure(
            text = item.text,
            style = style.textStyle.copy(color = item.color),
            maxLines = 1,
            softWrap = false,
        )
    }
    val widthsPx = FloatArray(sorted.size) { layouts[it].size.width.toFloat() }
    val lanes = LaneAllocator.allocate(
        timesMs = LongArray(sorted.size) { sorted[it].timeMs },
        widthsPx = widthsPx,
        laneCount = style.laneCount,
        containerWidthPx = containerWidthPx,
        durationMillis = style.durationMillis,
        gapPx = gapPx,
    )

    return sorted.mapIndexed { index, item ->
        PlacedDanmaku(
            item = item,
            textLayout = layouts[index],
            widthPx = widthsPx[index],
            lane = lanes[index],
            durationMillis = style.durationMillis,
        )
    }
}
