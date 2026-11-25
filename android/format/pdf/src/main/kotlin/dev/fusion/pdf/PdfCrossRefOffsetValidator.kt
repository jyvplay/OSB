// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

/**
 * Bounded PDF xref byte-offset validator.
 */
class PdfCrossRefOffsetValidator private constructor() {
    companion object {
        fun areValidOffsets(offsets: List<Long>, fileSize: Long): Boolean {
            require(fileSize >= 0L) { "negative fileSize" }
            var prev = -1L
            for (off in offsets) {
                if (off < 0L || off >= fileSize || off <= prev) return false
                prev = off
            }
            return true
        }
    }
}
