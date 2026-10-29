// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

import java.security.MessageDigest

/**
 * Pure-JVM **Merkle-Chunked Resumable LAN File-Transfer Session Verifier** for `:feature:beam`,
 * computing binary SHA-256 Merkle roots over fixed-size blocks so interrupted P2P transfers
 * resume only corrupted or missing leaf chunks!
 */
object BeamMerkleChunkVerifier {
    const val SCOPE =
        "Beam P2P LAN binary SHA-256 Merkle-tree chunk verifier and missing/corrupted chunk bitmask planner."

    data class MerkleManifest(
        val totalByteLength: Long,
        val chunkSize: Int,
        val leafHashesHex: List<String>,
        val merkleRootHex: String,
    )

    fun buildManifest(payload: ByteArray, chunkSize: Int = 65_536): MerkleManifest {
        require(chunkSize >= 256) { "chunkSize must be >= 256" }
        if (payload.isEmpty()) {
            val emptyRoot = sha256Hex(ByteArray(0))
            return MerkleManifest(0L, chunkSize, emptyList(), emptyRoot)
        }
        val leaves = ArrayList<ByteArray>()
        var off = 0
        while (off < payload.size) {
            val end = minOf(off + chunkSize, payload.size)
            leaves.add(sha256(payload.copyOfRange(off, end)))
            off = end
        }
        val leafHex = leaves.map { toHex(it) }
        val root = computeRoot(leaves)
        return MerkleManifest(payload.size.toLong(), chunkSize, leafHex, toHex(root))
    }

    fun findMissingOrCorruptChunks(manifest: MerkleManifest, receivedChunks: Map<Int, ByteArray>): List<Int> {
        val needed = ArrayList<Int>()
        for (idx in manifest.leafHashesHex.indices) {
            val got = receivedChunks[idx]
            if (got == null || sha256Hex(got) != manifest.leafHashesHex[idx]) {
                needed.add(idx)
            }
        }
        return needed
    }

    private fun computeRoot(level: List<ByteArray>): ByteArray {
        var cur = level
        while (cur.size > 1) {
            val next = ArrayList<ByteArray>((cur.size + 1) / 2)
            var i = 0
            while (i < cur.size) {
                val left = cur[i]
                val right = if (i + 1 < cur.size) cur[i + 1] else left
                next.add(sha256(left + right))
                i += 2
            }
            cur = next
        }
        return cur.first()
    }

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    private fun toHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun sha256Hex(b: ByteArray): String = toHex(sha256(b))
}
