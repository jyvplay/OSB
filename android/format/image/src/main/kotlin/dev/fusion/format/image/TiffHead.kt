// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image

/**
 * TIFF header reader: byte order, magic 42, first-IFD offset.
 *
 * Sits next to [JpegSeg] (marker walk) and [PngMetadata] (chunk walk):
 * nothing reads the 8-byte TIFF header — `II`/`MM` byte-order mark, the
 * 16-bit magic 42 in that order, and the 32-bit offset of IFD0. The magic
 * is validated in the file's own byte order (a little-endian file whose
 * magic reads 42 only big-endian is corrupt, not clever). The IFD offset
 * must be even and at least 8 (it points past the header); larger offsets
 * are accepted unchecked, because IFD parsing is explicitly outside this
 * kernel. BigTIFF (magic 43, 8-byte offsets) is refused as a different
 * format, documented so no caller mistakes the refusal for corruption.
 *
 * Plain Kotlin (ByteArray in, data out), JVM unit-testable.
 */
object TiffHead {

    /** Parsed header: byte order plus the IFD0 offset. */
    data class Header(val littleEndian: Boolean, val ifdOffset: Long)

    /**
     * Parses the TIFF header at the start of [bytes].
     *
     * @throws IllegalArgumentException on short input, a bad byte-order
     * mark, a wrong magic, or an impossible IFD offset.
     */
    fun parse(bytes: ByteArray): Header {
        require(bytes.size >= 8) { "short header" }
        val le = when {
            bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() -> true
            bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() -> false
            else -> throw IllegalArgumentException("bad byte order")
        }
        val magic = if (le) u16le(bytes, 2) else u16be(bytes, 2)
        require(magic == 42) { "bad magic $magic" }
        val off = if (le) u32le(bytes, 4) else u32be(bytes, 4)
        require(off >= 8 && off % 2 == 0L) { "bad IFD offset" }
        return Header(le, off)
    }

    private fun u16le(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u16be(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32le(b: ByteArray, at: Int): Long =
        (b[at].toInt() and 0xFF).toLong() or ((b[at + 1].toInt() and 0xFF).toLong() shl 8) or
            ((b[at + 2].toInt() and 0xFF).toLong() shl 16) or ((b[at + 3].toInt() and 0xFF).toLong() shl 24)

    private fun u32be(b: ByteArray, at: Int): Long =
        ((b[at].toInt() and 0xFF).toLong() shl 24) or ((b[at + 1].toInt() and 0xFF).toLong() shl 16) or
            ((b[at + 2].toInt() and 0xFF).toLong() shl 8) or (b[at + 3].toInt() and 0xFF).toLong()
}
