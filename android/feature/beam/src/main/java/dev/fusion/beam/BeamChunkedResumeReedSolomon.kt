// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Clean-room Beam Resumable Chunk Bitmap Tracker & Systematic XOR Erasure Parity Block Codec for
 * Lossy Local Wi-Fi Direct / mDNS Transfers.
 */
object BeamChunkedResumeReedSolomon {
    const val SCOPE =
        "Pure-JVM Beam resumable transfer bitset tracker and systematic parity block encoder/reconstructor recovering dropped LAN chunks."

    fun missingChunkIndices(receivedBitset: BooleanArray): List<Int> =
        receivedBitset.indices.filter { !receivedBitset[it] }

    fun computeParityShard(dataShards: List<ByteArray>): ByteArray {
        require(dataShards.isNotEmpty()) { "at least 1 data shard required" }
        val shardLen = dataShards.first().size
        require(shardLen > 0 && dataShards.all { it.size == shardLen }) { "shards must have equal non-zero length" }
        val parity = ByteArray(shardLen)
        for (s in dataShards) {
            for (i in 0 until shardLen) {
                parity[i] = (parity[i].toInt() xor s[i].toInt()).toByte()
            }
        }
        return parity
    }

    fun recoverSingleMissingShard(shardsOrNull: List<ByteArray?>, parityShard: ByteArray): ByteArray {
        val missingCount = shardsOrNull.count { it == null }
        require(missingCount == 1) { "systematic single-parity recovery requires exactly 1 missing shard, got $missingCount" }
        val recovered = parityShard.copyOf()
        for (s in shardsOrNull) {
            if (s != null) {
                require(s.size == parityShard.size) { "shard length mismatch" }
                for (i in recovered.indices) {
                    recovered[i] = (recovered[i].toInt() xor s[i].toInt()).toByte()
                }
            }
        }
        return recovered
    }
}
