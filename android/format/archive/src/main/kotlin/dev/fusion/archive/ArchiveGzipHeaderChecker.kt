// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Bounded 10-byte GZIP header magic, deflate method, and reserved-flag validator.
 */
class ArchiveGzipHeaderChecker private constructor() {
    companion object {
        fun isValid(header: ByteArray): Boolean {
            if (header.size < 10) return false
            if ((header[0].toInt() and 0xFF) != 0x1F) return false
            if ((header[1].toInt() and 0xFF) != 0x8B) return false
            if ((header[2].toInt() and 0xFF) != 0x08) return false // deflate
            val flags = header[3].toInt() and 0xFF
            return (flags and 0xE0) == 0 // reserved bits must be 0
        }
    }
}
