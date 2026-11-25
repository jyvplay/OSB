// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

/**
 * Bounded ELF header magic, class (32/64), and endianness validator.
 */
class NativeElfMagicChecker private constructor() {
    companion object {
        fun isValidElfHeader(header: ByteArray): Boolean {
            if (header.size < 6) return false
            if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
                header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()) return false
            val eiClass = header[4].toInt() and 0xFF
            val eiData = header[5].toInt() and 0xFF
            return (eiClass == 1 || eiClass == 2) && (eiData == 1 || eiData == 2)
        }
    }
}
