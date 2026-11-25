// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

/**
 * Bounded temporary test directory name guard.
 */
class TestTempDirNameGuard private constructor() {
    companion object {
        private val NAME_RE = Regex("^[a-zA-Z0-9._-]{1,64}$")

        fun isSafe(name: String): Boolean =
            name != "." && name != ".." && NAME_RE.matches(name)
    }
}
