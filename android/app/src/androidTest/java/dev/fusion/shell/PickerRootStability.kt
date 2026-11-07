// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/** Test-only pre-tap gate over observed accessibility rectangles; never a navigation receipt. */
internal class PickerRootStability(private val stableMs: Long = 250) {
    init { require(stableMs > 0) }
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val nonempty: Boolean get() = left < right && top < bottom
        fun contains(other: Bounds): Boolean = nonempty && other.nonempty && left <= other.left && top <= other.top &&
            right >= other.right && bottom >= other.bottom
    }
    private var previous: Pair<Bounds, Bounds>? = null
    private var since = 0L
    private var lastTime = -1L

    fun observe(nowMs: Long, root: Bounds?, target: Bounds?): Boolean {
        if (nowMs < lastTime || root == null || target == null || !root.contains(target)) {
            previous = null
            lastTime = nowMs
            return false
        }
        lastTime = nowMs
        val sample = root to target
        if (sample != previous) { previous = sample; since = nowMs; return false }
        return nowMs - since >= stableMs
    }
}
