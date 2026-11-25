// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Production-grade RFC 5869 HKDF-SHA256 key derivation (Extract + Expand).
 */
class SecurityHkdfSha256 private constructor() {
    companion object {
        const val HASH_LEN = 32
        const val MAX_OKM_LEN = 255 * HASH_LEN

        private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val k = if (key.isEmpty()) ByteArray(HASH_LEN) else key
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(k, "HmacSHA256"))
            return mac.doFinal(data)
        }

        fun extract(salt: ByteArray, ikm: ByteArray): ByteArray = hmacSha256(salt, ikm)

        fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
            require(prk.size >= HASH_LEN) { "PRK too short" }
            require(length in 1..MAX_OKM_LEN) { "OKM length out of bounds" }
            val n = (length + HASH_LEN - 1) / HASH_LEN
            val okm = ByteArray(length)
            var t = ByteArray(0)
            var offset = 0
            for (i in 1..n) {
                val input = ByteArray(t.size + info.size + 1)
                System.arraycopy(t, 0, input, 0, t.size)
                System.arraycopy(info, 0, input, t.size, info.size)
                input[input.size - 1] = i.toByte()
                t = hmacSha256(prk, input)
                val copyLen = minOf(HASH_LEN, length - offset)
                System.arraycopy(t, 0, okm, offset, copyLen)
                offset += copyLen
            }
            return okm
        }
    }
}
