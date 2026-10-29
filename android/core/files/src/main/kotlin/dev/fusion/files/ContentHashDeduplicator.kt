// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

import java.security.MessageDigest

/**
 * Clean-room **Content-Defined Chunking (`CDC` Gear Rolling Hash) & SHA-256 Deduplicator**.
 *
 * Splits byte streams at data-dependent cut points (so inserting bytes at offset `0` does NOT
 * shift all subsequent chunk boundaries, unlike fixed-size chunking!) and computes exact
 * deduplicated storage savings across multi-file sets.
 */
object ContentHashDeduplicator {
    const val SCOPE =
        "Clean-room Gear rolling-hash content-defined chunker (CDC) & SHA-256 deduplication manifest planner."

    private val GEAR_TABLE: LongArray = LongArray(256) { i ->
        // Deterministic SplitMix64 table seeded from byte index i
        var z = (i.toLong() + 1L) * -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z xor (z ushr 31)
    }

    data class ChunkRef(
        val offset: Int,
        val length: Int,
        val sha256Hex: String,
    )

    data class DedupReport(
        val fileChunks: Map<String, List<ChunkRef>>,
        val uniqueChunksBytes: Long,
        val logicalTotalBytes: Long,
        val savedBytes: Long,
    )

    fun chunkBytes(
        data: ByteArray,
        minChunk: Int = 512,
        avgChunkBits: Int = 11, // 2^11 = 2048 B average target
        maxChunk: Int = 8192,
    ): List<ChunkRef> {
        require(minChunk >= 64 && maxChunk >= minChunk && avgChunkBits in 6..20) { "invalid CDC bounds" }
        if (data.isEmpty()) return emptyList()
        val mask = (1L shl avgChunkBits) - 1L
        val out = ArrayList<ChunkRef>()
        var start = 0
        var fp = 0L
        var i = 0
        while (i < data.size) {
            fp = (fp shl 1) + GEAR_TABLE[data[i].toInt() and 0xFF]
            val curLen = i - start + 1
            val isCut = (curLen >= minChunk && (fp and mask) == 0L) || curLen >= maxChunk || i == data.lastIndex
            if (isCut) {
                val slice = data.copyOfRange(start, i + 1)
                out.add(ChunkRef(start, curLen, sha256Hex(slice)))
                start = i + 1
                fp = 0L
            }
            i++
        }
        return out
    }

    fun planDeduplication(files: Map<String, ByteArray>, minChunk: Int = 256, avgChunkBits: Int = 9, maxChunk: Int = 4096): DedupReport {
        val seenSizes = HashMap<String, Int>()
        val perFile = LinkedHashMap<String, List<ChunkRef>>()
        var logical = 0L
        for ((name, bytes) in files) {
            logical += bytes.size
            val chunks = chunkBytes(bytes, minChunk, avgChunkBits, maxChunk)
            perFile[name] = chunks
            for (c in chunks) seenSizes.putIfAbsent(c.sha256Hex, c.length)
        }
        val unique = seenSizes.values.sumOf { it.toLong() }
        return DedupReport(perFile, unique, logical, (logical - unique).coerceAtLeast(0L))
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
