// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded human-readable file size formatter (IEC 1024-based).
 */
class FileSizeFormatter private constructor() {
    companion object {
        private val UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

        fun formatIec(bytes: Long): String {
            require(bytes >= 0L) { "negative bytes: $bytes" }
            if (bytes < 1024L) return "$bytes B"
            var value = bytes.toDouble()
            var idx = 0
            while (value >= 1024.0 && idx < UNITS.size - 1) {
                value /= 1024.0
                idx++
            }
            return "%.1f %s".format(java.util.Locale.US, value, UNITS[idx])
        }
    }
}
