package xyz.larkzhh.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * Previews of the public surface.
 *
 * Every preview pins the clock at zero and gives its entries negative start times, which is a compact way of
 * saying "these entered before the moment being previewed". The entries therefore stand at fixed positions
 * over the middle of the layer with no frame ever having to run, which matters because the overlay's own
 * frame loop never stops and a preview that waited for it would render empty.
 */

@Preview(name = "Default", widthDp = 360, heightDp = 150, backgroundColor = 0xFF101014)
@Composable
private fun DanmakuOverlayDefaultPreview() {
    PreviewStage {
        DanmakuOverlay(
            items = previewItems(),
            clock = { 0L },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Outlined", widthDp = 360, heightDp = 150, backgroundColor = 0xFF101014)
@Composable
private fun DanmakuOverlayOutlinedPreview() {
    PreviewStage {
        DanmakuOverlay(
            items = previewItems(),
            clock = { 0L },
            style = DanmakuStyle(textOutline = DanmakuTextOutline.Default),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Custom renderer", widthDp = 360, heightDp = 150, backgroundColor = 0xFF101014)
@Composable
private fun DanmakuOverlayCustomRendererPreview() {
    val plated = DanmakuItemRenderer { context ->
        drawRect(
            color = Color(0xFF1F6FEB).copy(alpha = 0.85f),
            topLeft = context.topLeft,
            size = context.size.toSize(),
        )
        // Everything the stock look does, so the text and its own-entry plate are not reimplemented.
        drawDefaultDanmaku(context)
    }
    PreviewStage {
        DanmakuOverlay(
            items = previewItems(),
            clock = { 0L },
            itemRenderer = plated,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Bubble", widthDp = 360, heightDp = 200, backgroundColor = 0xFF101014)
@Composable
private fun DanmakuBubblePreview() {
    PreviewStage {
        DanmakuBubble(
            selection = DanmakuSelection(
                item = DanmakuItem(id = 1L, text = "hello", timeMs = 0L, isSelf = true),
                topLeft = Offset(60f, 40f),
                size = IntSize(200, 48),
                layerSize = IntSize(360, 231),
                frozenAtMs = 0L,
            ),
        ) {
            DanmakuBubbleItem("Copy") {}
            DanmakuBubbleItem("Report") {}
            DanmakuBubbleItem("Delete") {}
        }
    }
}

/// Stand-in for a video frame, so the white text is previewed against what it is designed to sit on.
@Composable
private fun PreviewStage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2B2B36), Color(0xFF121218))),
            )
            .padding(top = 16.dp),
    ) {
        content()
    }
}

/**
 * Entries spread along their lanes.
 *
 * The negative start times place them at roughly an eighth to seven eighths of their travel once the clock
 * reads zero, so a single preview shows entries entering, crossing and about to leave.
 */
private fun previewItems(): List<DanmakuItem> = listOf(
    DanmakuItem(id = 1L, text = "first", timeMs = -1_000L),
    DanmakuItem(id = 2L, text = "nice one", timeMs = -2_400L, color = Color(0xFFFFD54F)),
    DanmakuItem(id = 3L, text = "this is mine", timeMs = -3_800L, isSelf = true),
    DanmakuItem(id = 4L, text = "passing by", timeMs = -5_200L),
    DanmakuItem(id = 5L, text = "a rather long one", timeMs = -6_600L),
    DanmakuItem(id = 6L, text = "bye", timeMs = -7_300L, color = Color(0xFF7FE3A0)),
)