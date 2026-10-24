package dev.fusion.archive

import java.util.zip.CRC32

/**
 * 7-Zip signature-header parser (the fixed 32-byte start of every .7z,
 * as documented in the 7-Zip source distribution's 7zFormat.txt).
 *
 * Layout: 6-byte magic `37 7A BC AF 27 1C`; version major (0x00) and
 * minor; u32le StartHeaderCRC — the CRC-32 of the NEXT 20 bytes —
 * then u64le NextHeaderOffset, u64le NextHeaderSize, u32le NextHeaderCRC.
 *
 * Declared narrowings: the signature header only — no next-header
 * parsing, no codecs, no extraction. The CRC gate means a truncated or
 * bit-flipped download is refused BEFORE any attempt to walk the
 * archive, which is the whole point of parsing this block separately.
 */
object SevenZHead {
    const val SCOPE = "32-byte signature header + StartHeaderCRC gate only; no next-header walk, no extraction."
    val MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)

    data class Head(
        val versionMajor: Int, val versionMinor: Int,
        val nextHeaderOffset: Long, val nextHeaderSize: Long, val nextHeaderCrc: Long
    )

    private fun u32(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 3 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v
    }

    private fun u64(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 7 downTo 0) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v
    }

    fun parse(bytes: ByteArray): Head {
        require(bytes.size >= 32) { "7z signature header needs 32 bytes, got ${bytes.size}" }
        require(MAGIC.indices.all { bytes[it] == MAGIC[it] }) { "not a 7z archive (bad magic)" }
        val major = bytes[6].toInt() and 0xFF
        val minor = bytes[7].toInt() and 0xFF
        require(major == 0) { "unsupported 7z format major version $major" }
        val storedCrc = u32(bytes, 8)
        val crc = CRC32().apply { update(bytes, 12, 20) }
        require(crc.value == storedCrc) { "StartHeaderCRC mismatch: stored ${storedCrc.toString(16)}, computed ${crc.value.toString(16)}" }
        val offset = u64(bytes, 12)
        val size = u64(bytes, 20)
        require(offset >= 0 && size >= 0) { "negative next-header geometry" }
        require(size > 0 || offset == 0L) { "nonzero offset with zero-size next header" }
        return Head(major, minor, offset, size, u32(bytes, 28))
    }

    /** Whole-file bound check once the total length is known. */
    fun fits(head: Head, fileSize: Long): Boolean =
        32 + head.nextHeaderOffset + head.nextHeaderSize <= fileSize
}
