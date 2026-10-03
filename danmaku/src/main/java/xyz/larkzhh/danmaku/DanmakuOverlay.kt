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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import xyz.larkzhh.danmaku.engine.hitTest
import xyz.larkzhh.danmaku.engine.placeDanmaku

/**
 * Draws [items] as a scrolling danmaku layer.
 *
 * The layer only reserves vertical space and paints; it owns no playback state and no business rules.
 * Whether an entry is yours, what a tap should open and where the text field lives are all the host
 * app's decisions, exposed through [DanmakuItem.isSelf] and [onItemClick].
 *
 * Measuring happens once per change of [items] or of the layer width, and the frame loop writes its
 * position into a state read only by the draw phase. Neither composition nor layout runs per frame.
 *
 * @param items Entries to draw. Order does not matter, they are sorted by [DanmakuItem.timeMs].
 * @param clock Playback timeline used to place the entries. Read once per frame.
 * @param modifier Applied to the layer itself.
 * @param style Appearance and layout. Use [DanmakuStyle.Default] for the stock look.
 * @param enabled Whether entries are drawn. When `false` the layer still reserves its height, so
 *   toggling it does not shift the surrounding layout.
 * @param opacity Opacity of everything the layer draws, in `0f..1f`.
 * @param itemRenderer How a single entry is drawn.
 * @param onItemClick Called with the tapped entry. When `null` the layer consumes no touches at all,
 *   which lets a parent handle them instead.
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
    onItemClick: ((DanmakuItem) -> Unit)? = null,
) {
    val regionHeight = style.laneHeight * style.laneCount
    if (!enabled) {
        Spacer(modifier.height(regionHeight))
        return
    }

    val textMeasurer = rememberTextMeasurer()

    // Hoisted so that a clock built inline in a composable does not restart the frame loop on every
    // recomposition: only the clock's identity matters here, never its value.
    val currentClock by rememberUpdatedState(clock)

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

        val tapModifier = if (onItemClick == null) {
            Modifier
        } else {
            Modifier.pointerInput(placed, containerWidthPx, laneHeightPx, touchPaddingPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val hit = hitTest(
                        placed = placed,
                        position = down.position,
                        nowMs = frameMs,
                        containerWidthPx = containerWidthPx,
                        laneHeightPx = laneHeightPx,
                        touchPaddingPx = touchPaddingPx,
                    )
                    if (hit != null) {
                        up.consume()
                        onItemClick(hit.item)
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(tapModifier)) {
            placed.forEach { entry ->
                val progress = entry.progressAt(frameMs)
                if (progress !in 0f..1f) return@forEach
                // `with` is required, not stylistic: [DanmakuItemRenderer.drawDanmaku] is a member
                // extension, and Kotlin only resolves one whose dispatch receiver is implicit too.
                // Writing `itemRenderer.drawDanmaku(...)` here does not compile.
                with(itemRenderer) {
                    drawDanmaku(
                        DanmakuDrawContext(
                            item = entry.item,
                            textLayout = entry.textLayout,
                            topLeft = Offset(
                                x = entry.xAt(progress, containerWidthPx),
                                y = entry.topLeftY(laneHeightPx),
                            ),
                            alpha = opacity,
                            selfHighlight = if (entry.item.isSelf) style.selfHighlight else null,
                        ),
                    )
                }
            }
        }
    }
}
