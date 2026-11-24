package dev.fusion.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 key derivation with HMAC-SHA256 (RFC 2898 / PKCS#5 v2.0).
 *
 * Bounds (per call):
 *   - password length <= 1,024 UTF-16 code units
 *   - salt length <= 1,024 bytes
 *   - iterations between 1 and 1,000,000 inclusive
 *   - derived key length between 1 and 65,536 bytes inclusive
 *
 * Never returns a weak or zero-length result. Throws IllegalArgumentException
 * for out-of-bounds inputs; propagation of provider exceptions is intentional
 * (caller must handle). Constant-time comparison of derived keys is NOT
 * provided here — callers must use a constant-time compare for verification.
 */
object Pbkdf2KeyDerivation {
    private const val MIN_PASSWORD = 1
    private const val MAX_PASSWORD = 1_024
    private const val MIN_SALT = 1
    private const val MAX_SALT = 1_024
    private const val MIN_ITERATIONS = 1
    private const val MAX_ITERATIONS = 1_000_000
    private const val MIN_KEY_LEN = 1
    private const val MAX_KEY_LEN = 65_536
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    data class DerivedKey(
        val key: ByteArray,
        val salt: ByteArray,
        val iterations: Int
    )

    /**
     * Derive a key from a password and salt. Returns the derived key, the salt
     * used (caller-supplied, echoed for storage), and the iteration count.
     */
    fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): DerivedKey {
        require(password.length in MIN_PASSWORD..MAX_PASSWORD) {
            "Password length ${password.length} outside [$MIN_PASSWORD, $MAX_PASSWORD]"
        }
        require(salt.size in MIN_SALT..MAX_SALT) {
            "Salt length ${salt.size} outside [$MIN_SALT, $MAX_SALT]"
        }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "Iterations $iterations outside [$MIN_ITERATIONS, $MAX_ITERATIONS]"
        }
        require(keyLength in MIN_KEY_LEN..MAX_KEY_LEN) {
            "Key length $keyLength outside [$MIN_KEY_LEN, $MAX_KEY_LEN]"
        }

        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLength * 8)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val derived = factory.generateSecret(spec).encoded

        return DerivedKey(
            key = derived,
            salt = salt.copyOf(),
            iterations = iterations
        )
    }

    /**
     * Generate a cryptographically random salt of the given byte length.
     * Length bounds: [MIN_SALT, MAX_SALT].
     */
    fun generateSalt(length: Int): ByteArray {
        require(length in MIN_SALT..MAX_SALT) { "Salt length $length outside [$MIN_SALT, $MAX_SALT]" }
        val salt = ByteArray(length)
        SecureRandom.getInstanceStrong().nextBytes(salt)
        return salt
    }

    /**
     * Verify a candidate password against a previously derived key.
     * Returns true if the derived key matches. Uses the stored salt and iterations.
     */
    fun verify(
        password: String,
        stored: DerivedKey,
        keyLength: Int
    ): Boolean {
        val derived = derive(password, stored.salt, stored.iterations, keyLength)
        return derived.key.contentEquals(stored.key)
    }
}
