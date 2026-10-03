package xyz.larkzhh.compose_danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import xyz.larkzhh.danmaku.DanmakuBubble
import xyz.larkzhh.danmaku.DanmakuBubbleItem
import xyz.larkzhh.danmaku.DanmakuClock
import xyz.larkzhh.danmaku.DanmakuItem
import xyz.larkzhh.danmaku.DanmakuOverflowPolicy
import xyz.larkzhh.danmaku.DanmakuOverlay
import xyz.larkzhh.danmaku.DanmakuSelection
import xyz.larkzhh.danmaku.DanmakuStyle
import xyz.larkzhh.danmaku.DanmakuTextOutline
import xyz.larkzhh.danmaku.DefaultDanmakuTextStyle

/** How much danmaku the demo holds. The clock wraps at this point so the layer is never empty. */
private const val DEMO_CYCLE_MS = 80_000L

private const val BASE_DURATION_MS = 8_000f
private val BASE_FONT_SIZE = 14.sp

/**
 * Sample screen: a fake video surface with a danmaku layer on top.
 *
 * The clock is advanced by the frame loop instead of by a player, so the sample carries no media dependency.
 * The panel at the bottom drives [DanmakuStyle] and the layer opacity, which is what the library leaves to
 * the host: none of these values is a setting the library owns.
 */
@Composable
fun DanmakuDemo() {
    var playing by remember { mutableStateOf(true) }
    var danmakuEnabled by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<DanmakuSelection?>(null) }
    var lastAction by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var settings by remember { mutableStateOf(DemoSettings()) }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameTimeMs ->
                val delta = frameTimeMs - last
                last = frameTimeMs
                // The demo only holds a minute of danmaku, so the clock wraps rather than running dry.
                positionMs = (positionMs + delta) % DEMO_CYCLE_MS
            }
        }
    }

    val items = remember { demoDanmaku() }
    val clock = remember { DanmakuClock { positionMs } }
    val react: (String, DanmakuSelection) -> Unit = { label, selected ->
        lastAction = "$label: ${selected.item.text}"
        selection = null
    }

    // Built once per change of the settings: the style is a key of the measurement cache, so handing over a
    // fresh instance on every recomposition would re-measure every entry.
    val style = remember(settings) {
        DanmakuStyle(
            laneCount = settings.lanes.roundToInt(),
            durationMillis = (BASE_DURATION_MS / settings.speed).roundToLong(),
            maxVisible = settings.density.roundToInt(),
            overflowPolicy = if (settings.dropWhenFull) {
                DanmakuOverflowPolicy.Drop
            } else {
                DanmakuOverflowPolicy.Overlap
            },
            textStyle = DefaultDanmakuTextStyle.copy(fontSize = BASE_FONT_SIZE * settings.fontScale),
            textOutline = if (settings.outlined) DanmakuTextOutline.Default else null,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            // The layer only sees taps inside its own lane band, so a tap anywhere else is the host's to
            // handle. Taps that do land on an entry are consumed by the layer before this ever fires.
            .pointerInput(Unit) {
                detectTapGestures { selection = null }
            },
    ) {
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
            style = style,
            enabled = danmakuEnabled,
            opacity = settings.opacity,
            selection = selection,
            onSelectionChange = { selection = it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 96.dp),
        ) { selected ->
            // The plate and the plain item come from the library. Which actions exist, and the rule that
            // only your own entries can be deleted, stay here.
            DanmakuBubble(selection = selected) {
                DanmakuBubbleItem("Copy") { react("copy", selected) }
                DanmakuBubbleItem("Report") { react("report", selected) }
                if (selected.item.isSelf) {
                    DanmakuBubbleItem("Delete") { react("delete", selected) }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showSettings) {
                SettingsPanel(settings = settings, onChange = { settings = it })
            }
            Text(
                text = lastAction ?: "tap any danmaku",
                color = Color(0xFF9E9EAA),
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { playing = !playing }) {
                    Text(if (playing) "Pause" else "Play")
                }
                Button(onClick = { danmakuEnabled = !danmakuEnabled }) {
                    Text(if (danmakuEnabled) "Hide" else "Show")
                }
                Button(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "Close" else "Settings")
                }
            }
        }
    }
}

