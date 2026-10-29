// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JVM **RFC 5869 `HKDF-SHA256` (`Extract` + `Expand`) Domain-Separated Subkey Deriver**
 * for `:core:security`, deriving cryptographically independent subkeys for Recall encrypted
 * custom vaults, Tails persistent containers, and messaging attachments from a single master IKM!
 */
object Rfc5869HkdfSha256 {
    const val SCOPE =
        "IETF RFC 5869 HKDF-SHA256 Extract-and-Expand domain-separated cryptographic key derivation engine."

    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        return hmacSha256(effectiveSalt, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, outLengthBytes: Int): ByteArray {
        require(prk.size >= 32 && outLengthBytes in 1..(255 * 32)) {
            "Invalid HKDF PRK size or outLengthBytes=$outLengthBytes"
        }
        val out = ByteArray(outLengthBytes)
        var tPrev = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < outLengthBytes) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(tPrev)
            mac.update(info)
            mac.update(counter.toByte())
            tPrev = mac.doFinal()
            val copyLen = minOf(tPrev.size, outLengthBytes - generated)
            System.arraycopy(tPrev, 0, out, generated, copyLen)
            generated += copyLen
            counter++
        }
        return out
    }

    fun deriveKey(ikm: ByteArray, salt: ByteArray, contextLabel: String, outLengthBytes: Int = 32): ByteArray =
        expand(extract(salt, ikm), contextLabel.toByteArray(Charsets.UTF_8), outLengthBytes)

    private fun hmacSha256(key: ByteArray, msg: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg)
    }
}
