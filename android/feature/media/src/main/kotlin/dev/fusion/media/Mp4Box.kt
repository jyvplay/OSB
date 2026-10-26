// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

/**
 * ISO BMFF top-level box walk (MP4/M4A/M4V structure, not media).
 *
 * Sits next to [Id3Head] (ID3 headers), [WavHead] (WAV headers), and
 * [PlaylistParser] (M3U text): nothing walks MP4 boxes. [walk] reads
 * the top-level `size + type` headers — including 64-bit largesize
 * (`size == 1`) and to-end boxes (`size == 0`) — and reports each
 * box's type, total size, and offset. Payloads are never parsed
 * (uuid user-types included: the 16 bytes stay payload); nesting,
 * sample tables, and decode are outside.
 *
 * Plain Kotlin, no Android dependency, JVM unit-testable.
 */
object Mp4Box {
    const val MAX_BOXES = 10_000

    /** One top-level box: FourCC, total size, and file offset. */
    data class Box(val type: String, val size: Long, val offset: Long)

    /**
     * Walks the top-level boxes of [bytes].
     *
     * @throws IllegalArgumentException on a truncated header, a size
     * below its own header, a size overrunning the input, a
     * non-ASCII box type, or an over-cap box count.
     */
    fun walk(bytes: ByteArray): List<Box> {
        val out = ArrayList<Box>()
        var at = 0L
        while (at < bytes.size) {
            require(out.size < MAX_BOXES) { "too many boxes" }
            require(bytes.size - at >= 8) { "truncated header" }
            val o = at.toInt()
            var size = u32(bytes, o)
            for (i in 4..7) require(bytes[o + i].toInt() and 0xFF in 0x20..0x7E) { "bad type" }
            val type = String(bytes, o + 4, 4, Charsets.US_ASCII)
            var headLen = 8L
            if (size == 1L) {
                require(bytes.size - at >= 16) { "truncated largesize" }
                size = u64(bytes, o + 8)
                headLen = 16L
            } else if (size == 0L) {
                size = bytes.size - at
            }
            require(size >= headLen) { "size below header" }
            require(size <= bytes.size - at) { "size overruns input" }
            out.add(Box(type, size, at))
            at += size
        }
        return out
    }

    private fun u32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0..7) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        require(v >= 0) { "largesize over cap" }
        return v
    }
}
