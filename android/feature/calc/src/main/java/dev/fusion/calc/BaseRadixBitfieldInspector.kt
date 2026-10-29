// SPDX-License-Identifier: Apache-2.0
package dev.fusion.calc

/**
 * Clean-room Programmer 64-Bit Bitfield Slice Extractor, Endian Byte-Swapper, and IEEE-754
 * `binary32`/`binary64` Bit-Level Inspector for `:feature:calc` (`PR-09`).
 */
object BaseRadixBitfieldInspector {
    data class BitfieldSummary(
        val rawBits: Long,
        val hex64: String,
        val popCount: Int,
        val leadingZeros: Int,
        val trailingZeros: Int,
        val byteSwappedLittleBig: Long,
    )

    data class Ieee754Binary64Decomposition(
        val signBit: Int,
        val biasedExponent: Int,
        val unbiasedExponent: Int,
        val mantissaBits52: Long,
        val classification: String, // "ZERO", "SUBNORMAL", "NORMAL", "INFINITY", "NAN"
    )

    fun inspectWord64(value: Long): BitfieldSummary = BitfieldSummary(
        rawBits = value,
        hex64 = "0x" + java.lang.Long.toUnsignedString(value, 16).uppercase().padStart(16, '0'),
        popCount = java.lang.Long.bitCount(value),
        leadingZeros = java.lang.Long.numberOfLeadingZeros(value),
        trailingZeros = java.lang.Long.numberOfTrailingZeros(value),
        byteSwappedLittleBig = java.lang.Long.reverseBytes(value),
    )

    fun extractUnsignedSlice(word: Long, highBitInclusive: Int, lowBitInclusive: Int): Long {
        require(lowBitInclusive in 0..63 && highBitInclusive in lowBitInclusive..63) {
            "invalid bit slice [$highBitInclusive:$lowBitInclusive]"
        }
        val width = highBitInclusive - lowBitInclusive + 1
        val shifted = word ushr lowBitInclusive
        return if (width == 64) shifted else shifted and ((1L shl width) - 1L)
    }

    fun decomposeBinary64(value: Double): Ieee754Binary64Decomposition {
        val raw = java.lang.Double.doubleToRawLongBits(value)
        val sign = ((raw ushr 63) and 1L).toInt()
        val biased = ((raw ushr 52) and 0x7FFL).toInt()
        val mant = raw and 0x000F_FFFF_FFFF_FFFFL
        val cls = when {
            biased == 0 && mant == 0L -> "ZERO"
            biased == 0 -> "SUBNORMAL"
            biased == 0x7FF && mant == 0L -> "INFINITY"
            biased == 0x7FF -> "NAN"
            else -> "NORMAL"
        }
        val unbiased = if (biased == 0) -1022 else biased - 1023
        return Ieee754Binary64Decomposition(sign, biased, unbiased, mant, cls)
    }
}
