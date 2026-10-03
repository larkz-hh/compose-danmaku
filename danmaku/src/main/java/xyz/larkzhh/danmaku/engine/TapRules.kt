package xyz.larkzhh.danmaku.engine

/**
 * What a tap should do to the current selection.
 */
internal enum class TapAction {
    /** Nothing is selected and nothing was hit, so nothing changes. */
    Ignore,

    /** An entry was hit that is not the pinned one, so it takes over the selection. */
    Select,

    /** The selection should be cleared. */
    Clear,
}

/**
 * Decides what a tap does to the selection.
 *
 * Two kinds of tap clear it, and both are deliberate. A tap on empty space means "close this". So does a tap
 * on the entry that is already pinned: that is how every danmaku client behaves, and without it a pinned entry
 * would have no gesture of its own to release it, leaving the reader to find empty space or wait for the entry
 * to be dropped.
 *
 * @param hitId Id of the entry the tap landed on, or `null` when it missed every entry.
 * @param selectedId Id of the entry currently pinned, or `null` when nothing is selected.
 */
internal fun tapAction(hitId: Long?, selectedId: Long?): TapAction = when {
    hitId == null -> if (selectedId == null) TapAction.Ignore else TapAction.Clear
    hitId == selectedId -> TapAction.Clear
    else -> TapAction.Select
}