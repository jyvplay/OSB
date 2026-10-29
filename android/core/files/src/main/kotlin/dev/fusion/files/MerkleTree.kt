// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

import java.security.MessageDigest

/**
 * Clean-room RFC 6962 §2.1 domain-separated SHA-256 Merkle Tree & logarithmic inclusion-proof
 * verifier for `:core:files` (content-addressed backup chunks, sandbox bundle verification,
 * and tamper-evident archive manifests).
 *
 * Implements the exact RFC 6962 §2.1.1 / §2.1.3 recursive largest-power-of-two (`k < n`) split:
 * - `MTH({}) = SHA-256(empty)`
 * - `MTH({d(0)}) = SHA-256(0x00 || d(0))`
 * - `MTH(D[n]) = SHA-256(0x01 || MTH(D[0:k]) || MTH(D[k:n]))` where `k` is the largest power
 *   of two strictly less than `n`.
 * Because leaves use `0x00` and interior nodes use `0x01`, second-preimage tree-height confusion
 * attacks are impossible by construction.
 */
object MerkleTree {
    const val SCOPE =
        "RFC 6962 §2.1 SHA-256 Merkle tree root (0x00 leaf / 0x01 node domain separation, largest-power-of-two split) and O(log N) inclusion proofs; consistency proofs between two tree sizes out of scope."

    const val HASH_BYTES = 32
    const val MAX_LEAVES = 65_536

    data class ProofStep(val siblingHash: ByteArray, val siblingOnLeft: Boolean) {
        init {
            require(siblingHash.size == HASH_BYTES) { "Merkle sibling hash must be $HASH_BYTES bytes" }
        }
    }

    fun leafHash(leafPayload: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(0x00.toByte())
        md.update(leafPayload)
        return md.digest()
    }

    fun nodeHash(leftHash: ByteArray, rightHash: ByteArray): ByteArray {
        require(leftHash.size == HASH_BYTES && rightHash.size == HASH_BYTES) {
            "nodeHash inputs must be $HASH_BYTES bytes"
        }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(0x01.toByte())
        md.update(leftHash)
        md.update(rightHash)
        return md.digest()
    }

    fun rootHash(leaves: List<ByteArray>): ByteArray {
        require(leaves.size <= MAX_LEAVES) { "leaf count ${leaves.size} exceeds $MAX_LEAVES" }
        if (leaves.isEmpty()) {
            return MessageDigest.getInstance("SHA-256").digest()
        }
        return mthRange(leaves, 0, leaves.size)
    }

    fun inclusionProof(leaves: List<ByteArray>, leafIndex: Int): List<ProofStep> {
        require(leaves.isNotEmpty() && leaves.size <= MAX_LEAVES) {
            "leaves size ${leaves.size} outside 1..$MAX_LEAVES"
        }
        require(leafIndex in leaves.indices) { "leafIndex $leafIndex outside 0..${leaves.size - 1}" }
        val steps = ArrayList<ProofStep>()
        buildAuditPath(leaves, 0, leaves.size, leafIndex, steps)
        return steps
    }

    fun verifyInclusion(
        leafPayload: ByteArray,
        leafIndex: Int,
        treeSize: Int,
        expectedRootHash: ByteArray,
        proof: List<ProofStep>,
    ): Boolean {
        require(expectedRootHash.size == HASH_BYTES) { "expectedRootHash must be $HASH_BYTES bytes" }
        if (treeSize <= 0 || leafIndex !in 0 until treeSize) return false
        val expectedOrientation = expectedSidesBottomUp(leafIndex, treeSize)
        if (proof.size != expectedOrientation.size) return false

        var cur = leafHash(leafPayload)
        for (i in proof.indices) {
            val step = proof[i]
            if (step.siblingOnLeft != expectedOrientation[i]) return false
            cur = if (step.siblingOnLeft) nodeHash(step.siblingHash, cur) else nodeHash(cur, step.siblingHash)
        }
        return MessageDigest.isEqual(cur, expectedRootHash)
    }

    fun toHex(hash: ByteArray): String = buildString(hash.size * 2) {
        val alphabet = "0123456789abcdef"
        for (b in hash) {
            val v = b.toInt() and 0xFF
            append(alphabet[v ushr 4])
            append(alphabet[v and 0x0F])
        }
    }

    private fun mthRange(leaves: List<ByteArray>, from: Int, to: Int): ByteArray {
        val n = to - from
        if (n == 1) return leafHash(leaves[from])
        val k = largestPowerOfTwoLessThan(n)
        return nodeHash(mthRange(leaves, from, from + k), mthRange(leaves, from + k, to))
    }

    /** Appends bottom-up (`PATH(m, D[n])` per RFC 6962 §2.1.1). */
    private fun buildAuditPath(
        leaves: List<ByteArray>,
        from: Int,
        to: Int,
        targetAbs: Int,
        out: MutableList<ProofStep>,
    ) {
        val n = to - from
        if (n == 1) return
        val k = largestPowerOfTwoLessThan(n)
        val m = targetAbs - from
        if (m < k) {
            buildAuditPath(leaves, from, from + k, targetAbs, out)
            out.add(ProofStep(mthRange(leaves, from + k, to), siblingOnLeft = false))
        } else {
            buildAuditPath(leaves, from + k, to, targetAbs, out)
            out.add(ProofStep(mthRange(leaves, from, from + k), siblingOnLeft = true))
        }
    }

    private fun expectedSidesBottomUp(leafIndex: Int, treeSize: Int): List<Boolean> {
        val out = ArrayList<Boolean>()
        fun walk(m: Int, n: Int) {
            if (n == 1) return
            val k = largestPowerOfTwoLessThan(n)
            if (m < k) {
                walk(m, k)
                out.add(false)
            } else {
                walk(m - k, n - k)
                out.add(true)
            }
        }
        walk(leafIndex, treeSize)
        return out
    }

    private fun largestPowerOfTwoLessThan(n: Int): Int {
        check(n >= 2)
        return Integer.highestOneBit(n - 1)
    }
}
