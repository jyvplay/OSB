// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

/**
 * Clean-room systematic Reed-Solomon $(k, m)$ erasure coder over $\text{GF}(2^8)$ (`ReedSolomonGf256`).
 *
 * Powers `:feature:backup` bit-rot resilience on removable USB/SD media:
 * - Field arithmetic over $\text{GF}(2^8)$ with primitive polynomial $x^8 + x^4 + x^3 + x^2 + 1$ (`0x11D`).
 * - Constructs a $(k + m) \times k$ systematic Cauchy encoding matrix (top $k \times k$ rows = Identity $I_k$,
 *   bottom $m \times k$ rows = Cauchy $1 / (x_i \oplus y_j)$ over distinct elements of $\text{GF}(2^8)$,
 *   whose every square submatrix is strictly invertible!).
 * - Recovers ALL $k$ original data shards from ANY $k$ surviving shards out of $k + m$ via Gauss-Jordan
 *   matrix inversion over $\text{GF}(2^8)$!
 */
class ReedSolomonGf256(
    val dataShards: Int,
    val parityShards: Int,
) {
    companion object {
        const val SCOPE =
            "Systematic Cauchy Reed-Solomon (k, m) erasure coder over GF(2^8) (poly 0x11D, recovers any m lost data/parity shards up to k+m <= 255); SIMD GFNI vectorization out of scope."

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

        fun gfAdd(a: Int, b: Int): Int = (a xor b) and 0xFF

        fun gfMul(a: Int, b: Int): Int {
            if (a == 0 || b == 0) return 0
            return EXP[LOG[a and 0xFF] + LOG[b and 0xFF]]
        }

        fun gfInv(a: Int): Int {
            require(a and 0xFF != 0) { "GF(256) zero has no multiplicative inverse" }
            return EXP[255 - LOG[a and 0xFF]]
        }
    }

    val totalShards: Int = dataShards + parityShards
    private val encodeMatrix: Array<IntArray>

    init {
        require(dataShards >= 1 && parityShards >= 1 && totalShards <= 255) {
            "Invalid Reed-Solomon geometry ($dataShards, $parityShards)"
        }
        // Top k rows = Identity; bottom m rows = Cauchy matrix 1 / ((k + r) xor c)
        encodeMatrix = Array(totalShards) { r ->
            IntArray(dataShards) { c ->
                if (r < dataShards) {
                    if (r == c) 1 else 0
                } else {
                    gfInv((r xor c) and 0xFF)
                }
            }
        }
    }

    fun encode(data: List<ByteArray>): List<ByteArray> {
        require(data.size == dataShards) { "Expected $dataShards data shards, got ${data.size}" }
        val shardLen = data.first().size
        require(shardLen > 0 && data.all { it.size == shardLen }) { "All shards must have equal positive length" }

        val out = ArrayList<ByteArray>(totalShards)
        for (d in data) out.add(d.copyOf())
        for (p in 0 until parityShards) {
            val row = encodeMatrix[dataShards + p]
            val parity = ByteArray(shardLen)
            for (c in 0 until dataShards) {
                val coeff = row[c]
                val src = data[c]
                for (bIdx in 0 until shardLen) {
                    val prod = gfMul(coeff, src[bIdx].toInt() and 0xFF)
                    parity[bIdx] = (parity[bIdx].toInt() xor prod).toByte()
                }
            }
            out.add(parity)
        }
        return out
    }

    fun reconstructData(shards: List<ByteArray?>): List<ByteArray> {
        require(shards.size == totalShards) { "Expected $totalShards shard slots" }
        val presentIndices = shards.indices.filter { shards[it] != null }.take(dataShards)
        require(presentIndices.size == dataShards) {
            "Insufficient surviving shards (${presentIndices.size} < $dataShards)"
        }
        val shardLen = shards[presentIndices.first()]!!.size
        require(presentIndices.all { shards[it]!!.size == shardLen }) { "Mismatched surviving shard lengths" }

        // Fast path: all k data shards survived intact.
        if (presentIndices == (0 until dataShards).toList()) {
            return presentIndices.map { shards[it]!!.copyOf() }
        }

        val subMatrix = Array(dataShards) { r -> encodeMatrix[presentIndices[r]].copyOf() }
        val decodeMatrix = invertGf256(subMatrix)
        val surviving = presentIndices.map { shards[it]!! }

        return List(dataShards) { dIdx ->
            if (shards[dIdx] != null) {
                shards[dIdx]!!.copyOf()
            } else {
                val recovered = ByteArray(shardLen)
                val row = decodeMatrix[dIdx]
                for (c in 0 until dataShards) {
                    val coeff = row[c]
                    val src = surviving[c]
                    for (bIdx in 0 until shardLen) {
                        val prod = gfMul(coeff, src[bIdx].toInt() and 0xFF)
                        recovered[bIdx] = (recovered[bIdx].toInt() xor prod).toByte()
                    }
                }
                recovered
            }
        }
    }

    private fun invertGf256(matrix: Array<IntArray>): Array<IntArray> {
        val n = matrix.size
        val aug = Array(n) { r ->
            IntArray(2 * n) { c ->
                if (c < n) matrix[r][c] else if (c - n == r) 1 else 0
            }
        }
        for (col in 0 until n) {
            var pivot = col
            while (pivot < n && aug[pivot][col] == 0) pivot++
            check(pivot < n) { "Singular Reed-Solomon submatrix at col $col" }
            if (pivot != col) {
                val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            }
            val invPivot = gfInv(aug[col][col])
            for (j in 0 until 2 * n) aug[col][j] = gfMul(aug[col][j], invPivot)
            for (r in 0 until n) {
                if (r != col && aug[r][col] != 0) {
                    val factor = aug[r][col]
                    for (j in 0 until 2 * n) {
                        aug[r][j] = gfAdd(aug[r][j], gfMul(factor, aug[col][j]))
                    }
                }
            }
        }
        return Array(n) { r -> IntArray(n) { c -> aug[r][n + c] } }
    }
}
