package dev.fusion.vault

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Adi Shamir `(k, n)` Threshold Secret Sharing over `GF(2^8)` (`x^8 + x^4 + x^3 + x + 1`,
 * `0x11B`) with `HMAC-SHA256` Share Tamper Detection (Apache-2.0).
 *
 * Information-theoretically hides [secret] from any `k - 1` shares while reconstructing the exact
 * secret byte-for-byte from any `k` distinct valid shares via Lagrange interpolation at `x = 0`.
 */
object ShamirSecretVaultSplitter {
    const val SCOPE =
        "Shamir (k, n) threshold secret sharing over GF(2^8) (0x11B) with Lagrange x=0 reconstruction & HMAC-SHA256 share verification"

    data class SecretShare(
        val xCoord: Int, // 1..255
        val yBytes: ByteArray,
        val macTag16: ByteArray,
    ) {
        init {
            require(xCoord in 1..255) { "Share xCoord must be in 1..255" }
        }
    }

    fun split(
        secret: ByteArray,
        thresholdK: Int,
        totalSharesN: Int,
        verificationKey32: ByteArray,
        rng: SecureRandom = SecureRandom(),
    ): List<SecretShare> {
        require(secret.isNotEmpty() && secret.size <= 4096) { "Secret length must be 1..4096" }
        require(thresholdK in 2..totalSharesN && totalSharesN <= 255) { "Invalid (k=$thresholdK, n=$totalSharesN)" }
        require(verificationKey32.size == 32) { "verificationKey32 must be 32 bytes" }

        val shareYs = Array(totalSharesN) { ByteArray(secret.size) }
        val coeffs = ByteArray(thresholdK)
        for (byteIdx in secret.indices) {
            coeffs[0] = secret[byteIdx]
            val randTail = ByteArray(thresholdK - 1).also(rng::nextBytes)
            System.arraycopy(randTail, 0, coeffs, 1, randTail.size)
            for (sIdx in 0 until totalSharesN) {
                val x = sIdx + 1
                // Horner's method in GF(2^8)
                var acc = 0
                for (deg in thresholdK - 1 downTo 0) {
                    acc = gfMul(acc, x) xor (coeffs[deg].toInt() and 0xFF)
                }
                shareYs[sIdx][byteIdx] = acc.toByte()
            }
        }
        coeffs.fill(0)
        return List(totalSharesN) { sIdx ->
            val x = sIdx + 1
            SecretShare(x, shareYs[sIdx], computeShareMac(verificationKey32, x, shareYs[sIdx]))
        }
    }

    fun combine(shares: List<SecretShare>, thresholdK: Int, verificationKey32: ByteArray): ByteArray {
        require(shares.size >= thresholdK && thresholdK >= 2) { "Need >= $thresholdK shares, got ${shares.size}" }
        val chosen = shares.take(thresholdK)
        require(chosen.map { it.xCoord }.distinct().size == chosen.size) { "Duplicate share xCoords" }
        val len = chosen[0].yBytes.size
        for (s in chosen) {
            require(s.yBytes.size == len) { "Mismatched share payload lengths" }
            val expected = computeShareMac(verificationKey32, s.xCoord, s.yBytes)
            require(MessageDigest.isEqual(expected, s.macTag16)) { "Tampered Shamir share x=${s.xCoord}" }
        }

        val secret = ByteArray(len)
        for (byteIdx in 0 until len) {
            var sum = 0
            for (i in chosen.indices) {
                val xi = chosen[i].xCoord
                val yi = chosen[i].yBytes[byteIdx].toInt() and 0xFF
                var num = 1
                var den = 1
                for (j in chosen.indices) {
                    if (i == j) continue
                    val xj = chosen[j].xCoord
                    num = gfMul(num, xj)          // 0 xor xj == xj in GF(2^8)!
                    den = gfMul(den, xi xor xj)   // xi - xj == xi xor xj in GF(2^8)!
                }
                val basis = gfMul(num, gfInv(den))
                sum = sum xor gfMul(yi, basis)
            }
            secret[byteIdx] = sum.toByte()
        }
        return secret
    }

    private fun gfMul(a0: Int, b0: Int): Int {
        var a = a0 and 0xFF
        var b = b0 and 0xFF
        var p = 0
        repeat(8) {
            if ((b and 1) != 0) p = p xor a
            val hi = a and 0x80
            a = (a shl 1) and 0xFF
            if (hi != 0) a = a xor 0x1B // x^8 + x^4 + x^3 + x + 1
            b = b ushr 1
        }
        return p
    }

    /** Fermat's Little Theorem in `GF(2^8)*`: `a^-1 == a^254`. */
    private fun gfInv(a: Int): Int {
        require(a and 0xFF != 0) { "GF(2^8) division by zero" }
        var base = a and 0xFF
        var exp = 254
        var res = 1
        while (exp > 0) {
            if ((exp and 1) != 0) res = gfMul(res, base)
            base = gfMul(base, base)
            exp = exp ushr 1
        }
        return res
    }

    private fun computeShareMac(key32: ByteArray, x: Int, y: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key32, "HmacSHA256"))
        mac.update(x.toByte())
        return mac.doFinal(y).copyOf(16)
    }
}
