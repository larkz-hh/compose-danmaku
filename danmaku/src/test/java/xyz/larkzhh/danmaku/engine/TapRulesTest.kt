package xyz.larkzhh.danmaku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the tap decision behind the selection.
 *
 * The whole truth table fits in four cases, and the two that clear the selection are the ones worth pinning
 * down: without them a pinned entry has no gesture left that releases it.
 */
class TapRulesTest {

    @Test
    fun `a tap on empty space with nothing selected does nothing`() {
        assertEquals(TapAction.Ignore, tapAction(hitId = null, selectedId = null))
    }

    @Test
    fun `a tap on empty space clears a selection`() {
        assertEquals(TapAction.Clear, tapAction(hitId = null, selectedId = 7L))
    }

    @Test
    fun `a tap on another entry takes over the selection`() {
        assertEquals(TapAction.Select, tapAction(hitId = 8L, selectedId = 7L))
        assertEquals(TapAction.Select, tapAction(hitId = 8L, selectedId = null))
    }

    @Test
    fun `a tap on the pinned entry releases it`() {
        assertEquals(TapAction.Clear, tapAction(hitId = 7L, selectedId = 7L))
    }
}