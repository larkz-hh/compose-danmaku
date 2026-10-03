package xyz.larkzhh.danmaku.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the cache that carries measured layouts across placement passes.
 *
 * The reason the class exists is that a placement pass is rerun whenever the entry list changes, and danmaku
 * arrive one at a time. What has to hold, then, is that a repeated key is not built twice, and that an entry
 * which has left the list stops being held - the second one is what keeps a sliding window from growing
 * without bound.
 *
 * Instantiated over strings rather than over layouts: the bookkeeping is what is under test, and building a
 * `TextLayoutResult` needs a device.
 */
class MeasurementCacheTest {

    @Test
    fun `a key that was built already is not built again`() {
        val cache = MeasurementCache<String, String>()
        var builds = 0

        repeat(3) {
            assertEquals("value", cache.getOrBuild("key") { builds++; "value" })
        }

        assertEquals(1, builds)
    }

    @Test
    fun `each distinct key is built once`() {
        val cache = MeasurementCache<String, String>()
        var builds = 0

        cache.getOrBuild("a") { builds++; "A" }
        cache.getOrBuild("b") { builds++; "B" }
        cache.getOrBuild("a") { builds++; "A" }

        assertEquals(2, builds)
        assertEquals(2, cache.size)
    }

    @Test
    fun `retaining a key set drops everything outside it`() {
        val cache = MeasurementCache<String, String>()
        cache.getOrBuild("a") { "A" }
        cache.getOrBuild("b") { "B" }
        cache.getOrBuild("c") { "C" }

        cache.retainOnly(setOf("b"))

        assertEquals(1, cache.size)
        assertEquals("B", cache.getOrBuild("b") { "rebuilt" })
    }

    @Test
    fun `a key that was dropped is built again rather than served`() {
        val cache = MeasurementCache<String, String>()
        cache.getOrBuild("a") { "first" }
        cache.retainOnly(emptySet())

        assertEquals(0, cache.size)
        assertEquals("second", cache.getOrBuild("a") { "second" })
    }

    @Test
    fun `an empty key set empties the cache`() {
        val cache = MeasurementCache<String, String>()
        repeat(4) { cache.getOrBuild("key$it") { "value" } }

        cache.retainOnly(emptySet())

        assertEquals(0, cache.size)
    }
}
