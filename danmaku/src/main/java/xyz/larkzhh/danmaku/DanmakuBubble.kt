package xyz.larkzhh.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Appearance of [DanmakuBubble].
 *
 * Deliberately small. The point of the default bubble is the positioning and the plate, not the actions:
 * which actions a menu offers is a product decision, so the buttons are the caller's.
 *
 * @property background Fill colour of the plate and of its arrow.
 * @property contentColor Colour of the text drawn by [DanmakuBubbleItem].
 * @property cornerRadius Corner radius of the plate.
 * @property itemTextStyle Text style used by [DanmakuBubbleItem]. Its colour comes from [contentColor].
 * @property arrowWidth Width of the arrow pointing at the entry.
 * @property arrowHeight How far the arrow rises above the plate.
 */
@Immutable
public data class DanmakuBubbleStyle(
    public val background: Color = Color(0xFF2C2C2E),
    public val contentColor: Color = Color.White,
    public val cornerRadius: Dp = 8.dp,
    public val itemTextStyle: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    public val arrowWidth: Dp = 12.dp,
    public val arrowHeight: Dp = 6.dp,
) {
    public companion object {
        /** The style [DanmakuBubble] uses when none is passed. */
        public val Default: DanmakuBubbleStyle = DanmakuBubbleStyle()
    }
}

/** The style the enclosing [DanmakuBubble] was built with, so [DanmakuBubbleItem] does not need it passed twice. */
internal val LocalDanmakuBubbleStyle = compositionLocalOf { DanmakuBubbleStyle.Default }

/**
 * A plate anchored under the selected entry, with an arrow pointing back at it.
 *
 * Meant to be used as the `selectionContent` slot of [DanmakuOverlay]. That slot is composed inside the
 * layer, so [DanmakuSelection.topLeft] is already an offset and this composable only has to keep the plate
 * inside [DanmakuSelection.layerSize] and aim the arrow at the entry.
 *
 * The plate and the arrow are one shape rather than two composables, so the arrow cannot drift away from
 * the plate while the entry is still moving.
 *
 * The content is yours. [DanmakuBubbleItem] is a plain item that matches the plate, and can be mixed with
 * anything else in the same row.
 *
 * @param selection Entry the plate is anchored to.
 * @param modifier Applied to the plate.
 * @param style Appearance of the plate and of its arrow.
 * @param content Items of the menu, laid out in a row.
 */
@Composable
public fun DanmakuBubble(
    selection: DanmakuSelection,
    modifier: Modifier = Modifier,
    style: DanmakuBubbleStyle = DanmakuBubbleStyle.Default,
    content: @Composable RowScope.() -> Unit,
) {
    var size by remember(selection.item.id) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val arrowWidthPx = with(density) { style.arrowWidth.toPx() }
    val arrowHeightPx = with(density) { style.arrowHeight.toPx() }
    val cornerRadiusPx = with(density) { style.cornerRadius.toPx() }

    // Centred on the entry, then pulled back inside the layer when the entry sits near an edge.
    val entryCenter = selection.topLeft.x + selection.size.width / 2f
    val left = DanmakuBubbleMath.leftPx(entryCenter, size.width.toFloat(), selection.layerSize.width)
    val arrowCenter = DanmakuBubbleMath.arrowCenterPx(entryCenter, left)

    CompositionLocalProvider(LocalDanmakuBubbleStyle provides style) {
        Column(
            modifier = modifier
                .offset {
                    IntOffset(
                        x = left.roundToInt(),
                        y = (selection.topLeft.y + selection.size.height).roundToInt(),
                    )
                }
                .onSizeChanged { size = it }
                // Hidden until measured, otherwise the first frame flashes at the wrong place.
                .graphicsLayer { alpha = if (size == IntSize.Zero) 0f else 1f }
                .background(
                    color = style.background,
                    shape = DanmakuBubbleShape(
                        cornerRadiusPx = cornerRadiusPx,
                        arrowCenterPx = arrowCenter,
                        arrowWidthPx = arrowWidthPx,
                        arrowHeightPx = arrowHeightPx,
                    ),
                )
                .padding(top = style.arrowHeight)
                .padding(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

/**
 * A plain menu item that matches [DanmakuBubble].
 *
 * Provided so a default-looking menu costs one line per action, not because any particular action belongs
 * in a library. Pass whatever text you like, including a `stringResource`.
 *
 * @param text Label of the item.
 * @param modifier Applied to the item.
 * @param leadingIcon Optional icon drawn before the label.
 * @param onClick Called when the item is tapped. Last on purpose: it lets the common case be a trailing
 *   lambda, `DanmakuBubbleItem("Copy") { copy() }`.
 */
@Composable
public fun RowScope.DanmakuBubbleItem(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val style = LocalDanmakuBubbleStyle.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        BasicText(
            text = text,
            style = style.itemTextStyle.copy(color = style.contentColor),
        )
    }
}

/**
 * Where the plate goes, and where its arrow meets it.
 *
 * Pure, so the two things that can actually go wrong can be checked without a device: the plate must never
 * leave the layer, and the arrow must still point at the entry after the plate has been pulled back inside.
 */
internal object DanmakuBubbleMath {

    /**
     * Left edge of the plate.
     *
     * @param entryCenterPx Horizontal centre of the entry the plate is anchored to.
     * @param plateWidthPx Width the plate measured to.
     * @param layerWidthPx Width of the layer the plate has to stay inside.
     */
    fun leftPx(entryCenterPx: Float, plateWidthPx: Float, layerWidthPx: Int): Float {
        val limit = (layerWidthPx.toFloat() - plateWidthPx).coerceAtLeast(0f)
        return (entryCenterPx - plateWidthPx / 2f).coerceIn(0f, limit)
    }

    /**
     * Where the arrow meets the plate's top edge, relative to the plate's left edge.
     *
     * Derived from where the entry actually is rather than from the plate's centre, so a plate pushed away
     * from the edge still points at the entry instead of at nothing.
     *
     * @param entryCenterPx Horizontal centre of the entry.
     * @param leftPx Left edge of the plate, as returned by [leftPx].
     */
    fun arrowCenterPx(entryCenterPx: Float, leftPx: Float): Float = entryCenterPx - leftPx
}

/**
 * Rounded rectangle with an arrow rising from its top edge.
 *
 * One path rather than a plate plus a rotated square, so the arrow is filled and clipped by the same shape
 * the plate uses.
 */
private class DanmakuBubbleShape(
    private val cornerRadiusPx: Float,
    private val arrowCenterPx: Float,
    private val arrowWidthPx: Float,
    private val arrowHeightPx: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val halfArrow = arrowWidthPx / 2f
        val tipX = arrowCenterPx.coerceIn(halfArrow, (size.width - halfArrow).coerceAtLeast(halfArrow))
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, arrowHeightPx, size.width, size.height),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                ),
            )
            moveTo(tipX - halfArrow, arrowHeightPx)
            lineTo(tipX, 0f)
            lineTo(tipX + halfArrow, arrowHeightPx)
            close()
        }
        return Outline.Generic(path)
    }
}