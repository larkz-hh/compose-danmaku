package xyz.larkzhh.danmaku

/**
 * What happens to an entry that arrives while every lane is still busy.
 *
 * A layer never invents a lane: either the entry is drawn where it can be, or it is not drawn at all. Which
 * of the two is wanted depends on the content, so the choice is explicit.
 *
 * @see DanmakuStyle.overflowPolicy
 */
public enum class DanmakuOverflowPolicy {
    /**
     * Draw it on the lane that clears first, even though it overlaps an entry already there.
     *
     * The default. An overlapping comment is a cosmetic problem, while a missing one looks like a bug.
     */
    Overlap,

    /**
     * Drop it.
     *
     * Suits a busy layer where legibility matters more than showing every entry. Pair it with
     * [DanmakuStyle.maxVisible] to bound how much is drawn at once.
     */
    Drop,
}