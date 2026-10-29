// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

/**
 * Pure-JVM **Deterministic Shrinking Property-Based Fuzz Generator (`SplitMix64`)** for `:testing`,
 * generating reproducible adversarial byte arrays, URLs, and spreadsheet coordinates and shrinking
 * failing counterexamples to minimal inputs!
 */
object DeterministicFuzzShrinker {
    const val SCOPE =
        "SplitMix64 deterministic property-based fuzz input generator and binary-chop minimal counterexample shrinker."

    class SplitMix64(private var state: Long) {
        fun nextLong(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        fun nextInt(bound: Int): Int {
            require(bound > 0)
            return ((nextLong() ushr 1) % bound.toLong()).toInt()
        }

        fun nextBytes(len: Int): ByteArray = ByteArray(len) { (nextLong() and 0xFFL).toByte() }
    }

    fun shrinkFailingString(failingInput: String, failsPredicate: (String) -> Boolean): String {
        require(failsPredicate(failingInput)) { "Initial input must satisfy failsPredicate" }
        var cur = failingInput
        var chunk = cur.length / 2
        while (chunk >= 1) {
            var pos = 0
            var shrunkInPass = false
            while (pos + chunk <= cur.length) {
                val candidate = cur.removeRange(pos, pos + chunk)
                if (failsPredicate(candidate)) {
                    cur = candidate
                    shrunkInPass = true
                } else {
                    pos += chunk
                }
            }
            if (!shrunkInPass) chunk /= 2 else chunk = (cur.length / 2).coerceAtLeast(1).coerceAtMost(chunk)
            if (cur.isEmpty()) break
        }
        return cur
    }
}
