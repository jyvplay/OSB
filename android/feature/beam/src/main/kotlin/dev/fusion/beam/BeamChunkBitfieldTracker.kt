// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Production-grade chunked P2P transfer resume & bitfield tracker.
 */
class BeamChunkBitfieldTracker private constructor(val totalChunks: Int) {
    companion object {
        const val MAX_CHUNKS = 1_000_000

        fun create(totalChunks: Int): BeamChunkBitfieldTracker {
            require(totalChunks in 1..MAX_CHUNKS) { "totalChunks out of bounds" }
            return BeamChunkBitfieldTracker(totalChunks)
        }
    }

    private val received = BooleanArray(totalChunks)
    var receivedCount = 0
        private set

    fun markReceived(index: Int) {
        require(index in 0 until totalChunks) { "chunk index out of bounds" }
        if (!received[index]) {
            received[index] = true
            receivedCount++
        }
    }

    fun missingIndices(limit: Int = 64): List<Int> {
        val out = mutableListOf<Int>()
        for (i in 0 until totalChunks) {
            if (!received[i]) {
                out.add(i)
                if (out.size == limit) break
            }
        }
        return out
    }

    fun isComplete(): Boolean = receivedCount == totalChunks
}
