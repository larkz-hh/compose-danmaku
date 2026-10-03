package xyz.larkzhh.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import xyz.larkzhh.danmaku.engine.PlacedDanmaku
import xyz.larkzhh.danmaku.engine.hitTest
import xyz.larkzhh.danmaku.engine.placeDanmaku

/**
 * Draws [items] as a scrolling danmaku layer.
 *
 * The layer reserves vertical space and paints; it owns no playback state and no business rules. Whether
 * an entry is yours, what a tap should open and where the text field lives are all the host app's
 * decisions, reached through [DanmakuItem.isSelf], [selection] and [selectionContent].
 *
 * Measuring happens once per change of [items] or of the layer width, and the frame loop writes its
 * position into a state read only by the draw phase. Neither composition nor layout runs per frame.
 *
 * @param items Entries to draw. Order does not matter, they are sorted by [DanmakuItem.timeMs].
 * @param clock Playback timeline used to place the entries. Read once per frame.
 * @param modifier Applied to the layer itself.
 * @param style Appearance and layout. Use [DanmakuStyle.Default] for the stock look.
 * @param enabled Whether entries are drawn. When `false` the layer still reserves its height, so toggling
 *   it does not shift the surrounding layout.
 * @param opacity Opacity of everything the layer draws. Kept out of [DanmakuStyle] deliberately: the style
 *   is a key of the measurement cache, so a value that changes often would re-measure every entry.
 * @param itemRenderer How a single entry is drawn.
 * @param selection The entry to pin in place, normally the value last reported by [onSelectionChange].
 *   Pass `null` to let every entry follow the clock.
 * @param onSelectionChange Called with the tapped entry, and with `null` when a tap lands on empty space
 *   while something is selected. A tap on empty space with nothing selected reports nothing. When `null`
 *   the layer consumes no touches at all, which lets a parent handle them instead.
 * @param selectionContent Content drawn inside the layer's own coordinate space whenever [selection]
 *   resolves to an entry. Position it with [DanmakuSelection.topLeft]; what it contains is up to you.
 */
