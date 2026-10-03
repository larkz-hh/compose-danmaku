package xyz.larkzhh.danmaku

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Color as AndroidColor

/**
 * Tests that an entry keeps its own colour when it shares a measured layout with another entry.
 *
 * Placement measures one layout per text and size and hands the same instance to every entry that matches, so
 * a layout can only ever carry one colour. `drawText` falls back to that colour when it is not given one, which
 * means a renderer that omits it paints every one of those entries alike - and Compose's own layout cache
 * already ignores colour, so the collision is reachable with or without the placement cache.
 *
 * Drawn through [drawDefaultDanmaku] on a bare canvas rather than through [DanmakuOverlay]: this is about what
 * the renderer paints, and the overlay's frame loop never lets a composition test reach an idle state.
 */
@RunWith(AndroidJUnit4::class)
class DanmakuRendererColorTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun entriesSharingOneLayoutAreDrawnInTheirOwnColour() {
        rule.setContent {
            val measurer = rememberTextMeasurer()
            val style = DanmakuStyle()
            // One layout, handed to both entries on purpose: this is what placement does for two entries that
            // share a text and a size.
            val shared = remember {
                measurer.measure(
                    text = TEXT,
                    style = style.textStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            val plain = DanmakuItem(id = 1L, text = TEXT, timeMs = 0L, color = Color.White)
            val tinted = DanmakuItem(id = 2L, text = TEXT, timeMs = 0L, color = Color.Red)

            Canvas(modifier = Modifier.size(160.dp, 80.dp).testTag(TAG)) {
                val half = size.height / 2f
                with(DefaultDanmakuItemRenderer) {
                    drawDanmaku(shared.contextOf(plain, Offset(8f, 0f)))
                    drawDanmaku(shared.contextOf(tinted, Offset(8f, half)))
                }
            }
        }

        val bitmap = rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap()
        val top = inkAverage(bitmap, 0, bitmap.height / 2)
        val bottom = inkAverage(bitmap, bitmap.height / 2, bitmap.height)

        assertTrue("the white entry did not draw anything", top.any { it > 0f })
        assertTrue("the tinted entry did not draw anything", bottom.any { it > 0f })

        // The lower band is drawn from the same layout as the upper one, so this is what fails when a renderer
        // lets the layout decide the colour: both bands come out neutral.
        assertTrue(
            "the upper entry should be neutral, was r=${top[0]} g=${top[1]} b=${top[2]}",
            top.max() - top.min() < NEUTRAL_TOLERANCE,
        )
        assertTrue(
            "the lower entry should stay red, was r=${bottom[0]} g=${bottom[1]} b=${bottom[2]}",
            bottom[0] - bottom[1] > RED_MARGIN && bottom[0] - bottom[2] > RED_MARGIN,
        )
    }

    private fun TextLayoutResult.contextOf(item: DanmakuItem, topLeft: Offset) = DanmakuDrawContext(
        item = item,
        textLayout = this,
        topLeft = topLeft,
        size = IntSize(size.width, size.height),
        alpha = 1f,
        selfHighlight = null,
        textOutline = null,
    )

    /**
     * Mean colour of the drawn pixels in a band, weighted by coverage.
     *
     * Weighted by alpha and skipping fully transparent pixels, because the canvas is otherwise empty: an
     * unweighted mean over the band would be dominated by whatever is behind the glyphs.
     */
    private fun inkAverage(bitmap: Bitmap, fromY: Int, untilY: Int): FloatArray {
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var weight = 0.0
        for (y in fromY until untilY) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val coverage = AndroidColor.alpha(pixel) / 255.0
                if (coverage <= 0.0) continue
                red += AndroidColor.red(pixel) * coverage
                green += AndroidColor.green(pixel) * coverage
                blue += AndroidColor.blue(pixel) * coverage
                weight += coverage
            }
        }
        if (weight == 0.0) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf((red / weight).toFloat(), (green / weight).toFloat(), (blue / weight).toFloat())
    }

    private companion object {
        const val TAG = "renderer-color"
        const val TEXT = "666"
        const val NEUTRAL_TOLERANCE = 24f
        const val RED_MARGIN = 60f
    }
}
