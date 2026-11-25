// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Bounded IEEE 802.3 CRC-32 calculator.
 */
class ArchiveZipCrc32Table private constructor() {
    companion object {
        fun crc32(data: ByteArray): Long {
            var crc = 0xFFFFFFFFL
            for (b in data) {
                crc = crc xor (b.toInt() and 0xFF).toLong()
                repeat(8) {
                    crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xEDB88320L else (crc ushr 1)
                }
            }
            return crc xor 0xFFFFFFFFL
        }
    }
}
