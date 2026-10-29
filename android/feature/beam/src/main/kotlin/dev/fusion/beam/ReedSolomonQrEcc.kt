// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Clean-room **GF(256) (`x^8 + x^4 + x^3 + x^2 + 1 = 0x11D`) QR Reed-Solomon ECC Encoder &
 * Syndrome Corruption Detector** for Air-Gapped Optical QR Payload Framing (`ISO/IEC 18004`).
 */
object ReedSolomonQrEcc {
    const val SCOPE =
        "Clean-room ISO/IEC 18004 GF(256) (poly 0x11D) Reed-Solomon parity encoder and syndrome corruption verifier."

    private const val PRIM_POLY = 0x11D
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor PRIM_POLY
        }
        LOG[0] = 0
    }

    fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a and 0xFF] + LOG[b and 0xFF]]
    }

    fun generatorPolynomial(eccBytes: Int): IntArray {
        require(eccBytes in 2..68) { "eccBytes $eccBytes outside 2..68" }
        var poly = intArrayOf(1)
        for (i in 0 until eccBytes) {
            poly = polyMul(poly, intArrayOf(1, EXP[i]))
        }
        return poly
    }

    fun encodeBlock(data: ByteArray, eccBytes: Int): ByteArray {
        require(data.isNotEmpty() && data.size + eccBytes <= 255) {
            "RS(255) block total length ${data.size + eccBytes} exceeds 255"
        }
        val gen = generatorPolynomial(eccBytes)
        val rem = IntArray(eccBytes)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor rem[0]
            System.arraycopy(rem, 1, rem, 0, eccBytes - 1)
            rem[eccBytes - 1] = 0
            if (factor != 0) {
                for (j in 0 until eccBytes) {
                    rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
                }
            }
        }
        val codeword = ByteArray(data.size + eccBytes)
        System.arraycopy(data, 0, codeword, 0, data.size)
        for (i in 0 until eccBytes) codeword[data.size + i] = rem[i].toByte()
        return codeword
    }

    /** Evaluates all `eccBytes` syndromes `S_i = C(alpha^i)`: returns `true` iff all syndromes are `0`! */
    fun verifyCodewordClean(codeword: ByteArray, eccBytes: Int): Boolean {
        require(codeword.size > eccBytes && codeword.size <= 255) { "invalid codeword length" }
        for (i in 0 until eccBytes) {
            var syn = 0
            val root = EXP[i]
            for (b in codeword) {
                syn = gfMul(syn, root) xor (b.toInt() and 0xFF)
            }
            if (syn != 0) return false
        }
        return true
    }

    private fun polyMul(p: IntArray, q: IntArray): IntArray {
        val out = IntArray(p.size + q.size - 1)
        for (i in p.indices) {
            for (j in q.indices) {
                out[i + j] = out[i + j] xor gfMul(p[i], q[j])
            }
        }
        return out
    }
}
