package xyz.larkzhh.danmaku.engine

import androidx.compose.ui.unit.TextUnit

/**
 * Holds one value per key across calls, and lets go of the ones the latest call did not ask for.
 *
 * A placement pass measures one layout per entry, and the overlay reruns that pass whenever the entry list
 * changes. Danmaku arrive one at a time, so a cache that does not outlive a pass makes every arrival
 * re-measure the whole list: quadratic over a session, on the one number that keeps growing.
 *
 * Keyed generically on purpose. The values a placement pass stores need a device to build; the bookkeeping
 * does not, so keeping them apart lets the hit and eviction rules be verified on the JVM.
 */
internal class MeasurementCache<K : Any, V : Any> {
    private val values = HashMap<K, V>()

    /** The value for [key], building it with [build] only when it is not held already. */
    fun getOrBuild(key: K, build: () -> V): V = values.getOrPut(key) { build() }

    /**
     * Drops every value outside [keys].
     *
     * The bound is the caller's live keys rather than a capacity. An entry that has left the list can never
     * be asked for again, so a capacity would either hold layouts nothing can reach or evict one that is
     * still drawn; the live key set is exactly what is still reachable.
     */
    fun retainOnly(keys: Set<K>) {
        values.keys.retainAll(keys)
    }

    /** How many values are held. */
    val size: Int get() = values.size
}

/**
 * What a measured layout depends on: the text, and the size it is measured at.
 *
 * Colour is deliberately absent. Compose caches layouts under layout-affecting attributes only, and colour
 * is not one of them, so two entries of the same text at the same size share a layout whatever colour they
 * are drawn in. Keying on colour here would only fragment the cache.
 *
 * Everything else that reaches a layout - the font, the weight, the letter spacing - comes from
 * `DanmakuStyle.textStyle` and is therefore identical for every entry of a pass. That is why the overlay
 * drops the whole cache when the style changes instead of carrying the style in the key.
 */
internal data class MeasurementKey(
    val text: String,
    val fontSize: TextUnit,
)
