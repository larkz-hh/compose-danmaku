package xyz.larkzhh.danmaku

/**
 * Playback timeline that [DanmakuOverlay] reads once per frame.
 *
 * The library depends on no player implementation, so wiring it to ExoPlayer is a single lambda:
 *
 * ```kotlin
 * val clock = DanmakuClock { player.currentPosition }
 * ```
 *
 * Returning a value that moves backwards is allowed and expected: positions are never assumed to be
 * monotonic, so seeking, replaying and a manually scrubbed preview all behave correctly.
 */
public fun interface DanmakuClock {
    /**
     * Current playback position in milliseconds.
     *
     * Called once per frame while the layer is visible, so it must be cheap and must not block.
     */
    public fun positionMs(): Long
}
