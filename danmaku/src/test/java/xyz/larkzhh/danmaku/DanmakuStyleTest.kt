package xyz.larkzhh.danmaku

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests for the validation [DanmakuStyle] does on construction.
 *
 * A style holding a value that makes no sense would fail later, somewhere inside the frame loop where the
 * cause is no longer visible, so every one of them is rejected at construction instead.
 */
class DanmakuStyleTest {

    @Test
    fun `the default style is the documented one`() {
        val style = DanmakuStyle.Default

        assertEquals(3, style.laneCount)
        assertEquals(Int.MAX_VALUE, style.maxVisible)
        assertEquals(DanmakuOverflowPolicy.Overlap, style.overflowPolicy)
        assertNull(style.textOutline)
    }

    @Test
    fun `a non positive lane count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DanmakuStyle(laneCount = 0) }
    }

    @Test
    fun `a non positive lane height is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DanmakuStyle(laneHeight = 0.dp) }
    }

    @Test
    fun `a non positive duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DanmakuStyle(durationMillis = 0L) }
    }

    @Test
    fun `a non positive maxVisible is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DanmakuStyle(maxVisible = 0) }
    }

    @Test
    fun `an outline needs a positive width`() {
        assertThrows(IllegalArgumentException::class.java) { DanmakuTextOutline(width = 0.dp) }

        assertEquals(2.dp, DanmakuTextOutline(width = 2.dp).width)
    }
}