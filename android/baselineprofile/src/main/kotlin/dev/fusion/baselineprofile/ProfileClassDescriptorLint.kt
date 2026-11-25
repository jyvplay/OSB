// SPDX-License-Identifier: Apache-2.0
package dev.fusion.baselineprofile

/**
 * Bounded DEX/ART class descriptor validator (`Lpkg/ClassName;`).
 */
class ProfileClassDescriptorLint private constructor() {
    companion object {
        const val MAX_DESC_LEN = 256
        private val DESC_RE = Regex("^L[a-zA-Z0-9_/$]+;$")

        fun isValid(descriptor: String): Boolean {
            if (descriptor.isEmpty() || descriptor.length > MAX_DESC_LEN) return false
            return DESC_RE.matches(descriptor)
        }
    }
}
