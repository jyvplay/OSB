// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

/**
 * Bounded unsigned LEB128 varint encoder/decoder (up to 63-bit non-negative Long).
 */
class Leb128VarintCodec private constructor() {
    companion object {
        fun encode(value: Long): ByteArray {
            require(value >= 0L) { "negative value" }
            val out = mutableListOf<Byte>()
            var v = value
            do {
                var byte = (v and 0x7FL).toInt()
                v = v ushr 7
                if (v != 0L) byte = byte or 0x80
                out.add(byte.toByte())
            } while (v != 0L)
            return out.toByteArray()
        }

        fun decode(bytes: ByteArray): Long {
            require(bytes.isNotEmpty() && bytes.size <= 9) { "invalid varint length" }
            var result = 0L
            var shift = 0
            for (i in bytes.indices) {
                val b = bytes[i].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                require(shift < 63) { "varint overflow" }
            }
            throw IllegalArgumentException("unterminated varint")
        }
    }
}
