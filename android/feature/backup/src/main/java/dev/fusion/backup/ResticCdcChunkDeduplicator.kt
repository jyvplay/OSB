// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

import java.security.MessageDigest

/**
 * Clean-room Rolling-Hash Content-Defined Chunking (`CDC`) Variable-Block Backup Deduplicator.
 *
 * Splits byte streams at deterministic gear-hash cutpoints (bounded `[minChunk, maxChunk]`) so
 * inserting bytes near the start of an archive re-uploads only the single mutated chunk rather
 * than shifting every subsequent fixed-size block.
 */
object ResticCdcChunkDeduplicator {
    const val SCOPE =
        "Pure-JVM gear-rolling-hash Content-Defined Chunking (CDC) variable-block backup deduplicator with SHA-256 chunk manifest."

    data class CdcChunk(val offset: Int, val length: Int, val sha256Hex: String)

    private val GEAR_TABLE: IntArray = IntArray(256) { i ->
        var x = (i + 1) * 0x45D9F3B
        x = ((x ushr 16) xor x) * 0x45D9F3B
        (x ushr 16) xor x
    }

    fun chunkStream(
        data: ByteArray,
        minChunk: Int = 512,
        avgMaskBits: Int = 10, // ~1 KiB average in tests, configurable up to 20 (1 MiB)
        maxChunk: Int = 4096,
    ): List<CdcChunk> {
        require(minChunk >= 64 && maxChunk > minChunk && avgMaskBits in 6..22) { "invalid CDC bounds" }
        if (data.isEmpty()) return emptyList()
        val mask = (1 shl avgMaskBits) - 1
        val out = ArrayList<CdcChunk>()
        var start = 0
        var rolling = 0
        var pos = 0

        while (pos < data.size) {
            rolling = (rolling shl 1) + GEAR_TABLE[data[pos].toInt() and 0xFF]
            val curLen = pos - start + 1
            val isCut = (curLen >= minChunk && (rolling and mask) == 0) || curLen >= maxChunk || pos == data.lastIndex
            if (isCut) {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(data.copyOfRange(start, pos + 1))
                    .joinToString("") { "%02x".format(it) }
                out.add(CdcChunk(start, curLen, digest))
                start = pos + 1
                rolling = 0
            }
            pos++
        }
        return out
    }
}
