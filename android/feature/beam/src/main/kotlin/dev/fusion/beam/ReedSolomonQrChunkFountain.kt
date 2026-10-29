package dev.fusion.beam

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Clean-room Systematic `GF(2^8)` Cauchy/Vandermonde Erasure-Coded Animated QR Fountain Framing
 * Codec (Apache-2.0) recovering air-gapped transfers despite arbitrary dropped camera frames!
 */
object ReedSolomonQrChunkFountain {
    const val SCOPE =
        "Systematic GF(2^8) erasure-coded QR frame fountain encoder/decoder recovering payloads from any k-of-(k+m) frames"

    data class QrFrame(
        val frameIndex: Int, // 0 until k = systematic data; k until k+m = parity
        val dataShardsK: Int,
        val sliceLen: Int,
        val originalByteLen: Int,
        val payloadSha256Prefix8: ByteArray,
        val shardBytes: ByteArray,
    )

    fun encodeFrames(payload: ByteArray, dataShardsK: Int, parityShardsM: Int): List<QrFrame> {
        require(payload.isNotEmpty() && dataShardsK in 1..64 && parityShardsM in 0..32 && dataShardsK + parityShardsM <= 128)
        val sliceLen = (payload.size + dataShardsK - 1) / dataShardsK
        val digest8 = MessageDigest.getInstance("SHA-256").digest(payload).copyOf(8)
        val data = Array(dataShardsK) { idx ->
            val buf = ByteArray(sliceLen)
            val off = idx * sliceLen
            if (off < payload.size) {
                System.arraycopy(payload, off, buf, 0, minOf(sliceLen, payload.size - off))
            }
            buf
        }
        val out = ArrayList<QrFrame>(dataShardsK + parityShardsM)
        for (i in 0 until dataShardsK) {
            out.add(QrFrame(i, dataShardsK, sliceLen, payload.size, digest8, data[i]))
        }
        // Cauchy matrix row r in GF(2^8): entry (r, c) = 1 / ((k + r) xor c) — every square submatrix is invertible!
        for (r in 0 until parityShardsM) {
            val pBuf = ByteArray(sliceLen)
            for (c in 0 until dataShardsK) {
                val coeff = gfInv((dataShardsK + r) xor c)
                for (b in 0 until sliceLen) {
                    pBuf[b] = (pBuf[b].toInt() xor gfMul(coeff, data[c][b].toInt() and 0xFF)).toByte()
                }
            }
            out.add(QrFrame(dataShardsK + r, dataShardsK, sliceLen, payload.size, digest8, pBuf))
        }
        return out
    }

    fun decodeFromReceived(received: List<QrFrame>): ByteArray {
        require(received.isNotEmpty())
        val k = received[0].dataShardsK
        val sliceLen = received[0].sliceLen
        val origLen = received[0].originalByteLen
        val distinct = received.distinctBy { it.frameIndex }.take(k)
        require(distinct.size == k) { "Need $k distinct QR frames to reconstruct, received ${distinct.size}" }

        // Build k x k generator submatrix M and invert in GF(2^8)
        val mat = Array(k) { rowIdx ->
            val fIdx = distinct[rowIdx].frameIndex
            IntArray(2 * k) { col ->
                when {
                    col < k -> if (fIdx < k) (if (fIdx == col) 1 else 0) else gfInv(fIdx xor col)
                    col - k == rowIdx -> 1
                    else -> 0
                }
            }
        }
        for (col in 0 until k) {
            var piv = (col until k).firstOrNull { mat[it][col] != 0 } ?: error("Singular Cauchy submatrix")
            val tmp = mat[col]; mat[col] = mat[piv]; mat[piv] = tmp
            val inv = gfInv(mat[col][col])
            for (j in col until 2 * k) mat[col][j] = gfMul(mat[col][j], inv)
            for (r in 0 until k) {
                if (r == col || mat[r][col] == 0) continue
                val f = mat[r][col]
                for (j in col until 2 * k) mat[r][j] = mat[r][j] xor gfMul(f, mat[col][j])
            }
        }

        val full = ByteBuffer.allocate(k * sliceLen)
        for (dataRow in 0 until k) {
            val recovered = ByteArray(sliceLen)
            for (src in 0 until k) {
                val coeff = mat[dataRow][k + src]
                if (coeff == 0) continue
                val shard = distinct[src].shardBytes
                for (b in 0 until sliceLen) {
                    recovered[b] = (recovered[b].toInt() xor gfMul(coeff, shard[b].toInt() and 0xFF)).toByte()
                }
            }
            full.put(recovered)
        }
        val result = full.array().copyOf(origLen)
        val actualDigest8 = MessageDigest.getInstance("SHA-256").digest(result).copyOf(8)
        require(actualDigest8.contentEquals(received[0].payloadSha256Prefix8)) { "Decoded QR payload SHA-256 mismatch" }
        return result
    }

    private fun gfMul(a0: Int, b0: Int): Int {
        var a = a0 and 0xFF; var b = b0 and 0xFF; var p = 0
        repeat(8) {
            if ((b and 1) != 0) p = p xor a
            val hi = a and 0x80
            a = (a shl 1) and 0xFF
            if (hi != 0) a = a xor 0x1B
            b = b ushr 1
        }
        return p
    }

    private fun gfInv(a: Int): Int {
        var base = a and 0xFF; var exp = 254; var res = 1
        while (exp > 0) {
            if ((exp and 1) != 0) res = gfMul(res, base)
            base = gfMul(base, base)
            exp = exp ushr 1
        }
        return res
    }
}
