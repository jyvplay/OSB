// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * R51 `:feature:messages` ISO/IEC 7816-4 (`0x80 0x00*`) constant-block traffic-analysis padding codec.
 *
 * Ensures message ciphertexts are bucketed to exact multiples of `blockSize` bytes (`16..65,536`)
 * by appending an unconditional `0x80` terminator byte followed by zero bytes, preventing fine-grained
 * ciphertext-length side channels while remaining 100% invertible for arbitrary binary payloads.
 */
object SealedSenderPaddingEnvelope {
    const val SCOPE = "ISO/IEC 7816-4 0x80 message length bucketing codec; local byte framing only."

    fun padIso7816(payload: ByteArray, blockSize: Int = 256): ByteArray {
        require(blockSize in 16..65_536) { "Invalid ISO 7816-4 blockSize=$blockSize" }
        val minNeeded = payload.size + 1
        val paddedLen = ((minNeeded + blockSize - 1) / blockSize) * blockSize
        val out = ByteArray(paddedLen)
        System.arraycopy(payload, 0, out, 0, payload.size)
        out[payload.size] = 0x80.toByte()
        return out
    }

    fun unpadIso7816(padded: ByteArray, blockSize: Int = 256): ByteArray {
        require(padded.isNotEmpty() && padded.size % blockSize == 0) {
            "Padded buffer (${padded.size}) is not a positive multiple of blockSize=$blockSize"
        }
        var idx = padded.lastIndex
        while (idx >= 0 && padded[idx] == 0.toByte()) idx--
        require(idx >= 0 && (padded[idx].toInt() and 0xFF) == 0x80) { "Missing ISO/IEC 7816-4 0x80 delimiter" }
        return padded.copyOfRange(0, idx)
    }

    /** Returns the number of fixed-size ISO/IEC 7816-4 buckets required for [payloadLength] bytes. */
    fun bucketCount(payloadLength: Int, blockSize: Int = 256): Int {
        require(payloadLength >= 0 && blockSize in 16..65_536) { "Invalid bucket sizing parameters" }
        return ((payloadLength + 1 + blockSize - 1) / blockSize).coerceAtLeast(1)
    }
}
