// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

/**
 * Bounded PSD color mode code validator.
 */
class PsdColorModeValidator private constructor() {
    companion object {
        val VALID_MODES = setOf(0, 1, 2, 3, 4, 7, 8, 9)

        fun isSupported(mode: Int): Boolean = mode in VALID_MODES
    }
}
