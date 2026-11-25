// SPDX-License-Identifier: Apache-2.0
package dev.fusion.image

/**
 * Production-grade PNG chunk (`IHDR`, `IDAT`, `IEND`) walker & bounds validator.
 */
class ImagePngChunkWalker private constructor() {
    companion object {
        data class PngChunk(val type: String, val length: Int)

        fun walkChunks(bytes: ByteArray): List<PngChunk> {
            require(bytes.size >= 8) { "too short for PNG" }
            val chunks = mutableListOf<PngChunk>()
            var pos = 8
            while (pos + 12 <= bytes.size) {
                val len = ((bytes[pos].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
                require(len >= 0 && pos + 12L + len <= bytes.size) { "chunk length out of bounds" }
                val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                chunks.add(PngChunk(type, len))
                pos += 12 + len
                if (type == "IEND") break
            }
            return chunks
        }
    }
}
