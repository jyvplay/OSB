// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room IETF **`RFC 5869` HMAC-based Extract-and-Expand Key Derivation Function (`HKDF-SHA256`)**.
 *
 * Used by `RFC 9180` HPKE / `RFC 9458` Oblivious HTTP, TLS 1.3, Signal Double-Ratchet chains, and
 * Vault domain-separated subkey derivation. Verified directly against official `RFC 5869` Appendix
 * A.1, A.2, and A.3 test vectors.
 */
object HkdfRfc5869 {
    const val SCOPE =
        "Clean-room IETF RFC 5869 HKDF-SHA256 (extract, expand up to 255*32 = 8160 bytes, and combined deriveKey)."

    const val HASH_LEN = 32
    const val MAX_OKM_BYTES = 255 * HASH_LEN

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val actualSalt = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmacSha256(actualSalt, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray = ByteArray(0), lengthBytes: Int): ByteArray {
        require(prk.size >= HASH_LEN) { "HKDF PRK must be >= $HASH_LEN bytes, got ${prk.size}" }
        require(lengthBytes in 0..MAX_OKM_BYTES) { "HKDF OKM length $lengthBytes outside 0..$MAX_OKM_BYTES" }
        if (lengthBytes == 0) return ByteArray(0)
        val n = (lengthBytes + HASH_LEN - 1) / HASH_LEN
        val okm = ByteArray(lengthBytes)
        var tPrev = ByteArray(0)
        var generated = 0
        for (counter in 1..n) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(tPrev)
            mac.update(info)
            mac.update(counter.toByte())
            tPrev = mac.doFinal()
            val copyLen = minOf(HASH_LEN, lengthBytes - generated)
            System.arraycopy(tPrev, 0, okm, generated, copyLen)
            generated += copyLen
        }
        return okm
    }

    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray, lengthBytes: Int): ByteArray =
        expand(extract(salt, ikm), info, lengthBytes)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
