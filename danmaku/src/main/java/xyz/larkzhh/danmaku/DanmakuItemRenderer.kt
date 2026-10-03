package xyz.larkzhh.danmaku

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize

/**
 * Everything [DanmakuItemRenderer] needs to draw one entry of one frame.
 *
 * The overlay resolves all layout for you, so an implementation only has to decide how the entry looks.
 *
 * @property item The entry being drawn.
 * @property textLayout Measured text, ready to hand to `drawText`. Never measured per frame.
 * @property topLeft Where the text goes, in pixels, relative to the layer.
 * @property size Box reserved for the entry in pixels. Its width is the measured text width unless
 *   [DanmakuItem.width] overrides it; content that is not text should be drawn inside it.
 * @property alpha Opacity of the layer, as passed to [DanmakuOverlay].
 * @property selfHighlight Plate to draw behind the text, or `null` when the entry is not the current
 *   user's or [DanmakuStyle.selfHighlight] is disabled.
 * @property textOutline Outline to stroke behind the text, or `null` for none.
 */
public class DanmakuDrawContext internal constructor(
    public val item: DanmakuItem,
    public val textLayout: TextLayoutResult,
    public val topLeft: Offset,
    public val size: IntSize,
    public val alpha: Float,
    public val selfHighlight: DanmakuSelfHighlight?,
    public val textOutline: DanmakuTextOutline?,
)

/**
 * Draws a single danmaku entry.
 *
 * The overlay runs its frame loop in the draw phase, so an implementation is called once per visible
 * entry per frame and must only draw. Composing, measuring or allocating there scales with the frame
 * rate and is what makes hand-written danmaku layers stutter.
 *
 * Two ways to get one:
 * - [DanmakuItemRenderer] builds it from a lambda, `DrawScope` being the receiver.
 * - [drawDefaultDanmaku] is the stock look, callable from a custom renderer to extend rather than
 *   replace it.
 */
public interface DanmakuItemRenderer {
    /** Draws [context] into this scope. */
    public fun DrawScope.drawDanmaku(context: DanmakuDrawContext)
}

/**
 * Builds a [DanmakuItemRenderer] from a lambda, the way a `fun interface` would.
 *
 * ```kotlin
 * val mine = DanmakuItemRenderer { context ->
 *     if (context.item.isSelf) drawDefaultDanmaku(context)
 * }
 * ```
 */
public fun DanmakuItemRenderer(
    draw: DrawScope.(DanmakuDrawContext) -> Unit,
): DanmakuItemRenderer = object : DanmakuItemRenderer {
    override fun DrawScope.drawDanmaku(context: DanmakuDrawContext): Unit = draw(context)
}

/**
 * Stock renderer: the optional [DanmakuSelfHighlight] plate, then the text.
 *
 * This is the default of [DanmakuOverlay], so the default look only ever exists as one implementation
 * rather than as a branch beside a customisable one.
 */
public object DefaultDanmakuItemRenderer : DanmakuItemRenderer {
    override fun DrawScope.drawDanmaku(context: DanmakuDrawContext) {
        drawDefaultDanmaku(context)
    }
}

/**
 * Draws the stock look.
 *
 * Call it from a custom [DanmakuItemRenderer] to keep the plate and the text and only add to them,
 * instead of reimplementing both.
 */
public fun DrawScope.drawDefaultDanmaku(context: DanmakuDrawContext) {
    val highlight = context.selfHighlight
    if (highlight != null) {
        val paddingPx = highlight.padding.toPx()
        val plateTopLeft = Offset(context.topLeft.x - paddingPx, context.topLeft.y - paddingPx)
        val plateSize = Size(
            width = context.size.width + paddingPx * 2f,
            height = context.size.height + paddingPx * 2f,
        )
        drawRect(
            color = highlight.background,
            topLeft = plateTopLeft,
            size = plateSize,
            alpha = context.alpha,
        )
        drawRect(
            color = highlight.border,
            topLeft = plateTopLeft,
            size = plateSize,
            style = Stroke(width = highlight.borderWidth.toPx()),
            alpha = context.alpha,
        )
    }
    val outline = context.textOutline
    if (outline != null) {
        // A stroked pass underneath: on its own it would render the glyph hollow, which is not the look.
        drawText(
            textLayoutResult = context.textLayout,
            color = outline.color,
            topLeft = context.topLeft,
            alpha = context.alpha,
            drawStyle = Stroke(width = outline.width.toPx()),
        )
    }
    drawText(
        textLayoutResult = context.textLayout,
        topLeft = context.topLeft,
        alpha = context.alpha,
    )
}
