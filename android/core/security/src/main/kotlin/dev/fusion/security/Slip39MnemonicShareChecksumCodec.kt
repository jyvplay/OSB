// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * SatoshiLabs **SLIP-0039** RS1024 (`GF(1024)` Reed-Solomon 3-word customization-string `"shamir"`
 * checksum) verifier + `GF(256)` (`x^8 + x^4 + x^3 + x + 1`) threshold secret share splitter/combiner
 * for offline Fusion Vault emergency recovery shares!
 */
object Slip39MnemonicShareChecksumCodec {
    const val SCOPE =
        "SLIP-0039 RS1024 ('shamir' customization) 10-bit word checksum codec and GF(256) k-of-n Shamir threshold secret combiner."

    private val GEN = intArrayOf(
        0x00e0e040, 0x01c1c080, 0x03838100, 0x07070200, 0x0e0e0009,
        0x1c0c2412, 0x38086c24, 0x3090fc48, 0x21b1f890, 0x03f3f120,
    )

    fun createRs1024ChecksumWords(dataWords10Bit: List<Int>, customization: String = "shamir"): List<Int> {
        require(dataWords10Bit.all { it in 0..1023 })
        val values = customization.map { it.code and 0x7F } + dataWords10Bit + listOf(0, 0, 0)
        val poly = rs1024Polymod(values) xor 1
        return listOf((poly ushr 20) and 0x3FF, (poly ushr 10) and 0x3FF, poly and 0x3FF)
    }

    fun verifyRs1024ShareWords(allWordsWith3WordChecksum: List<Int>, customization: String = "shamir"): Boolean {
        if (allWordsWith3WordChecksum.size < 4 || allWordsWith3WordChecksum.any { it !in 0..1023 }) return false
        return rs1024Polymod(customization.map { it.code and 0x7F } + allWordsWith3WordChecksum) == 1
    }

    private fun rs1024Polymod(values: List<Int>): Int {
        var chk = 1
        for (v in values) {
            val b = chk ushr 20
            chk = ((chk and 0xFFFFF) shl 10) xor v
            for (i in 0 until 10) {
                if (((b ushr i) and 1) != 0) chk = chk xor GEN[i]
            }
        }
        return chk
    }

    // GF(256) over Rijndael irreducible polynomial 0x11B for exact byte-wise Lagrange threshold secret recovery
    fun splitSecretGf256(secret: ByteArray, thresholdK: Int, totalSharesN: Int, deterministicSeed: Long): Map<Int, ByteArray> {
        require(thresholdK in 2..totalSharesN && totalSharesN <= 16 && secret.isNotEmpty())
        var rng = deterministicSeed xor -0x543210fedcba9876L
        fun nextByteNonZero(): Int {
            while (true) {
                rng = rng * 6364136223846793005L + 1442695040888963407L
                val b = ((rng ushr 32).toInt()) and 0xFF
                if (b != 0) return b
            }
        }
        val shares = (1..totalSharesN).associateWith { ByteArray(secret.size) }
        for (byteIdx in secret.indices) {
            val coeffs = IntArray(thresholdK)
            coeffs[0] = secret[byteIdx].toInt() and 0xFF
            for (d in 1 until thresholdK) coeffs[d] = nextByteNonZero()
            for (x in 1..totalSharesN) {
                var y = 0
                for (d in (thresholdK - 1) downTo 0) {
                    y = gf256Mul(y, x) xor coeffs[d]
                }
                shares[x]!![byteIdx] = y.toByte()
            }
        }
        return shares
    }

    fun combineSharesGf256(sharesByX: Map<Int, ByteArray>): ByteArray {
        require(sharesByX.size >= 2)
        val xs = sharesByX.keys.toList()
        val len = sharesByX.values.first().size
        require(sharesByX.values.all { it.size == len })
        val out = ByteArray(len)
        for (byteIdx in 0 until len) {
            var secretByte = 0
            for (i in xs.indices) {
                val xi = xs[i]
                val yi = sharesByX[xi]!![byteIdx].toInt() and 0xFF
                var num = 1
                var den = 1
                for (j in xs.indices) {
                    if (i == j) continue
                    val xj = xs[j]
                    num = gf256Mul(num, xj)
                    den = gf256Mul(den, xi xor xj)
                }
                val basis = gf256Mul(num, gf256Inv(den))
                secretByte = secretByte xor gf256Mul(yi, basis)
            }
            out[byteIdx] = secretByte.toByte()
        }
        return out
    }

    private fun gf256Mul(aIn: Int, bIn: Int): Int {
        var a = aIn and 0xFF; var b = bIn and 0xFF; var p = 0
        repeat(8) {
            if ((b and 1) != 0) p = p xor a
            val hi = a and 0x80
            a = (a shl 1) and 0xFF
            if (hi != 0) a = a xor 0x1B
            b = b ushr 1
        }
        return p
    }

    private fun gf256Inv(a: Int): Int {
        require(a != 0)
        // a^254 in GF(256)
        var res = 1; var base = a; var exp = 254
        while (exp > 0) {
            if ((exp and 1) != 0) res = gf256Mul(res, base)
            base = gf256Mul(base, base)
            exp = exp ushr 1
        }
        return res
    }
}
