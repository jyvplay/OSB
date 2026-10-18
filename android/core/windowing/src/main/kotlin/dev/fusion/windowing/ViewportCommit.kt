// SPDX-License-Identifier: Apache-2.0
package dev.fusion.windowing

/** Admit a persistent viewport only from a focused, unobscured native window.
 * Coordinates are PHYSICAL PIXELS, not dp. One-pixel edge tolerance is for rounding only.
 * This checks observed facts, not their honesty under a compromised OS. No timers/retries.
 */
object ViewportCommit {
    data class Pixels(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        internal fun valid(): Boolean =
            listOf(left, top, right, bottom).all { it in -1_000_000..1_000_000 } &&
                right.toLong() - left in 1..100_000L && bottom.toLong() - top in 1..100_000L
    }
    fun admit(focused: Boolean, imeVisible: Boolean, usable: Pixels?, visible: Pixels?): Boolean {
        if (!focused || imeVisible || usable == null || visible == null || !usable.valid() || !visible.valid()) return false
        return visible.left.toLong() <= usable.left.toLong() + 1 &&
            visible.top.toLong() <= usable.top.toLong() + 1 &&
            visible.right.toLong() >= usable.right.toLong() - 1 &&
            visible.bottom.toLong() >= usable.bottom.toLong() - 1
    }
}
