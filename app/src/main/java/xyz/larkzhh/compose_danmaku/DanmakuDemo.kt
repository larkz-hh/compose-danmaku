package xyz.larkzhh.compose_danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.larkzhh.danmaku.DanmakuClock
import xyz.larkzhh.danmaku.DanmakuItem
import xyz.larkzhh.danmaku.DanmakuOverlay

/**
 * Sample screen: a fake video surface with a danmaku layer on top.
 *
 * The clock is advanced by the frame loop instead of by a player, so the sample carries no media
 * dependency and scrolls the moment the app starts.
 */
@Composable
fun DanmakuDemo() {
    var playing by remember { mutableStateOf(true) }
    var danmakuEnabled by remember { mutableStateOf(true) }
    var lastTapped by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameTimeMs ->
                positionMs += frameTimeMs - last
                last = frameTimeMs
            }
        }
    }

    val items = remember { demoDanmaku() }
    val clock = remember { DanmakuClock { positionMs } }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F))) {
        // Stand-in for the video surface.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center)
                .background(Brush.verticalGradient(listOf(Color(0xFF2B2B36), Color(0xFF121218)))),
        )

        DanmakuOverlay(
            items = items,
            clock = clock,
            enabled = danmakuEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 96.dp),
            onItemClick = { lastTapped = it.text },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = lastTapped?.let { "tapped: $it" } ?: "tap any danmaku",
                color = Color(0xFF9E9EAA),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { playing = !playing }) {
                    Text(if (playing) "Pause" else "Play")
                }
                Button(onClick = { danmakuEnabled = !danmakuEnabled }) {
                    Text(if (danmakuEnabled) "Hide danmaku" else "Show danmaku")
                }
            }
        }
    }
}

/** A minute of synthetic danmaku, dense enough to keep all three lanes busy. */
private fun demoDanmaku(): List<DanmakuItem> {
    val texts = listOf(
        "前方高能", "这也太顶了吧", "哈哈哈哈哈哈", "第一次看这种",
        "弹幕护体", "路过", "这段我看了三遍", "作者好厉害",
        "有一起看的吗", "打卡", "这个转场绝了", "笑死我了",
    )
    return List(120) { index ->
        DanmakuItem(
            id = index.toLong(),
            text = texts[index % texts.size],
            timeMs = index * 600L,
            color = if (index % 11 == 0) Color(0xFFFFD54F) else Color.White,
            isSelf = index % 17 == 0,
        )
    }
}