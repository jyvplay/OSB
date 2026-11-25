// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capture

/**
 * Bounded capture FPS frame pacer.
 */
class CaptureFpsFramePacer private constructor() {
    companion object {
        const val NS_PER_SEC = 1_000_000_000L

        fun frameIntervalNs(fps: Int): Long {
            require(fps in 1..240) { "fps out of bounds: $fps" }
            return NS_PER_SEC / fps.toLong()
        }

        fun isFrameDropped(deltaNs: Long, fps: Int): Boolean {
            require(deltaNs >= 0L) { "negative deltaNs" }
            return deltaNs > frameIntervalNs(fps)
        }
    }
}
