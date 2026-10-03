package xyz.larkzhh.danmaku

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A single danmaku entry.
 *
 * This is the only data contract between the library and the host app: the library knows nothing about
 * your users, your feeds or your backend models, it only needs to know what to scroll and when.
 *
 * @property id Stable identity of the entry. Must be unique within the list passed to [DanmakuOverlay].
 * @property text Text to draw. An entry wider than the layer is never wrapped or clipped; it simply
 *   scrolls across the screen, which is what makes lane allocation predictable.
 * @property timeMs The moment the entry enters from the right edge, in milliseconds, measured against
 *   the timeline returned by [DanmakuClock.positionMs].
 * @property color Text colour. Always wins over [DanmakuStyle.textStyle], so an entry that wants the
 *   style colour has to pass that colour explicitly.
 * @property isSelf Whether the entry was sent by the current user. The library never resolves identity
 *   itself; the host app computes this, typically as `authorId == currentUserId`. The default renderer
 *   highlights the entries flagged this way.
 */
@Immutable
public data class DanmakuItem(
    public val id: Long,
    public val text: String,
    public val timeMs: Long,
    public val color: Color = Color.White,
    public val isSelf: Boolean = false,
)
