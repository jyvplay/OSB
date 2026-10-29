// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

import java.security.MessageDigest

/**
 * Rsync-style **Rolling Adler-32 + Strong SHA-256 Binary Delta Patch Generator & Reconstructor**
 * for bandwidth-minimal encrypted Fusion Backup incremental snapshots!
 */
object RabinKarpDeltaPatchGenerator {
    const val SCOPE =
        "Rolling Adler-32 + SHA-256 block-matching binary delta patch generator and bit-exact target reconstructor."

    sealed interface DeltaOp {
        data class CopyBlock(val basisBlockIndex: Int, val length: Int) : DeltaOp
        data class InsertLiteral(val bytes: ByteArray) : DeltaOp
    }

    data class DeltaPatch(
        val blockSize: Int,
        val ops: List<DeltaOp>,
        val targetSha256Hex: String,
        val transmittedLiteralBytes: Int,
    )

    fun computePatch(basis: ByteArray, target: ByteArray, blockSize: Int = 64): DeltaPatch {
        require(blockSize in 16..65536)
        val index = HashMap<Int, MutableList<Pair<Int, String>>>()
        var bIdx = 0
        var off = 0
        while (off + blockSize <= basis.size) {
            val weak = adler32Slice(basis, off, blockSize)
            val strong = sha256Hex(basis, off, blockSize)
            index.getOrPut(weak) { ArrayList() }.add(bIdx to strong)
            bIdx++
            off += blockSize
        }

        val ops = ArrayList<DeltaOp>()
        val pendingLit = java.io.ByteArrayOutputStream()
        var literalCount = 0

        fun flushLit() {
            if (pendingLit.size() > 0) {
                val arr = pendingLit.toByteArray()
                ops.add(DeltaOp.InsertLiteral(arr))
                literalCount += arr.size
                pendingLit.reset()
            }
        }

        var pos = 0
        while (pos < target.size) {
            if (pos + blockSize <= target.size) {
                val w = adler32Slice(target, pos, blockSize)
                val candidates = index[w]
                if (candidates != null) {
                    val s = sha256Hex(target, pos, blockSize)
                    val hit = candidates.firstOrNull { it.second == s }
                    if (hit != null) {
                        flushLit()
                        ops.add(DeltaOp.CopyBlock(hit.first, blockSize))
                        pos += blockSize
                        continue
                    }
                }
            }
            pendingLit.write(target[pos].toInt())
            pos++
        }
        flushLit()
        return DeltaPatch(blockSize, ops, sha256Hex(target, 0, target.size), literalCount)
    }

    fun applyPatch(basis: ByteArray, patch: DeltaPatch): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (op in patch.ops) {
            when (op) {
                is DeltaOp.CopyBlock -> {
                    val srcOff = op.basisBlockIndex * patch.blockSize
                    require(srcOff + op.length <= basis.size) { "Out-of-bounds CopyBlock" }
                    out.write(basis, srcOff, op.length)
                }
                is DeltaOp.InsertLiteral -> out.write(op.bytes)
            }
        }
        val rebuilt = out.toByteArray()
        val actualHash = sha256Hex(rebuilt, 0, rebuilt.size)
        check(actualHash == patch.targetSha256Hex) { "Delta patch SHA-256 reconstruction mismatch!" }
        return rebuilt
    }

    private fun adler32Slice(b: ByteArray, off: Int, len: Int): Int {
        var s1 = 1
        var s2 = 0
        for (i in 0 until len) {
            s1 = (s1 + (b[off + i].toInt() and 0xFF)) % 65521
            s2 = (s2 + s1) % 65521
        }
        return (s2 shl 16) or s1
    }

    private fun sha256Hex(b: ByteArray, off: Int, len: Int): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(b, off, len)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
