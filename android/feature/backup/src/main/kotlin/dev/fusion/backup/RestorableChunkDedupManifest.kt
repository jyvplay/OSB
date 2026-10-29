package dev.fusion.backup

import java.security.MessageDigest

/**
 * Clean-room Content-Defined Gear-Hash Rolling Chunk Deduplicator & Merkle Backup Manifest
 * Verifier (Restic/FastCDC-inspired; Apache-2.0).
 */
object RestorableChunkDedupManifest {
    const val SCOPE =
        "FastCDC-style Gear rolling-hash content-defined chunker (4 KiB..64 KiB) + SHA-256 deduplicated store & Merkle root verifier"

    data class BackupManifest(
        val totalLogicalBytes: Long,
        val uniqueStoredBytes: Long,
        val chunkDigestsHex: List<String>,
        val merkleRootHex: String,
    ) {
        val deduplicationRatio: Double
            get() = if (uniqueStoredBytes == 0L) 1.0 else totalLogicalBytes.toDouble() / uniqueStoredBytes.toDouble()
    }

    private val GEAR_TABLE: LongArray = LongArray(256) { i ->
        // Deterministic SplitMix64-seeded 64-bit Gear lookup table (zero runtime drift!)
        var z = (i.toLong() + 1L) * -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z xor (z ushr 31)
    }

    class ChunkRepository {
        private val chunks = LinkedHashMap<String, ByteArray>()

        fun ingest(payload: ByteArray, minChunk: Int = 2048, avgMaskBits: Int = 12, maxChunk: Int = 32_768): BackupManifest {
            require(minChunk in 256..maxChunk)
            val mask = (1L shl avgMaskBits) - 1L
            val orderedDigests = ArrayList<String>()
            var pos = 0
            while (pos < payload.size) {
                val limit = minOf(payload.size, pos + maxChunk)
                var cut = minOf(payload.size, pos + minChunk)
                var fp = 0L
                while (cut < limit) {
                    fp = (fp shl 1) + GEAR_TABLE[payload[cut].toInt() and 0xFF]
                    cut++
                    if ((fp and mask) == 0L) break
                }
                val slice = payload.copyOfRange(pos, cut)
                val digest = sha256Hex(slice)
                chunks.putIfAbsent(digest, slice)
                orderedDigests.add(digest)
                pos = cut
            }
            val uniqueBytes = orderedDigests.distinct().sumOf { chunks.getValue(it).size.toLong() }
            return BackupManifest(
                totalLogicalBytes = payload.size.toLong(),
                uniqueStoredBytes = uniqueBytes,
                chunkDigestsHex = orderedDigests,
                merkleRootHex = computeMerkleRoot(orderedDigests),
            )
        }

        fun restoreAndVerify(manifest: BackupManifest): ByteArray {
            require(computeMerkleRoot(manifest.chunkDigestsHex) == manifest.merkleRootHex) {
                "Backup manifest Merkle root mismatch"
            }
            val out = java.io.ByteArrayOutputStream(manifest.totalLogicalBytes.toInt())
            for (h in manifest.chunkDigestsHex) {
                val blk = chunks[h] ?: throw IllegalStateException("Missing chunk $h in repository")
                require(sha256Hex(blk) == h) { "Bit-rot detected in stored chunk $h" }
                out.write(blk)
            }
            return out.toByteArray()
        }
    }

    private fun computeMerkleRoot(leaves: List<String>): String {
        if (leaves.isEmpty()) return sha256Hex(ByteArray(0))
        var level = leaves
        while (level.size > 1) {
            val next = ArrayList<String>((level.size + 1) / 2)
            var i = 0
            while (i < level.size) {
                val left = level[i]
                val right = if (i + 1 < level.size) level[i + 1] else left
                next.add(sha256Hex("$left:$right".toByteArray(Charsets.US_ASCII)))
                i += 2
            }
            level = next
        }
        return level[0]
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
