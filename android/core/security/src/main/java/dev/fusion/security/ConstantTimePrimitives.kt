// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Side-channel-hardened constant-time byte comparison, branchless integer selection, conditional
 * array swap, and constant-time PKCS#7 padding verification.
 *
 * Eliminates early-exit data-dependent branches in tag/MAC/PIN/padding checks.
 */
object ConstantTimePrimitives {
    const val SCOPE =
        "Constant-time byte/slice equality, branchless ctSelectInt, conditional in-place swap, and constant-time PKCS#7 unpadding."

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        val maxLen = maxOf(a.size, b.size)
        var diff = a.size xor b.size
        for (i in 0 until maxLen) {
            val av = if (i < a.size) a[i].toInt() and 0xFF else 0
            val bv = if (i < b.size) b[i].toInt() and 0xFF else 0
            diff = diff or (av xor bv)
        }
        return diff == 0
    }

    /** Branchless select: returns [whenOne] if `bit == 1`, [whenZero] if `bit == 0` (`bit` must be `0` or `1`). */
    fun ctSelectInt(bit: Int, whenOne: Int, whenZero: Int): Int {
        require(bit == 0 || bit == 1) { "ctSelectInt bit must be 0 or 1, got $bit" }
        val mask = -bit
        return (whenZero and mask.inv()) or (whenOne and mask)
    }

    /** Constant-time conditional swap of equal-length byte arrays when `swapBit == 1`. */
    fun ctConditionalSwap(swapBit: Int, a: ByteArray, b: ByteArray) {
        require(swapBit == 0 || swapBit == 1) { "swapBit must be 0 or 1" }
        require(a.size == b.size) { "arrays must have identical length" }
        val mask = (-swapBit) and 0xFF
        for (i in a.indices) {
            val ai = a[i].toInt() and 0xFF
            val bi = b[i].toInt() and 0xFF
            val x = (ai xor bi) and mask
            a[i] = (ai xor x).toByte()
            b[i] = (bi xor x).toByte()
        }
    }

    /** Constant-time PKCS#7 padding validation (`blockSize` in `1..255`), returning unpadded copy or `null` without early exit. */
    fun unpadPkcs7ConstantTime(padded: ByteArray, blockSize: Int = 16): ByteArray? {
        require(blockSize in 1..255) { "invalid PKCS#7 blockSize $blockSize" }
        if (padded.isEmpty() || padded.size % blockSize != 0) return null
        val padLen = padded.last().toInt() and 0xFF
        // Valid padLen is in 1..blockSize.
        val lenOutOfRange = ((padLen - 1) ushr 31) or ((blockSize - padLen) ushr 31)
        var badByte = lenOutOfRange
        for (i in 0 until blockSize) {
            val idx = padded.size - 1 - i
            val b = padded[idx].toInt() and 0xFF
            // Check byte iff i < padLen -> (i - padLen) < 0 -> sign bit 1
            val shouldCheckMask = (i - padLen) shr 31
            badByte = badByte or ((b xor padLen) and shouldCheckMask)
        }
        return if (badByte != 0) null else padded.copyOfRange(0, padded.size - padLen)
    }
}
