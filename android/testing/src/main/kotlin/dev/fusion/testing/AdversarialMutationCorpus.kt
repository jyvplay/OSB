// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

/**
 * Clean-room Deterministic Adversarial Mutation & Boundary Corpus Generator shared across
 * `:testing` host contract runners (`R49+`).
 *
 * Supplies reproducible hostile inputs — path-traversal probes, BiDi Trojan-Source overrides,
 * IEEE-754 edge floats (`-0.0`, subnormals, `NaN`, `±Infinity`), XXE entity payloads, and
 * truncated binary varint frames — using a seedable SplitMix64 PRNG (zero nondeterminism).
 */
object AdversarialMutationCorpus {
    val PATH_TRAVERSAL_VECTORS: List<String> = listOf(
        "../etc/passwd",
        "..\\..\\Windows\\System32\\cmd.exe",
        "subdir/../../../secret.key",
        "valid/path\u0000hidden.sh",
        "/absolute/escape/attempt",
        "...",
    )

    val BIDI_AND_CONTROL_VECTORS: List<String> = listOf(
        "invoice\u202Efdp.exe",          // RLO Trojan filename spoof (`invoiceexe.pdf` visually!)
        "safe\u001B[201~; rm -rf /\n",   // Bracketed-paste breakout attempt
        "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><a:t>&xxe;</a:t>",
    )

    val IEEE754_EDGE_DOUBLES: List<Double> = listOf(
        0.0,
        -0.0,
        Double.MIN_VALUE,
        -Double.MIN_VALUE,
        Double.MAX_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
    )

    class SplitMix64(seed: Long) {
        private var state: Long = seed

        fun nextLong(): Long {
            state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15uL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9uL
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EBuL
            return z xor (z ushr 31)
        }

        fun nextInt(boundExclusive: Int): Int {
            require(boundExclusive > 0) { "boundExclusive must be > 0" }
            return ((nextLong() ushr 1) % boundExclusive).toInt()
        }

        fun mutateBytes(input: ByteArray, flipCount: Int = 1): ByteArray {
            if (input.isEmpty()) return ByteArray(0)
            val copy = input.copyOf()
            repeat(flipCount.coerceAtLeast(1)) {
                val byteIdx = nextInt(copy.size)
                val bitIdx = nextInt(8)
                copy[byteIdx] = (copy[byteIdx].toInt() xor (1 shl bitIdx)).toByte()
            }
            return copy
        }
    }
}
