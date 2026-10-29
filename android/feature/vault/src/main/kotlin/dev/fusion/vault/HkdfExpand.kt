// SPDX-License-Identifier: Apache-2.0
package dev.fusion.vault

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function (`HKDF-SHA256`)
 * for `:feature:vault` domain-separated subkey derivation (vault metadata key, per-item AEAD
 * subkey, attachment MAC key).
 *
 * Verified byte-for-byte against all three normative RFC 5869 Appendix A.1–A.3 SHA-256 test
 * vectors:
 * - [extract]: `PRK = HMAC-SHA256(salt_or_32_zero_bytes, IKM)` (§2.2),
 * - [expand]: `T(i) = HMAC-SHA256(PRK, T(i-1) || info || byte(i))` for `i = 1..ceil(L / 32)` (§2.3),
 *   enforcing `PRK.size >= 32` and `1 <= length <= 255 * 32 = 8160`.
 */
object HkdfExpand {
    const val SCOPE =
        "RFC 5869 HKDF-SHA256 extract, expand, and deriveKey (L in 1..8160); password-hardening Argon2id/PBKDF2 and Android Keystore hardware-backed keys out of scope."

    const val HASH_LEN = 32
    const val MAX_OKM_BYTES = 255 * HASH_LEN

    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        require(ikm.isNotEmpty()) { "HKDF input keying material (IKM) must not be empty" }
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmacSha256(effectiveSalt, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(prk.size >= HASH_LEN) { "HKDF PRK length ${prk.size} shorter than HashLen=$HASH_LEN" }
        require(length in 1..MAX_OKM_BYTES) {
            "HKDF output length $length outside 1..$MAX_OKM_BYTES (255 * HashLen)"
        }
        val n = (length + HASH_LEN - 1) / HASH_LEN
        val okm = ByteArray(length)
        var tPrev = ByteArray(0)
        var outOffset = 0

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        for (counter in 1..n) {
            mac.reset()
            if (tPrev.isNotEmpty()) mac.update(tPrev)
            if (info.isNotEmpty()) mac.update(info)
            mac.update(counter.toByte())
            tPrev = mac.doFinal()
            val copyLen = minOf(HASH_LEN, length - outOffset)
            System.arraycopy(tPrev, 0, okm, outOffset, copyLen)
            outOffset += copyLen
        }
        return okm
    }

    fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray =
        expand(extract(salt, ikm), info, length)

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
