// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room IETF **RFC 8439** §2.3 / §2.4 **ChaCha20** 20-round ARX 64-byte keystream block
 * function and stream XOR cipher (`"expand 32-byte k"`, 256-bit key, 32-bit block counter, 96-bit nonce).
 */
object ChaCha20Rfc8439StreamBlock {
    const val SCOPE =
        "IETF RFC 8439 §2.3/§2.4 ChaCha20 20-round quarter-round 64-byte block generator and symmetric stream cipher."

    fun chacha20Block(key32: ByteArray, counter: Int, nonce12: ByteArray): ByteArray {
        require(key32.size == 32 && nonce12.size == 12)
        val init = IntArray(16)
        init[0] = 0x61707865
        init[1] = 0x3320646e
        init[2] = 0x79622d32
        init[3] = 0x6b206574
        val kb = ByteBuffer.wrap(key32).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 8) init[4 + i] = kb.int
        init[12] = counter
        val nb = ByteBuffer.wrap(nonce12).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 3) init[13 + i] = nb.int

        val w = init.copyOf()
        repeat(10) {
            // Column rounds
            quarterRound(w, 0, 4, 8, 12); quarterRound(w, 1, 5, 9, 13)
            quarterRound(w, 2, 6, 10, 14); quarterRound(w, 3, 7, 11, 15)
            // Diagonal rounds
            quarterRound(w, 0, 5, 10, 15); quarterRound(w, 1, 6, 11, 12)
            quarterRound(w, 2, 7, 8, 13); quarterRound(w, 3, 4, 9, 14)
        }
        val out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16) out.putInt(w[i] + init[i])
        return out.array()
    }

    fun xorStream(key32: ByteArray, initialCounter: Int, nonce12: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var off = 0
        var blk = initialCounter
        while (off < input.size) {
            val ks = chacha20Block(key32, blk++, nonce12)
            val n = minOf(64, input.size - off)
            for (i in 0 until n) out[off + i] = (input[off + i].toInt() xor ks[i].toInt()).toByte()
            off += n
        }
        return out
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = Integer.rotateLeft(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = Integer.rotateLeft(s[b] xor s[c], 7)
    }
}
