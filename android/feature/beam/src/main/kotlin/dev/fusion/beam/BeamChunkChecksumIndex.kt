// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Bounded chunk SHA-256 hex digest index for beam transfers.
 */
class BeamChunkChecksumIndex private constructor(private val totalChunks: Int) {
    companion object {
        const val MAX_CHUNKS = 65_536
        private val HEX64_RE = Regex("^[0-9a-f]{64}$")

        fun create(totalChunks: Int): BeamChunkChecksumIndex {
            require(totalChunks in 1..MAX_CHUNKS) { "totalChunks out of bounds" }
            return BeamChunkChecksumIndex(totalChunks)
        }
    }

    private val digests = HashMap<Int, String>()

    fun record(chunkIndex: Int, sha256Hex: String) {
        require(chunkIndex in 0 until totalChunks) { "chunkIndex out of bounds" }
        val norm = sha256Hex.lowercase()
        require(HEX64_RE.matches(norm)) { "invalid sha256 hex" }
        digests[chunkIndex] = norm
    }

    fun isComplete(): Boolean = digests.size == totalChunks
    fun recordedCount(): Int = digests.size
}
