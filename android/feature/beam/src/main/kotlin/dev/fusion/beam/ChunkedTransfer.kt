// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

import java.security.MessageDigest

/**
 * Resumable Out-of-Order Chunked File Transfer Assembler with Per-Chunk & Whole-Payload SHA-256.
 *
 * Enables Beam peer-to-peer transfers to survive link drops, reordered UDP/WebRTC data-channel
 * packets, and untrusted relay bit-flips without restarting from byte 0.
 */
object ChunkedTransfer {
    const val SCOPE =
        "Out-of-order resumable chunked payload assembler with per-chunk + whole-file SHA-256 verification and missing-chunk bitmap; network socket transport out of scope."

    const val MAX_TOTAL_BYTES = 32 * 1024 * 1024

    data class Manifest(
        val transferId: String,
        val totalBytes: Int,
        val chunkSize: Int,
        val chunkSha256Hex: List<String>,
        val wholeSha256Hex: String,
    ) {
        val chunkCount: Int get() = chunkSha256Hex.size

        init {
            require(transferId.isNotBlank()) { "transferId must not be blank" }
            require(totalBytes in 1..MAX_TOTAL_BYTES) { "totalBytes $totalBytes outside 1..$MAX_TOTAL_BYTES" }
            require(chunkSize in 64..1_048_576) { "chunkSize $chunkSize outside 64..1048576" }
            val expectedChunks = (totalBytes + chunkSize - 1) / chunkSize
            require(chunkSha256Hex.size == expectedChunks) {
                "expected $expectedChunks chunk hashes, got ${chunkSha256Hex.size}"
            }
        }
    }

    enum class AcceptStatus { ACCEPTED_NEW, DUPLICATE_IGNORED, REJECTED_BAD_HASH, REJECTED_BAD_SIZE }

    class Receiver(val manifest: Manifest) {
        private val buffer = ByteArray(manifest.totalBytes)
        private val received = BooleanArray(manifest.chunkCount)
        private var receivedCount = 0

        val isComplete: Boolean get() = receivedCount == manifest.chunkCount

        fun missingChunkIndices(): List<Int> =
            (0 until manifest.chunkCount).filter { !received[it] }

        fun acceptChunk(index: Int, data: ByteArray): AcceptStatus {
            require(index in 0 until manifest.chunkCount) { "chunk index $index out of range" }
            val offset = index * manifest.chunkSize
            val expectedLen = minOf(manifest.chunkSize, manifest.totalBytes - offset)
            if (data.size != expectedLen) return AcceptStatus.REJECTED_BAD_SIZE
            if (sha256Hex(data) != manifest.chunkSha256Hex[index].lowercase()) {
                return AcceptStatus.REJECTED_BAD_HASH
            }
            if (received[index]) return AcceptStatus.DUPLICATE_IGNORED
            System.arraycopy(data, 0, buffer, offset, expectedLen)
            received[index] = true
            receivedCount++
            return AcceptStatus.ACCEPTED_NEW
        }

        fun assembleVerified(): ByteArray {
            check(isComplete) { "transfer '${manifest.transferId}' incomplete (missing ${missingChunkIndices().size} chunks)" }
            val actualWhole = sha256Hex(buffer)
            check(actualWhole == manifest.wholeSha256Hex.lowercase()) {
                "whole-file SHA-256 mismatch (expected ${manifest.wholeSha256Hex}, got $actualWhole)"
            }
            return buffer.copyOf()
        }
    }

    fun sliceWithManifest(transferId: String, payload: ByteArray, chunkSize: Int): Pair<Manifest, List<ByteArray>> {
        require(payload.size in 1..MAX_TOTAL_BYTES) { "payload size ${payload.size} outside 1..$MAX_TOTAL_BYTES" }
        require(chunkSize in 64..1_048_576) { "chunkSize $chunkSize outside 64..1048576" }
        val chunks = ArrayList<ByteArray>()
        val hashes = ArrayList<String>()
        var off = 0
        while (off < payload.size) {
            val end = minOf(off + chunkSize, payload.size)
            val slice = payload.copyOfRange(off, end)
            chunks.add(slice)
            hashes.add(sha256Hex(slice))
            off = end
        }
        val manifest = Manifest(transferId, payload.size, chunkSize, hashes, sha256Hex(payload))
        return manifest to chunks
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
