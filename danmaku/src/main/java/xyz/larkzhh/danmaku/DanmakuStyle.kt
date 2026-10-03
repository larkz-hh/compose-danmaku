package xyz.larkzhh.danmaku

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text style used when [DanmakuStyle.textStyle] is not customised.
 *
 * The soft drop shadow is what keeps white text readable over a bright video frame.
 */
public val DefaultDanmakuTextStyle: TextStyle = TextStyle(
    color = Color.White,
    fontSize = 14.sp,
    fontWeight = FontWeight.Medium,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.6f),
        offset = Offset(0f, 1f),
        blurRadius = 3f,
    ),
)

/**
 * Appearance and layout of [DanmakuOverlay].
 *
 * Everything here is static configuration: it is safe to build one instance per screen and reuse it,
 * and it is compared by value, so passing an equal copy does not trigger a re-measure.
 *
 * @property laneCount Number of horizontal lanes. The layer is `laneCount * laneHeight` tall.
 * @property laneHeight Height of one lane. Text is vertically centred inside it.
 * @property durationMillis Time one entry needs to travel from the right edge until its tail leaves the
 *   left edge. Smaller values scroll faster and free a lane sooner.
 * @property itemGap Minimum horizontal gap kept between two entries sharing a lane.
 * @property textStyle Style used to measure and draw the text. Its colour is overridden per entry by
 *   [DanmakuItem.color].
 * @property touchPadding How far the tap target extends past the text on every side. Text is thin, and
 *   a bare glyph is an unreasonably small target.
 * @property selfHighlight Plate drawn behind entries flagged as [DanmakuItem.isSelf], or `null` to draw
 *   them exactly like every other entry.
 * @property maxVisible Upper bound on how many entries may be on screen at the same moment. Entries that
 *   arrive while the layer already holds this many are dropped. The default places no cap; lower it to keep
 *   a busy layer readable, which is cheaper than shrinking the text.
 * @property overflowPolicy What happens to an entry that arrives while every lane is still busy. Defaults to
 *   [DanmakuOverflowPolicy.Overlap]. Only reached while [maxVisible] still has room: a lane counts as busy
 *   until the tail of its last entry has cleared the right edge, which happens well before that entry leaves
 *   the screen, so with `maxVisible <= laneCount` the cap always fires first.
 */
@Immutable
public data class DanmakuStyle(
    public val laneCount: Int = 3,
    public val laneHeight: Dp = 22.dp,
    public val durationMillis: Long = 8_000L,
    public val itemGap: Dp = 16.dp,
    public val textStyle: TextStyle = DefaultDanmakuTextStyle,
    public val touchPadding: Dp = 8.dp,
    public val selfHighlight: DanmakuSelfHighlight? = DanmakuSelfHighlight.Default,
    public val maxVisible: Int = Int.MAX_VALUE,
    public val overflowPolicy: DanmakuOverflowPolicy = DanmakuOverflowPolicy.Overlap,
) {
    init {
        require(laneCount > 0) { "laneCount must be positive, was $laneCount" }
        require(durationMillis > 0L) { "durationMillis must be positive, was $durationMillis" }
        require(laneHeight > 0.dp) { "laneHeight must be positive, was $laneHeight" }
        require(maxVisible > 0) { "maxVisible must be positive, was $maxVisible" }
    }

    public companion object {
        /** The style [DanmakuOverlay] uses when none is passed. */
        public val Default: DanmakuStyle = DanmakuStyle()
    }
}

/**
 * Plate drawn behind an entry the host app flagged as [DanmakuItem.isSelf].
 *
 * Sending a danmaku into a stream of strangers is the one moment a user needs to find their own entry
 * again, so it gets a plate instead of being distinguished by colour alone, which would collide with
 * the per-entry colours the backend controls.
 *
 * @property background Fill colour of the plate.
 * @property border Stroke colour around the plate.
 * @property borderWidth Stroke width of that border.
 * @property padding How far the plate extends past the text on every side.
 */
@Immutable
public data class DanmakuSelfHighlight(
    public val background: Color = Color.Black.copy(alpha = 0.25f),
    public val border: Color = Color.White,
    public val borderWidth: Dp = 0.5.dp,
    public val padding: Dp = 4.dp,
) {
    public companion object {
        /** A translucent dark fill with a hairline white border. */
        public val Default: DanmakuSelfHighlight = DanmakuSelfHighlight()
    }
}
