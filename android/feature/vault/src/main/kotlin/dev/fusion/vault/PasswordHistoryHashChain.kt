// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

/**
 * R146 — bounded password-history reuse gate (owner:v2.0 SC-01 passwords;
 * pairs with [PwPolicy], which owns composition rules this kernel never
 * duplicates).
 *
 * FHC2 uses the JCA PBKDF2WithHmacSHA256 implementation, 600,000 iterations,
 * 256-bit output, and a domain-separated 32-byte caller-supplied salt. The
 * class name is retained but this is NOT a homemade iterated SHA hash/KDF.
 * Salt generation, protected persistence, rate limiting and worker-thread
 * dispatch belong to the integrating vault (not supplied here). Use a random
 * per-vault salt retained for its history; changing it invalidates reuse checks.
 * This is not the authentication store and is not a memory-hard KDF.
 *
 * Password: 1..1024 UTF-16 units; at most 12 lowercase hex hashes. FORMAT and
 * ITERATIONS must be persisted by callers; no legacy FHC1 migration is claimed.
 * Strings may remain in JVM memory; no secure-erasure guarantee is made.
 */
object PasswordHistoryHashChain {
    const val SCOPE =
        "FHC2 PBKDF2-HMAC-SHA256 password-history reuse gate; no plaintext retention, no composition policy, no network."

    const val FORMAT = "FHC2"
    const val ITERATIONS = 600_000
    const val MAX_HISTORY = 12
    const val MAX_PASSWORD = 1024

    private val HEX64 = Regex("[0-9a-f]{64}")

    fun hash(password: String, saltHex: String): String {
        require(password.length in 1..MAX_PASSWORD) { "password length out of range" }
        require(saltHex.length == 64) { "salt must be 32 bytes of hex" }
        val salt = decodeHex(saltHex)
        val chars = password.toCharArray()
        val keySpec = javax.crypto.spec.PBEKeySpec(
            chars, FORMAT.toByteArray(Charsets.US_ASCII) + salt, ITERATIONS, 256,
        )
        chars.fill('\u0000')
        return try {
            encodeHex(javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec).encoded)
        } finally {
            keySpec.clearPassword()
        }
    }

    /**
     * `admit` lane: `admit;saltHex;history;password` where history is a
     * comma-joined list of 64-char lowercase hex hashes (possibly empty).
     * Returns `ok;<newHistory>` with the password hash appended, or
     * `reuse;<index>` when the hash is already retained (index 0-based).
     */
    fun admit(saltHex: String, historySpec: String, password: String): String {
        val history = if (historySpec.isEmpty()) emptyList() else historySpec.split(',')
        require(history.size <= MAX_HISTORY) { "history over $MAX_HISTORY entries" }
        for (h in history) require(HEX64.matches(h)) { "bad history hash" }
        val h = hash(password, saltHex)
        // Walk all retained entries, not an early-return byte comparison.
        var idx = -1
        val candidate = decodeHex(h)
        for (i in history.indices) {
            val equal = java.security.MessageDigest.isEqual(candidate, decodeHex(history[i]))
            if (equal && idx < 0) idx = i
        }
        if (idx >= 0) return "reuse;$idx"
        val next = history + h
        val pruned = if (next.size > MAX_HISTORY) next.subList(next.size - MAX_HISTORY, next.size) else next
        return "ok;" + pruned.joinToString(",")
    }

    private fun decodeHex(s: String): ByteArray {
        require(s.length % 2 == 0) { "hex length must be even" }
        require(s.isNotEmpty()) { "empty hex" }
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = hexVal(s[i * 2])
            val lo = hexVal(s[i * 2 + 1])
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("non-hex character")
    }

    private fun encodeHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append("%02x".format(x))
        return sb.toString()
    }
}
