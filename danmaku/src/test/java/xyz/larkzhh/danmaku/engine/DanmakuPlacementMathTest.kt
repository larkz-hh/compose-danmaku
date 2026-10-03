package xyz.larkzhh.danmaku.engine

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import xyz.larkzhh.danmaku.DanmakuItem

/**
 * Tests for the two measurement decisions [placeDanmaku] delegates.
 *
 * Both are pure, so they run on the JVM: laying text out needs a device, but deciding what to lay it out with
 * and how much room to reserve for it does not.
 */
class DanmakuPlacementMathTest {

    private val entry = DanmakuItem(id = 1L, text = "hello", timeMs = 0L)

    @Test
    fun `an entry without a declared width is placed by its measured text`() {
        val width = resolveWidthPx(entry, measuredWidthPx = 120f, density = Density(2f))

        assertEquals(120f, width, 0f)
    }

    @Test
    fun `a declared width wins over the measured text`() {
        val avatar = entry.copy(width = 40.dp)

        val width = resolveWidthPx(avatar, measuredWidthPx = 120f, density = Density(2f))

        assertEquals(80f, width, 0f)
    }

    @Test
    fun `a declared width follows the density`() {
        val avatar = entry.copy(width = 40.dp)

        assertEquals(40f, resolveWidthPx(avatar, measuredWidthPx = 0f, density = Density(1f)), 0f)
        assertEquals(120f, resolveWidthPx(avatar, measuredWidthPx = 0f, density = Density(3f)), 0f)
    }

    @Test
    fun `scale multiplies the style font size`() {
        assertEquals(14f, scaledFontSize(entry, base = 14.sp).value, 0f)
        assertEquals(21f, scaledFontSize(entry.copy(scale = 1.5f), base = 14.sp).value, 0f)
        assertEquals(7f, scaledFontSize(entry.copy(scale = 0.5f), base = 14.sp).value, 0f)
    }

    @Test
    fun `an unspecified font size is left unspecified`() {
        val large = entry.copy(scale = 1.5f)

        assertFalse(scaledFontSize(large, base = TextUnit.Unspecified).isSpecified)
    }
}