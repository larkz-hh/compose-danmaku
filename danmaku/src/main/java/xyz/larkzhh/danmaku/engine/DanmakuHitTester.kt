package xyz.larkzhh.danmaku.engine

import androidx.compose.ui.geometry.Offset

/**
 * Finds the entry under a touch position.
 *
 * Entries are painted in list order and the pinned entry is painted last, so the search visits the pinned
 * entry first and then walks the rest backwards. A tap therefore lands on what the user sees on top.
 *
 * @param placed Entries as returned by [placeDanmaku].
 * @param position Touch position relative to the layer, in pixels.
 * @param nowMs Current playback position, used to locate the moving entries.
 * @param containerWidthPx Width of the layer in pixels.
 * @param laneHeightPx Height of one lane in pixels.
 * @param touchPaddingPx How far the tap target extends past the text on every side.
 * @param pinnedId Id of the entry currently pinned by a selection, if any.
 * @param pinnedMs The moment [pinnedId] is pinned at. The pinned entry is tested there instead of at
 *   [nowMs], because that is where it is drawn.
 * @param shiftMsOf How far the drawing of an entry is delayed by [DanmakuHold]. Tested against the same
 *   reading the entry is drawn at, or a tap would land somewhere the entry is not.
 * @return The touched entry, or `null` when the touch missed every visible entry.
 */
internal fun hitTest(
    placed: List<PlacedDanmaku>,
    position: Offset,
    nowMs: Long,
    containerWidthPx: Float,
    laneHeightPx: Float,
    touchPaddingPx: Float,
    pinnedId: Long? = null,
    pinnedMs: Long = 0L,
    shiftMsOf: (Long) -> Long = { 0L },
): PlacedDanmaku? {
    val pinned = pinnedId?.let { id -> placed.firstOrNull { it.item.id == id } }
    val pinnedAt = pinnedMs - (pinned?.let { shiftMsOf(it.item.id) } ?: 0L)
    if (pinned != null && pinned.contains(position, pinnedAt, containerWidthPx, laneHeightPx, touchPaddingPx)) {
        return pinned
    }
    for (index in placed.indices.reversed()) {
        val entry = placed[index]
        if (entry === pinned) continue
        val at = nowMs - shiftMsOf(entry.item.id)
        if (entry.contains(position, at, containerWidthPx, laneHeightPx, touchPaddingPx)) return entry
    }
    return null
}

/// Whether this entry covers [position] at [timeMs], allowing for the touch padding.
private fun PlacedDanmaku.contains(
    position: Offset,
    timeMs: Long,
    containerWidthPx: Float,
    laneHeightPx: Float,
    touchPaddingPx: Float,
): Boolean {
    val progress = progressAt(timeMs)
    if (progress !in 0f..1f) return false

    val left = xAt(progress, containerWidthPx)
    val top = lane * laneHeightPx
    val insideX = position.x in (left - touchPaddingPx)..(left + widthPx + touchPaddingPx)
    val insideY = position.y in (top - touchPaddingPx)..(top + laneHeightPx + touchPaddingPx)
    return insideX && insideY
}