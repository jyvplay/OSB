// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

import java.security.MessageDigest

/**
 * Domain-Separated **SHA-256 Binary Merkle-Tree Backup Restore Verifier** (`RFC 6962` `0x00` leaf /
 * `0x01` interior node domain separation preventing second-preimage tree-height attacks!).
 */
object ChunkedRestoreVerifier {
    const val SCOPE =
        "RFC 6962 domain-separated (0x00 leaf / 0x01 node) SHA-256 Merkle-tree backup manifest & per-chunk inclusion proof verifier."

    data class ProofStep(val siblingHash: ByteArray, val siblingIsLeft: Boolean)

    fun leafHash(chunkIndex: Int, chunkBytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(0x00.toByte())
        md.update(
            byteArrayOf(
                ((chunkIndex ushr 24) and 0xFF).toByte(),
                ((chunkIndex ushr 16) and 0xFF).toByte(),
                ((chunkIndex ushr 8) and 0xFF).toByte(),
                (chunkIndex and 0xFF).toByte(),
            ),
        )
        return md.digest(chunkBytes)
    }

    fun interiorHash(left: ByteArray, right: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(0x01.toByte())
        md.update(left)
        return md.digest(right)
    }

    fun computeMerkleRoot(chunks: List<ByteArray>): ByteArray {
        require(chunks.isNotEmpty()) { "backup requires >= 1 chunk" }
        var level = chunks.mapIndexed { idx, bytes -> leafHash(idx, bytes) }
        while (level.size > 1) {
            val next = ArrayList<ByteArray>((level.size + 1) / 2)
            var i = 0
            while (i < level.size) {
                if (i + 1 < level.size) next.add(interiorHash(level[i], level[i + 1]))
                else next.add(level[i]) // RFC 6962 odd promotion without self-duplication vulnerability!
                i += 2
            }
            level = next
        }
        return level[0]
    }

    fun buildInclusionProof(chunks: List<ByteArray>, targetIndex: Int): List<ProofStep> {
        require(targetIndex in chunks.indices) { "targetIndex $targetIndex out of bounds" }
        val proof = ArrayList<ProofStep>()
        var idx = targetIndex
        var level = chunks.mapIndexed { i, b -> leafHash(i, b) }
        while (level.size > 1) {
            if (idx % 2 == 1) {
                proof.add(ProofStep(level[idx - 1], siblingIsLeft = true))
            } else if (idx + 1 < level.size) {
                proof.add(ProofStep(level[idx + 1], siblingIsLeft = false))
            }
            val next = ArrayList<ByteArray>((level.size + 1) / 2)
            var i = 0
            while (i < level.size) {
                if (i + 1 < level.size) next.add(interiorHash(level[i], level[i + 1]))
                else next.add(level[i])
                i += 2
            }
            idx /= 2
            level = next
        }
        return proof
    }

    fun verifyChunk(chunkIndex: Int, chunkBytes: ByteArray, proof: List<ProofStep>, expectedRoot: ByteArray): Boolean {
        var cur = leafHash(chunkIndex, chunkBytes)
        for (step in proof) {
            cur = if (step.siblingIsLeft) interiorHash(step.siblingHash, cur)
            else interiorHash(cur, step.siblingHash)
        }
        if (cur.size != expectedRoot.size) return false
        var diff = 0
        for (i in cur.indices) diff = diff or ((cur[i].toInt() and 0xFF) xor (expectedRoot[i].toInt() and 0xFF))
        return diff == 0
    }
}
