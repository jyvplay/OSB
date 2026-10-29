// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Monotonic **ULID** (Universally Unique Lexicographically Sortable Identifier — 48-bit epoch
 * millisecond timestamp + 80-bit monotonic entropy in 26-char Crockford Base32) for sortable
 * **Recall** archive items and journal events.
 */
object DeterministicUlidGenerator {
    const val SCOPE =
        "26-character Crockford Base32 monotonic ULID encoder/decoder preserving lexicographic chronological sort order."

    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    class MonotonicGenerator(seedEntropyHi16: Int = 0x42A7, seedEntropyLo64: Long = 0x1357_9BDF_2468_ACE0L) {
        private var lastMs: Long = -1L
        private var entHi16: Int = seedEntropyHi16 and 0xFFFF
        private var entLo64: Long = seedEntropyLo64

        @Synchronized
        fun nextUlid(timestampMillis: Long): String {
            require(timestampMillis in 0L..0xFFFF_FFFF_FFFFL) { "ULID timestamp out of 48-bit range" }
            if (timestampMillis == lastMs) {
                entLo64 += 1L
                if (entLo64 == 0L) entHi16 = (entHi16 + 1) and 0xFFFF
            } else {
                lastMs = timestampMillis
                // Mix deterministically via SplitMix64 step so consecutive milliseconds stay distinct
                var z = entLo64 + (timestampMillis xor -0x61c8864680b583ebL)
                z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
                z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
                entLo64 = z xor (z ushr 31)
                entHi16 = ((entLo64 ushr 48).toInt() xor entHi16) and 0xFFFF
            }
            return encode128(timestampMillis, entHi16, entLo64)
        }
    }

    fun encode128(timestampMillis48: Long, entropyHi16: Int, entropyLo64: Long): String {
        val chars = CharArray(26)
        // First 10 chars = 48-bit timestamp (top 2 bits zero)
        var t = timestampMillis48 and 0xFFFF_FFFF_FFFFL
        for (i in 9 downTo 0) {
            chars[i] = CROCKFORD[(t and 0x1FL).toInt()]
            t = t ushr 5
        }
        // Remaining 16 chars = 80 bits (entropyHi16 << 64 | entropyLo64)
        val bytes = ByteArray(10)
        bytes[0] = ((entropyHi16 ushr 8) and 0xFF).toByte()
        bytes[1] = (entropyHi16 and 0xFF).toByte()
        for (b in 0 until 8) {
            bytes[2 + b] = ((entropyLo64 ushr ((7 - b) * 8)) and 0xFFL).toByte()
        }
        var bitBuf = 0
        var bitCount = 0
        var outIdx = 10
        for (b in bytes) {
            bitBuf = (bitBuf shl 8) or (b.toInt() and 0xFF)
            bitCount += 8
            while (bitCount >= 5) {
                bitCount -= 5
                chars[outIdx++] = CROCKFORD[(bitBuf ushr bitCount) and 0x1F]
            }
        }
        return String(chars)
    }

    fun decodeTimestampMillis(ulid: String): Long {
        require(ulid.length == 26) { "ULID must be 26 chars" }
        var ts = 0L
        for (i in 0 until 10) {
            val idx = CROCKFORD.indexOf(ulid[i].uppercaseChar())
            require(idx >= 0) { "Invalid Crockford Base32 char '${ulid[i]}'" }
            ts = (ts shl 5) or idx.toLong()
        }
        return ts
    }
}
