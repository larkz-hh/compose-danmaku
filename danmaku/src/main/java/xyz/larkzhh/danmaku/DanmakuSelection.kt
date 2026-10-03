package xyz.larkzhh.danmaku

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * An entry the user has selected, together with where it sits in the layer.
 *
 * Produced by [DanmakuOverlay] when an entry is tapped, and passed back through its `selection`
 * parameter to pin that entry in place while the rest keeps scrolling.
 *
 * Only the library can answer "where is that entry right now": the position depends on the container
 * width, the measured text and the moment the entry was pinned, none of which the caller has. Everything
 * else about a menu built on top of this - its shape, its entries, who is allowed to delete - stays with
 * the caller.
 *
 * @property item The selected entry.
 * @property topLeft Where the entry sits, in pixels, relative to the layer's top left corner. Use it
 *   directly from the `selectionContent` slot, which is composed inside the same coordinate space.
 * @property size Measured size of the entry in pixels.
 * @property layerSize Size of the layer in pixels. Position a menu with it: a menu near the right edge has
 *   to be pulled back inside, and only the library knows how wide the layer ended up.
 * @property frozenAtMs The clock value the entry is pinned at. While the selection is passed back, the
 *   entry is drawn at this moment instead of following the clock, which is what stops it mid-flight.
 */
@Immutable
public data class DanmakuSelection(
    public val item: DanmakuItem,
    public val topLeft: Offset,
    public val size: IntSize,
    public val layerSize: IntSize,
    public val frozenAtMs: Long,
)