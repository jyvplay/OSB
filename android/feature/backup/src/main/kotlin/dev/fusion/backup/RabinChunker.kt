// SPDX-License-Identifier: Apache-2.0
package dev.fusion.backup

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Clean-room Gear-Hash Content-Defined Chunker (`FastCDC` conceptual architecture) for
 * Incremental Encrypted Backups.
 *
 * Unlike fixed-block chunking (where inserting 1 byte at offset 0 invalidates 100% of downstream
 * blocks), rolling Gear-hash CDC re-synchronizes at content-defined cut-points within 1–2 chunks,
 * preserving high deduplication ratios across edited `.docx` / `.tar` / SQLite files!
 */
object RabinChunker {
    const val SCOPE =
        "Deterministic Gear rolling-hash content-defined chunker (min/avg/max byte bounds, SHA-256 deduplication index, exact byte-stream reassembly); encryption envelope lives in Vault/Privacy."

    const val HARD_MAX_STREAM_BYTES = 32 * 1024 * 1024

    // Deterministic 256-entry 64-bit Gear table generated via SplitMix64 from fixed constant pi-seed
    private val GEAR_TABLE: LongArray = LongArray(256).also { tbl ->
        var s = 0x243F6A8885A308D3L
        for (i in 0 until 256) {
            s += -0x61c8864680b583ebL
            var z = s
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            tbl[i] = z xor (z ushr 31)
        }
    }

    data class ChunkRef(
        val offset: Int,
        val length: Int,
        val sha256Hex: String,
    )

    data class DedupSnapshot(
        val totalLogicalBytes: Long,
        val uniqueStoredBytes: Long,
        val chunks: List<ChunkRef>,
        val chunkStore: Map<String, ByteArray>,
    ) {
        val savedBytes: Long get() = totalLogicalBytes - uniqueStoredBytes
    }

    fun chunk(
        data: ByteArray,
        minSize: Int = 512,
        maskBits: Int = 11, // avg ~ 2 KiB
        maxSize: Int = 8_192,
    ): List<ChunkRef> {
        require(data.size <= HARD_MAX_STREAM_BYTES) { "data exceeds $HARD_MAX_STREAM_BYTES bytes" }
        require(minSize in 64..maxSize && maxSize <= 1_048_576) { "invalid CDC bounds min=$minSize max=$maxSize" }
        require(maskBits in 6..20) { "maskBits $maskBits outside 6..20" }
        if (data.isEmpty()) return emptyList()

        // High-bit mask on 64-bit Gear fingerprint
        val mask = ((1L shl maskBits) - 1L) shl (64 - maskBits)
        val out = ArrayList<ChunkRef>()
        var start = 0
        while (start < data.size) {
            val remaining = data.size - start
            if (remaining <= minSize) {
                val slice = data.copyOfRange(start, data.size)
                out.add(ChunkRef(start, slice.size, sha256Hex(slice)))
                break
            }
            val hardLimit = minOf(data.size, start + maxSize)
            var fp = 0L
            var pos = start + minSize
            while (pos < hardLimit) {
                fp = (fp shl 1) + GEAR_TABLE[data[pos].toInt() and 0xFF]
                pos++
                if ((fp and mask) == 0L) break
            }
            val slice = data.copyOfRange(start, pos)
            out.add(ChunkRef(start, slice.size, sha256Hex(slice)))
            start = pos
        }
        return out
    }

    fun deduplicate(
        versions: List<ByteArray>,
        minSize: Int = 512,
        maskBits: Int = 11,
        maxSize: Int = 8_192,
    ): List<DedupSnapshot> {
        val store = LinkedHashMap<String, ByteArray>()
        return versions.map { payload ->
            val refs = ArrayList<ChunkRef>()
            var pos = 0
            for (c in chunk(payload, minSize, maskBits, maxSize)) {
                val bytes = payload.copyOfRange(c.offset, c.offset + c.length)
                store.putIfAbsent(c.sha256Hex, bytes)
                refs.add(c)
                pos += c.length
            }
            check(pos == payload.size)
            val uniqueBytes = store.values.sumOf { it.size.toLong() }
            DedupSnapshot(payload.size.toLong(), uniqueBytes, refs, LinkedHashMap(store))
        }
    }

    fun reassemble(refs: List<ChunkRef>, store: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for (r in refs) {
            val block = store[r.sha256Hex]
                ?: throw IllegalArgumentException("missing chunk ${r.sha256Hex} in backup store")
            require(block.size == r.length && sha256Hex(block) == r.sha256Hex) {
                "corrupted backup chunk ${r.sha256Hex}"
            }
            out.write(block)
        }
        return out.toByteArray()
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
