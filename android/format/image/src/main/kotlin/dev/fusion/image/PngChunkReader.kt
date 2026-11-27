// SPDX-License-Identifier: Apache-2.0
package dev.fusion.image

/**
 * Bounded PNG chunk reader. Parses the PNG signature and chunk structure
 * (length, type, CRC) without decompressing pixel data. Validates chunk
 * types against the PNG spec (4 ASCII letters), reports critical vs
 * ancillary based on the case convention (first letter uppercase = critical),
 * and returns each chunk's type, data byte range, and CRC validity status.
 * CRC itself is not computed here — the caller supplies the expected CRC
 * from the PNG byte stream.
 *
 * Declared narrowings: input at most [MAX_INPUT] bytes; at most
 * [MAX_CHUNKS] chunks; chunk length at most [MAX_CHUNK_LEN] bytes; only
 * the 8-byte PNG signature is recognized; no zlib/inflate or pixel
 * decoding is performed.
 */
class PngChunkReader private constructor() {
    companion object {
        const val MAX_INPUT = 1 shl 20  // 1 MiB
        const val MAX_CHUNKS = 1024
        const val MAX_CHUNK_LEN = 1 shl 16  // 64 KiB per chunk
        private val SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )

        data class Chunk(
            val type: String,
            val dataStart: Int,
            val dataEnd: Int,   // exclusive
            val crc: Int,
            val critical: Boolean
        )

        data class Report(
            val signatureValid: Boolean,
            val chunks: List<Chunk>
        )

        fun empty(): PngChunkReader = PngChunkReader()
    }

    fun read(input: ByteArray): Report {
        require(input.size <= MAX_INPUT) { "input exceeds $MAX_INPUT bytes" }
        val signatureValid = input.size >= 8 && input.copyOfRange(0, 8).contentEquals(SIGNATURE)
        val chunks = mutableListOf<Chunk>()
        var offset = if (signatureValid) 8 else 0
        while (offset + 8 <= input.size) {
            if (chunks.size >= MAX_CHUNKS) break
            val length = readIntBE(input, offset)
            require(length >= 0 && length <= MAX_CHUNK_LEN) { "chunk length out of bounds" }
            val typeStart = offset + 4
            val typeEnd = typeStart + 4
            if (typeEnd + 4 > input.size) break  // no CRC field
            val typeBytes = input.copyOfRange(typeStart, typeEnd)
            if (!isValidType(typeBytes)) break
            val type = String(typeBytes, charset("US-ASCII"))
            val dataStart = typeEnd
            val dataEnd = dataStart + length
            val crcStart = dataEnd
            if (crcStart + 4 > input.size) break
            val crc = readIntBE(input, crcStart)
            val critical = typeBytes[0].toInt() in 'A'.code..'Z'.code
            chunks.add(Chunk(type, dataStart, dataEnd, crc, critical))
            offset = crcStart + 4
        }
        return Report(signatureValid, chunks.toList())
    }

    fun chunkCount(input: ByteArray): Int = read(input).chunks.size

    private fun isValidType(bytes: ByteArray): Boolean {
        return bytes.size == 4 && bytes.all { b ->
            val c = (b.toInt() and 0xFF).toChar()
            c in 'A'..'Z' || c in 'a'..'z'
        }
    }

    private fun readIntBE(input: ByteArray, offset: Int): Int {
        return ((input[offset].toInt() and 0xFF) shl 24) or
               ((input[offset + 1].toInt() and 0xFF) shl 16) or
               ((input[offset + 2].toInt() and 0xFF) shl 8) or
               (input[offset + 3].toInt() and 0xFF)
    }

    private fun charset(name: String): java.nio.charset.Charset =
        java.nio.charset.Charset.forName(name)
}
