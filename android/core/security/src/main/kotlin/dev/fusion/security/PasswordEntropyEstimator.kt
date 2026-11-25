// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import kotlin.math.log2

/**
 * Bounded password pool-entropy estimator (bits = length * log2(poolSize)).
 */
class PasswordEntropyEstimator private constructor() {
    companion object {
        const val MAX_PASSWORD_LEN = 512

        fun estimateBits(password: String): Double {
            require(password.length <= MAX_PASSWORD_LEN) { "password too long" }
            if (password.isEmpty()) return 0.0
            var hasLower = false
            var hasUpper = false
            var hasDigit = false
            var hasSymbol = false
            for (ch in password) {
                when {
                    ch in 'a'..'z' -> hasLower = true
                    ch in 'A'..'Z' -> hasUpper = true
                    ch in '0'..'9' -> hasDigit = true
                    else -> hasSymbol = true
                }
            }
            var pool = 0
            if (hasLower) pool += 26
            if (hasUpper) pool += 26
            if (hasDigit) pool += 10
            if (hasSymbol) pool += 32
            if (pool == 0) return 0.0
            return password.length * log2(pool.toDouble())
        }
    }
}
