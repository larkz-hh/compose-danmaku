package xyz.larkzhh.danmaku.engine

import androidx.compose.ui.geometry.Offset

/**
 * Finds the entry under a touch position.
 *
 * The search walks the placed entries backwards, so the entry drawn last wins. That matches what the
 * user sees: entries are painted in list order, and overlapping ones do occur when every lane is busy.
 *
 * @param placed Entries as returned by [placeDanmaku].
 * @param position Touch position relative to the layer, in pixels.
 * @param nowMs Current playback position, used to locate moving entries.
 * @param containerWidthPx Width of the layer in pixels.
 * @param laneHeightPx Height of one lane in pixels.
 * @param touchPaddingPx How far the tap target extends past the text on every side.
 * @return The touched entry, or `null` when the touch missed every visible entry.
 */
internal fun hitTest(
    placed: List<PlacedDanmaku>,
    position: Offset,
    nowMs: Long,
    containerWidthPx: Float,
    laneHeightPx: Float,
    touchPaddingPx: Float,
): PlacedDanmaku? {
    for (index in placed.indices.reversed()) {
        val entry = placed[index]
        val progress = entry.progressAt(nowMs)
        if (progress !in 0f..1f) continue

        val left = entry.xAt(progress, containerWidthPx)
        val top = entry.lane * laneHeightPx
        val insideX = position.x in (left - touchPaddingPx)..(left + entry.widthPx + touchPaddingPx)
        val insideY = position.y in (top - touchPaddingPx)..(top + laneHeightPx + touchPaddingPx)
        if (insideX && insideY) return entry
    }
    return null
}
