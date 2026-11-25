// SPDX-License-Identifier: Apache-2.0
package dev.fusion.continuity

/**
 * Bounded 6-digit pairing PIN verifier with attempt lockout.
 */
class ContinuityPairCodeVerifier private constructor(
    private val expectedPin: String,
    private val maxAttempts: Int
) {
    companion object {
        private val PIN_RE = Regex("^\\d{6}$")

        fun create(expectedPin: String, maxAttempts: Int = 5): ContinuityPairCodeVerifier {
            require(PIN_RE.matches(expectedPin)) { "PIN must be 6 digits" }
            require(maxAttempts in 1..20) { "maxAttempts out of bounds" }
            return ContinuityPairCodeVerifier(expectedPin, maxAttempts)
        }
    }

    private var attempts = 0
    private var locked = false

    fun verify(candidate: String): Boolean {
        if (locked) return false
        attempts++
        if (attempts >= maxAttempts && candidate != expectedPin) {
            locked = true
            return false
        }
        if (candidate.length != expectedPin.length) return false
        var diff = 0
        for (i in expectedPin.indices) {
            diff = diff or (expectedPin[i].code xor candidate[i].code)
        }
        return diff == 0
    }

    fun isLocked(): Boolean = locked
}
