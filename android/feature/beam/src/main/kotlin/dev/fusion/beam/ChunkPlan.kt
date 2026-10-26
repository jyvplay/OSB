// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/** One byte range: 0-based index, offset, and length. */
data class Chunk(val index: Int, val offset: Long, val length: Long)

/**
 * Fixed-size chunk planning for transfers: total bytes plus chunk size
 * yield ordered (index, offset, length) ranges, the last short when the
 * total is uneven. [missing] subtracts a validated done-set for resume.
 * Ranges only — hashing, transport, and reassembly are outside this
 * kernel. Caps: 1 Mi chunks, 1 GiB chunks.
 */
object ChunkPlan {
    const val MAX_CHUNKS = 1_000_000
    const val MAX_CHUNK_BYTES = 1024L * 1024 * 1024

    fun plan(totalBytes: Long, chunkBytes: Long): List<Chunk> {
        require(totalBytes in 0..Long.MAX_VALUE) { "negative total" }
        require(chunkBytes in 1..MAX_CHUNK_BYTES) { "chunk $chunkBytes outside 1..$MAX_CHUNK_BYTES" }
        if (totalBytes == 0L) return emptyList()
        val count = (totalBytes - 1) / chunkBytes + 1
        require(count <= MAX_CHUNKS) { "over $MAX_CHUNKS chunks" }
        return List(count.toInt()) { i ->
            val off = i.toLong() * chunkBytes
            Chunk(i, off, minOf(chunkBytes, totalBytes - off))
        }
    }

    /** Plan ranges minus validated done indexes, in order. */
    fun missing(totalBytes: Long, chunkBytes: Long, done: Set<Int>): List<Chunk> {
        val all = plan(totalBytes, chunkBytes)
        require(done.all { it in all.indices }) { "done index out of range" }
        return all.filter { it.index !in done }
    }
}
