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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import xyz.larkzhh.danmaku.engine.DanmakuHold
import xyz.larkzhh.danmaku.engine.MeasurementCache
import xyz.larkzhh.danmaku.engine.MeasurementKey
import xyz.larkzhh.danmaku.engine.PlacedDanmaku
import xyz.larkzhh.danmaku.engine.TapAction
import xyz.larkzhh.danmaku.engine.hitTest
import xyz.larkzhh.danmaku.engine.placeDanmaku
import xyz.larkzhh.danmaku.engine.tapAction

/// How long a selection is held before the layer releases it on its own, when the host does not say otherwise.
public const val DefaultDanmakuSelectionTimeoutMillis: Long = 5_000L

/**
 * Draws [items] as a scrolling danmaku layer.
 *
 * The layer reserves vertical space and paints; it owns no playback state and no business rules. Whether
 * an entry is yours, what a tap should open and where the text field lives are all the host app's
 * decisions, reached through [DanmakuItem.isSelf], [selection] and [selectionContent].
 *
 * Measuring happens once per entry rather than once per pass: layouts are carried across passes, so an entry
 * list that grew by one entry costs one measurement instead of one per entry. The frame loop writes its
 * position into a state read only by the draw phase, so neither composition nor layout runs per frame.
 *
 * An entry that has been selected and released carries on from the place it was held at rather than snapping
 * forward to wherever the timeline has since reached, which is what a reader who stopped to finish a long
 * entry expects. It is then out of step with the timeline for good, and keeps the lane it was first given -
 * the delay is applied while drawing and never reaches lane allocation. Seeking the timeline backwards
 * forgets every such delay, because the reading they were measured against has moved.
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
 * @param onSelectionChange Called with the tapped entry. Reported as `null` when the tap releases the
 *   selection instead: a tap on empty space, or a second tap on the entry already pinned. A tap on empty
 *   space with nothing selected reports nothing at all. When `null` the layer consumes no touches, which
 *   lets a parent handle them instead.
 * @param selectionTimeoutMillis How long a selection is held before the layer releases it on its own, in
 *   milliseconds. The count restarts on every press inside the layer, so a menu is not taken away while it is
 *   being read or reached for. `0` or less leaves the selection to the host to release.
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
    selectionTimeoutMillis: Long = DefaultDanmakuSelectionTimeoutMillis,
    selectionContent: @Composable (DanmakuSelection) -> Unit = {},
) {
    val regionHeight = style.laneHeight * style.laneCount
    if (!enabled) {
        Spacer(modifier.height(regionHeight))
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Outlives a pass, which is the point: a list that grew by one entry then costs one measurement instead of
    // one per entry. Everything a layout depends on is a key of this remember, so a change to any of it starts
    // a fresh cache rather than serving layouts measured under the old one. The layer width is deliberately
    // absent: entries are measured unwrapped, so a resize re-places without re-measuring.
    val measurementCache = remember(style, textMeasurer, density) {
        MeasurementCache<MeasurementKey, TextLayoutResult>()
    }

    // How far the drawing of an entry the user has held is delayed, so releasing it carries on from where it
    // stopped. Read by the draw phase and by the hit test; placement is never told about it, which is what
    // keeps a held entry on the lane it was given.
    val hold = remember { DanmakuHold() }
    val liveIds = remember(items) { items.mapTo(HashSet(items.size)) { it.id } }
    LaunchedEffect(liveIds) { hold.retainOnly(liveIds) }

    // Hoisted so that a clock built inline in a composable does not restart the frame loop on every
    // recomposition: only the latest value matters here, never the identity.
    val currentClock by rememberUpdatedState(clock)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)

    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            // An endless frame loop is exactly what this is, and saying so lets previews and tests skip it
            // instead of waiting for a frame that never stops arriving.
            withInfiniteAnimationFrameNanos { }
            val position = currentClock.positionMs()
            // A paused player would otherwise invalidate the draw phase sixty times a second.
            if (position != frameMs) {
                // Going backwards is a seek, and a seek is the one thing a held delay cannot survive: it was
                // measured against a reading of the timeline that no longer applies. Every delay is dropped,
                // and the selection goes with them rather than staying pinned to a moment long gone.
                if (position < frameMs) {
                    hold.clear()
                    if (currentSelection != null) currentOnSelectionChange?.invoke(null)
                }
                frameMs = position
            }
        }
    }

    // Counts presses inside the layer, so the automatic release below restarts whenever the user touches it.
    var presses by remember { mutableIntStateOf(0) }

    // Settled on the change rather than at the tap, so every way a selection can end - a tap on empty space, a
    // tap on the pinned entry, the host clearing its own state, the timeout below - is covered by one rule. A
    // release landing before the moment the entry was frozen is a seek, and DanmakuHold ignores it.
    //
    // SideEffect rather than LaunchedEffect: this has to have happened by the time the frame is drawn, or the
    // entry is painted for one frame at the position it would have reached had it never been held, which reads
    // as a flicker before it snaps back.
    var previousSelection by remember { mutableStateOf<DanmakuSelection?>(null) }
    SideEffect {
        val released = previousSelection
        if (released != selection) {
            if (released != null && selection == null) {
                hold.settle(released.item.id, released.frozenAtMs, frameMs)
            }
            previousSelection = selection
        }
    }

    LaunchedEffect(selection?.item?.id, presses) {
        selection ?: return@LaunchedEffect
        if (selectionTimeoutMillis <= 0L) return@LaunchedEffect
        delay(selectionTimeoutMillis)
        currentOnSelectionChange?.invoke(null)
    }

    BoxWithConstraints(
        modifier = modifier
            .height(regionHeight)
            // Observed on the Initial pass and never consumed: the layer only wants to know that a press
            // happened, so it can postpone the automatic release of a selection. The gesture that selects an
            // entry is untouched and still reaches the canvas below, and so is a tap on the host's menu.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                            presses++
                        }
                    }
                }
            },
    ) {
        val layerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val containerWidthPx = constraints.maxWidth.toFloat()
        val laneHeightPx = with(density) { style.laneHeight.toPx() }
        val gapPx = with(density) { style.itemGap.toPx() }
        val touchPaddingPx = with(density) { style.touchPadding.toPx() }

        val placed = remember(items, containerWidthPx, style, textMeasurer, measurementCache) {
            placeDanmaku(
                items = items,
                containerWidthPx = containerWidthPx,
                gapPx = gapPx,
                measurer = textMeasurer,
                density = density,
                style = style,
                cache = measurementCache,
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
            val progress = pinnedEntry.progressAt(pinnedMs - hold.shiftMs(pinnedEntry.item.id))
            selection.copy(
                topLeft = Offset(
                    x = pinnedEntry.xAt(progress, containerWidthPx),
                    y = pinnedEntry.topLeftY(laneHeightPx),
                ),
                size = pinnedEntry.size,
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
                        shiftMsOf = hold::shiftMs,
                    )
                    when (tapAction(hit?.item?.id, current?.item?.id)) {
                        TapAction.Ignore -> return@awaitEachGesture

                        TapAction.Clear -> {
                            up.consume()
                            currentOnSelectionChange?.invoke(null)
                        }

                        TapAction.Select -> {
                            // tapAction only reports Select for a hit, so this is never null in practice.
                            val entry = hit ?: return@awaitEachGesture
                            up.consume()
                            // Read at the same moment the entry is drawn at, or the position handed to the
                            // host is not where the entry it describes actually is.
                            val progress = entry.progressAt(frameMs - hold.shiftMs(entry.item.id))
                            currentOnSelectionChange?.invoke(
                                DanmakuSelection(
                                    item = entry.item,
                                    topLeft = Offset(
                                        x = entry.xAt(progress, containerWidthPx),
                                        y = entry.topLeftY(laneHeightPx),
                                    ),
                                    size = entry.size,
                                    layerSize = layerSize,
                                    frozenAtMs = frameMs,
                                ),
                            )
                        }
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(tapModifier)) {
            placed.forEach { entry ->
                // The pinned entry is drawn after the loop so nothing paints over it.
                if (entry === pinnedEntry) return@forEach
                val progress = entry.progressAt(frameMs - hold.shiftMs(entry.item.id))
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
                val progress = entry.progressAt(pinnedMs - hold.shiftMs(entry.item.id))
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
    size = size,
    alpha = alpha,
    selfHighlight = if (item.isSelf) style.selfHighlight else null,
    textOutline = style.textOutline,
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