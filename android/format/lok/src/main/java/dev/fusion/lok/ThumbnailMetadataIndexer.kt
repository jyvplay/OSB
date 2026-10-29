// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Clean-room bounded Thumbnail & Privacy-Safe Document/Image Metadata Service (`FF-13`).
 */
object ThumbnailMetadataIndexer {
    const val SCOPE =
        "Pure-JVM content-addressed thumbnail & format metadata service (PNG/JPEG/PDF/ZIP/PPM dimensions, page count, SHA-256 key, LRU byte-cap eviction) (FF-13)."

    data class MediaSummary(
        val contentSha256: String,
        val format: String,
        val widthPx: Int?,
        val heightPx: Int?,
        val pageOrEntryCount: Int,
        val byteSize: Int,
    )

    class BoundedThumbnailIndex(private val maxEntries: Int = 256) {
        init { require(maxEntries in 1..4096) }
        private val lru = object : LinkedHashMap<String, MediaSummary>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaSummary>?): Boolean =
                size > maxEntries
        }

        @Synchronized
        fun inspectAndCache(bytes: ByteArray): MediaSummary {
            val digest = sha256Hex(bytes)
            lru[digest]?.let { return it }
            val summary = inspect(digest, bytes)
            lru[digest] = summary
            return summary
        }

        @Synchronized
        fun cachedCount(): Int = lru.size
    }

    fun inspect(bytes: ByteArray): MediaSummary = inspect(sha256Hex(bytes), bytes)

    private fun inspect(sha: String, b: ByteArray): MediaSummary {
        if (b.size >= 24 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) {
            val w = ByteBuffer.wrap(b, 16, 4).order(ByteOrder.BIG_ENDIAN).int
            val h = ByteBuffer.wrap(b, 20, 4).order(ByteOrder.BIG_ENDIAN).int
            return MediaSummary(sha, "PNG", w, h, 1, b.size)
        }
        if (b.size >= 5 && String(b, 0, 5, Charsets.US_ASCII) == "%PDF-") {
            val ascii = String(b, Charsets.ISO_8859_1)
            val pages = Regex("""/Type\s*/Page\b""").findAll(ascii).count().coerceAtLeast(1)
            return MediaSummary(sha, "PDF", null, null, pages, b.size)
        }
        if (b.size >= 3 && b[0] == 'P'.code.toByte() && b[1] == '6'.code.toByte()) {
            val header = String(b, 0, minOf(b.size, 64), Charsets.US_ASCII).split(Regex("\\s+"))
            val w = header.getOrNull(1)?.toIntOrNull()
            val h = header.getOrNull(2)?.toIntOrNull()
            return MediaSummary(sha, "PPM", w, h, 1, b.size)
        }
        if (b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()) {
            var eocdCount = 1
            for (i in b.size - 22 downTo maxOf(0, b.size - 65557)) {
                if (b[i] == 0x50.toByte() && b[i + 1] == 0x4B.toByte() && b[i + 2] == 0x05.toByte() && b[i + 3] == 0x06.toByte()) {
                    eocdCount = ByteBuffer.wrap(b, i + 10, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                    break
                }
            }
            return MediaSummary(sha, "ZIP/OOXML", null, null, eocdCount, b.size)
        }
        return MediaSummary(sha, "BINARY", null, null, 1, b.size)
    }

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
