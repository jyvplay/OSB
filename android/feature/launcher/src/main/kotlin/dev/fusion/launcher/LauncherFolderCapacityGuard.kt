// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

/**
 * Bounded launcher folder capacity guard.
 */
class LauncherFolderCapacityGuard private constructor() {
    companion object {
        const val MAX_ITEMS = 48

        fun canAdd(currentCount: Int): Boolean {
            require(currentCount >= 0) { "negative count" }
            return currentCount < MAX_ITEMS
        }
    }
}
