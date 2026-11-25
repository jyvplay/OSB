// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * Production-grade zero-knowledge secret envelope & KDF policy validator.
 * Enforces Argon2id/PBKDF2 parameter floors, 12-byte AEAD nonce length,
 * and constant-time tag verification.
 */
class VaultEnvelopePolicy private constructor() {
    companion object {
        const val MIN_SALT_BYTES = 16
        const val AEAD_NONCE_BYTES = 12
        const val MIN_ARGON2_MEM_KIB = 19_456 // OWASP 19 MiB floor
        const val MIN_ARGON2_ITERS = 2
        const val MIN_PBKDF2_ITERS = 600_000

        fun isValidArgon2Params(memKib: Int, iterations: Int, parallelism: Int, saltLen: Int): Boolean =
            memKib >= MIN_ARGON2_MEM_KIB &&
                iterations >= MIN_ARGON2_ITERS &&
                parallelism in 1..16 &&
                saltLen >= MIN_SALT_BYTES

        fun isValidPbkdf2Params(iterations: Int, saltLen: Int): Boolean =
            iterations >= MIN_PBKDF2_ITERS && saltLen >= MIN_SALT_BYTES

        fun isValidAeadNonce(nonce: ByteArray): Boolean = nonce.size == AEAD_NONCE_BYTES

        fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
            if (a.size != b.size) return false
            var diff = 0
            for (i in a.indices) {
                diff = diff or (a[i].toInt() xor b[i].toInt())
            }
            return diff == 0
        }
    }
}
