// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Bounded LOK page margin validator.
 */
class LokPageMarginValidator private constructor() {
    companion object {
        const val MAX_MARGIN_TWIPS = 14400 // 10 inches

        fun areValid(top: Int, right: Int, bottom: Int, left: Int): Boolean =
            top in 0..MAX_MARGIN_TWIPS &&
                right in 0..MAX_MARGIN_TWIPS &&
                bottom in 0..MAX_MARGIN_TWIPS &&
                left in 0..MAX_MARGIN_TWIPS
    }
}