@Composable
public fun DanmakuOverlay(
    items: List<DanmakuItem>,
    clock: DanmakuClock,
    modifier: Modifier = Modifier,
    style: DanmakuStyle = DanmakuStyle.Default,
    enabled: Boolean = true,
    opacity: Float = 1f,
    itemRenderer: DanmakuItemRenderer = DefaultDanmakuItemRenderer,
    selection: DanmakuSelection? = null,
    onSelectionChange: ((DanmakuSelection?) -> Unit)? = null,
    selectionContent: @Composable (DanmakuSelection) -> Unit = {},
) {
    val regionHeight = style.laneHeight * style.laneCount
    if (!enabled) {
        Spacer(modifier.height(regionHeight))
        return
    }

    val textMeasurer = rememberTextMeasurer()

    // Hoisted so that a clock built inline in a composable does not restart the frame loop on every
    // recomposition: only the latest value matters here, never the identity.
    val currentClock by rememberUpdatedState(clock)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)

    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            val position = currentClock.positionMs()
            // A paused player would otherwise invalidate the draw phase sixty times a second.
            if (position != frameMs) frameMs = position
        }
    }

    BoxWithConstraints(modifier = modifier.height(regionHeight)) {
        val layerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val containerWidthPx = constraints.maxWidth.toFloat()
        val density = LocalDensity.current
        val laneHeightPx = with(density) { style.laneHeight.toPx() }
        val gapPx = with(density) { style.itemGap.toPx() }
        val touchPaddingPx = with(density) { style.touchPadding.toPx() }

        val placed = remember(items, containerWidthPx, style, textMeasurer) {
            placeDanmaku(
                items = items,
                containerWidthPx = containerWidthPx,
                gapPx = gapPx,
                measurer = textMeasurer,
                style = style,
            )
        }

        val pinnedId = selection?.item?.id
        val pinnedMs = selection?.frozenAtMs ?: 0L
        val pinnedEntry = if (pinnedId == null) null else placed.firstOrNull { it.item.id == pinnedId }

        // Resolved here rather than trusting the value the caller stored, so the slot stays aligned when the
        // layer is resized while a menu is open. A selection whose entry is no longer placed - dropped by
        // [DanmakuStyle.maxVisible], or gone from the list - reports nothing, so no menu lingers over nothing.
        val resolvedSelection = if (selection == null || pinnedEntry == null) {
            null
        } else {
            val progress = pinnedEntry.progressAt(pinnedMs)
            selection.copy(
                topLeft = Offset(
                    x = pinnedEntry.xAt(progress, containerWidthPx),
                    y = pinnedEntry.topLeftY(laneHeightPx),
                ),
                size = pinnedEntry.textLayout.size,
                layerSize = layerSize,
            )
        }

        val tapModifier = if (onSelectionChange == null) {
            Modifier
        } else {
            Modifier.pointerInput(placed, containerWidthPx, laneHeightPx, touchPaddingPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val current = currentSelection
                    val hit = hitTest(
                        placed = placed,
                        position = down.position,
                        nowMs = frameMs,
                        containerWidthPx = containerWidthPx,
                        laneHeightPx = laneHeightPx,
                        touchPaddingPx = touchPaddingPx,
                        pinnedId = current?.item?.id,
                        pinnedMs = current?.frozenAtMs ?: 0L,
                    )
                    when {
                        hit != null -> {
                            up.consume()
                            // Re-tapping the pinned entry keeps it where it already is.
                            val frozenAtMs =
                                if (hit.item.id == current?.item?.id) current.frozenAtMs else frameMs
                            val progress = hit.progressAt(frozenAtMs)
                            currentOnSelectionChange?.invoke(
                                DanmakuSelection(
                                    item = hit.item,
                                    topLeft = Offset(
                                        x = hit.xAt(progress, containerWidthPx),
                                        y = hit.topLeftY(laneHeightPx),
                                    ),
                                    size = hit.textLayout.size,
                                    layerSize = layerSize,
                                    frozenAtMs = frozenAtMs,
                                ),
                            )
                        }

                        current != null -> {
                            up.consume()
                            currentOnSelectionChange?.invoke(null)
                        }
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(tapModifier)) {
            placed.forEach { entry ->
                // The pinned entry is drawn after the loop so nothing paints over it.
                if (entry === pinnedEntry) return@forEach
                val progress = entry.progressAt(frameMs)
                if (progress !in 0f..1f) return@forEach
                renderDanmaku(
                    context = entry.drawContext(
                        topLeft = Offset(
                            x = entry.xAt(progress, containerWidthPx),
                            y = entry.topLeftY(laneHeightPx),
                        ),
                        alpha = opacity,
                        style = style,
                    ),
                    itemRenderer = itemRenderer,
                )
            }
            pinnedEntry?.let { entry ->
                val progress = entry.progressAt(pinnedMs)
                renderDanmaku(
                    context = entry.drawContext(
                        topLeft = Offset(
                            x = entry.xAt(progress, containerWidthPx),
                            y = entry.topLeftY(laneHeightPx),
                        ),
                        alpha = opacity,
                        style = style,
                    ),
                    itemRenderer = itemRenderer,
                )
            }
        }

        if (resolvedSelection != null) {
            selectionContent(resolvedSelection)
        }
    }
}

/// Builds the drawing context of one entry.
private fun PlacedDanmaku.drawContext(
    topLeft: Offset,
    alpha: Float,
    style: DanmakuStyle,
): DanmakuDrawContext = DanmakuDrawContext(
    item = item,
    textLayout = textLayout,
    topLeft = topLeft,
    alpha = alpha,
    selfHighlight = if (item.isSelf) style.selfHighlight else null,
)

/// Hands one entry to the renderer.
private fun DrawScope.renderDanmaku(
    context: DanmakuDrawContext,
    itemRenderer: DanmakuItemRenderer,
) {
    with(itemRenderer) {
        drawDanmaku(context)
    }
}