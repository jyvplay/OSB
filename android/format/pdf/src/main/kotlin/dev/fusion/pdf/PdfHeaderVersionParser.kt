// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

/**
 * Bounded `%PDF-X.Y` header version parser.
 */
class PdfHeaderVersionParser private constructor() {
    companion object {
        private val HDR_RE = Regex("^%PDF-([12])\\.([0-7])$")

        fun parseVersion(headerLine: String): Pair<Int, Int>? {
            val m = HDR_RE.matchEntire(headerLine.trimEnd('\r', '\n')) ?: return null
            return m.groupValues[1].toInt() to m.groupValues[2].toInt()
        }
    }
}
