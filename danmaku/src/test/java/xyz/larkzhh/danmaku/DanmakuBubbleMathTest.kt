package xyz.larkzhh.danmaku

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for where the default bubble puts itself.
 *
 * Pure geometry, so the two things that can actually go wrong are checked without a device: the plate must
 * stay inside the layer, and the arrow must still point at the entry once the plate has been pulled back.
 */
class DanmakuBubbleMathTest {

    private companion object {
        const val LAYER_WIDTH_PX = 1_000
        const val PLATE_WIDTH_PX = 200f
    }

    @Test
    fun `a plate under an entry in the middle is centred on it`() {
        val left = DanmakuBubbleMath.leftPx(
            entryCenterPx = 500f,
            plateWidthPx = PLATE_WIDTH_PX,
            layerWidthPx = LAYER_WIDTH_PX,
        )

        assertEquals(400f, left, 0f)
    }

    @Test
    fun `a plate under an entry near the left edge is pulled back to it`() {
        val left = DanmakuBubbleMath.leftPx(
            entryCenterPx = 20f,
            plateWidthPx = PLATE_WIDTH_PX,
            layerWidthPx = LAYER_WIDTH_PX,
        )

        assertEquals(0f, left, 0f)
    }

    @Test
    fun `a plate under an entry near the right edge is pulled inside the layer`() {
        val left = DanmakuBubbleMath.leftPx(
            entryCenterPx = 990f,
            plateWidthPx = PLATE_WIDTH_PX,
            layerWidthPx = LAYER_WIDTH_PX,
        )

        assertEquals(800f, left, 0f)
    }

    @Test
    fun `a plate wider than the layer stays at the left edge`() {
        val left = DanmakuBubbleMath.leftPx(
            entryCenterPx = 500f,
            plateWidthPx = 1_200f,
            layerWidthPx = LAYER_WIDTH_PX,
        )

        assertEquals(0f, left, 0f)
    }

    @Test
    fun `the arrow keeps pointing at the entry after the plate has been pulled back`() {
        val entryCenter = 20f
        val left = DanmakuBubbleMath.leftPx(entryCenter, PLATE_WIDTH_PX, LAYER_WIDTH_PX)

        // The plate is pushed to 0, so the arrow sits 20 px from its left edge. Deriving it from the plate's
        // own centre would put it at 100 px and aim it at nothing.
        assertEquals(20f, DanmakuBubbleMath.arrowCenterPx(entryCenter, left), 0f)
    }

    @Test
    fun `an unclamped plate points its arrow at its own centre`() {
        val entryCenter = 500f
        val left = DanmakuBubbleMath.leftPx(entryCenter, PLATE_WIDTH_PX, LAYER_WIDTH_PX)

        assertEquals(PLATE_WIDTH_PX / 2f, DanmakuBubbleMath.arrowCenterPx(entryCenter, left), 0f)
    }
}