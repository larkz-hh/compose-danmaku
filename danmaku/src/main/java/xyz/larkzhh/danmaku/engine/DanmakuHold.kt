package xyz.larkzhh.danmaku.engine

/**
 * How far each entry's drawing is delayed, because the user once held that entry still.
 *
 * Releasing a selection lets the entry carry on from where it stopped rather than snapping forward to where
 * the playback timeline has since reached. Those two are the same thing: an entry whose drawing is delayed by
 * `d` resumes exactly where it was held. So the whole feature is one number per entry.
 *
 * The delay applies to drawing only. Placement - which lane an entry gets - is computed from
 * [xyz.larkzhh.danmaku.DanmakuItem.timeMs] and is never told about it, so an entry that was held keeps the
 * lane it was given. Folding the delay into `timeMs` instead would send the entry back through lane
 * allocation, where it now arrives later, finds its lane taken and is moved to another one.
 *
 * Pure bookkeeping in milliseconds, so the rules below are covered on the JVM.
 */
internal class DanmakuHold {
    private val shifts = HashMap<Long, Long>()

    /** Milliseconds the drawing of [id] is delayed by, or `0` for an entry that was never held. */
    fun shiftMs(id: Long): Long = shifts[id] ?: 0L

    /**
     * Settles a release of [id]: whatever it was held for is added to its delay, so it picks up where it was.
     *
     * A release that lands before the moment the entry was frozen is not a release but a seek backwards in
     * time, and is ignored - there is no such thing as carrying on from a position the timeline has not
     * reached. [clear] is what a seek calls.
     */
    fun settle(id: Long, frozenAtMs: Long, releasedAtMs: Long) {
        val held = releasedAtMs - frozenAtMs
        if (held <= 0L) return
        shifts[id] = (shifts[id] ?: 0L) + held
    }

    /**
     * Drops every entry outside [ids].
     *
     * A delay is only ever read back for an entry still in the list, so bounding this by that list is both
     * enough and necessary: an entry that has left can never be drawn again.
     */
    fun retainOnly(ids: Set<Long>) {
        shifts.keys.retainAll(ids)
    }

    /** Forgets every delay, which is what seeking the timeline does. */
    fun clear() {
        shifts.clear()
    }

    /** How many entries are held. */
    val size: Int get() = shifts.size
}