/** Everything the panel can change, in one object so the style can be rebuilt in a single step. */
private data class DemoSettings(
    val opacity: Float = 1f,
    /** Entries allowed on screen at once, which is what a danmaku client calls its density. */
    val density: Float = 30f,
    /** Whether an entry arriving with every lane busy is dropped rather than drawn over another. */
    val dropWhenFull: Boolean = false,
    val fontScale: Float = 1f,
    val speed: Float = 1f,
    val lanes: Float = 3f,
    val outlined: Boolean = false,
)

@Composable
private fun SettingsPanel(
    settings: DemoSettings,
    onChange: (DemoSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF17171C))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        SettingSlider(
            label = "Opacity",
            value = settings.opacity,
            range = 0.2f..1f,
            valueText = format(settings.opacity),
            onValueChange = { onChange(settings.copy(opacity = it)) },
        )
        // Density and the behaviour when the layer is full are two halves of one setting, so they sit together.
        SettingSlider(
            label = "Density",
            value = settings.density,
            range = 4f..40f,
            valueText = settings.density.roundToInt().toString(),
            onValueChange = { onChange(settings.copy(density = it)) },
        )
        SettingSwitch(
            label = "Drop when full",
            checked = settings.dropWhenFull,
            enabled = settings.density > settings.lanes,
            hint = if (settings.density <= settings.lanes) {
                "The cap fires first at or below the lane count"
            } else {
                null
            },
            onCheckedChange = { onChange(settings.copy(dropWhenFull = it)) },
        )
        SettingSlider(
            label = "Font size",
            value = settings.fontScale,
            range = 0.6f..1.8f,
            valueText = format(settings.fontScale) + "x",
            onValueChange = { onChange(settings.copy(fontScale = it)) },
        )
        SettingSlider(
            label = "Speed",
            value = settings.speed,
            range = 0.5f..2f,
            valueText = format(settings.speed) + "x",
            onValueChange = { onChange(settings.copy(speed = it)) },
        )
        SettingSlider(
            label = "Lanes",
            value = settings.lanes,
            range = 1f..6f,
            valueText = settings.lanes.roundToInt().toString(),
            onValueChange = { onChange(settings.copy(lanes = it)) },
        )
        SettingSwitch(
            label = "Outline",
            checked = settings.outlined,
            onCheckedChange = { onChange(settings.copy(outlined = it)) },
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, color = Color(0xFF9E9EAA), fontSize = 12.sp)
            Text(text = valueText, color = Color(0xFF9E9EAA), fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    hint: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) Color(0xFF9E9EAA) else Color(0xFF55555C),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        if (hint != null) {
            Text(text = hint, color = Color(0xFF55555C), fontSize = 11.sp)
        }
    }
}

private fun format(value: Float): String = String.format(Locale.US, "%.2f", value)

/**
 * Synthetic danmaku, dense enough that the lane count matters.
 *
 * One entry every 600 ms is little enough that three lanes recycle fast enough to absorb it, and the extra
 * lanes a bigger `laneCount` adds would stay empty. At 300 ms the arrival rate outruns a lane's 1.4 s
 * recycling time, so the lane count and the on-screen bound both become visible.
 */
private fun demoDanmaku(): List<DanmakuItem> {
    val texts = listOf(
        "前方高能", "这也太顶了吧", "哈哈哈哈哈哈", "第一次看这种",
        "弹幕护体", "路过", "这段我看了三遍", "作者好厉害",
        "有一起看的吗", "打卡", "这个转场绝了", "笑死我了",
    )
    return List(266) { index ->
        DanmakuItem(
            id = index.toLong(),
            text = texts[index % texts.size],
            timeMs = index * 300L,
            color = if (index % 11 == 0) Color(0xFFFFD54F) else Color.White,
            isSelf = index % 17 == 0,
        )
    }
}