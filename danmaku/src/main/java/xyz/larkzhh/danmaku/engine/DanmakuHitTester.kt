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
): PlacedDanmaku? {
    val pinned = if (pinnedId == null) null else placed.firstOrNull { it.item.id == pinnedId }
    if (pinned != null && pinned.contains(position, pinnedMs, containerWidthPx, laneHeightPx, touchPaddingPx)) {
        return pinned
    }
    for (index in placed.indices.reversed()) {
        val entry = placed[index]
        if (entry === pinned) continue
        if (entry.contains(position, nowMs, containerWidthPx, laneHeightPx, touchPaddingPx)) return entry
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