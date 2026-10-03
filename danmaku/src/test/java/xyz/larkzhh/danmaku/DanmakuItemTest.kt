package xyz.larkzhh.danmaku

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests for the validation [DanmakuItem] does on construction.
 *
 * Nothing here needs a device: the item is plain data, and the two fields that describe a per-entry size and
 * non-text content are the only ones that can be given a value which makes no sense.
 */
class DanmakuItemTest {

    @Test
    fun `an entry is plain by default`() {
        val item = DanmakuItem(id = 1L, text = "hello", timeMs = 0L)

        assertEquals(1f, item.scale, 0f)
        assertNull(item.width)
    }

    @Test
    fun `a zero or negative scale is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DanmakuItem(id = 1L, text = "hello", timeMs = 0L, scale = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DanmakuItem(id = 1L, text = "hello", timeMs = 0L, scale = -1f)
        }
    }

    @Test
    fun `a zero or negative width is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DanmakuItem(id = 1L, text = "hello", timeMs = 0L, width = 0.dp)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DanmakuItem(id = 1L, text = "hello", timeMs = 0L, width = (-4).dp)
        }
    }

    @Test
    fun `a declared width survives as declared`() {
        val item = DanmakuItem(id = 1L, text = "hello", timeMs = 0L, width = 40.dp)

        assertEquals(40.dp, item.width)
    }
}