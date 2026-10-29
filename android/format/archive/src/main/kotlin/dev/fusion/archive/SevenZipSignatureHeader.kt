// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Clean-room **7z (`37 7A BC AF 27 1C`) `32`-Byte SignatureHeader Binary Codec & Safety Validator**.
 *
 * Layout per 7-Zip Format Specification (`Public Domain` spec):
 * - `[0..5]`   6-byte signature `37 7A BC AF 27 1C`
 * - `[6..7]`   ArchiveVersion (`major = 0, minor = 4`)
 * - `[8..11]`  `StartHeaderCRC` (little-endian IEEE CRC-32 over bytes `[12..31]`)
 * - `[12..19]` `NextHeaderOffset` (`u64` LE relative to byte `32`)
 * - `[20..27]` `NextHeaderSize` (`u64` LE)
 * - `[28..31]` `NextHeaderCRC` (`u32` LE IEEE CRC-32 over the `NextHeader` slice)
 */
object SevenZipSignatureHeader {
    const val SCOPE =
        "Clean-room 7z 32-byte SignatureHeader + NextHeader binary encoder & dual IEEE CRC-32 / bounds-overflow validator."

    val MAGIC = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
    const val HEADER_BYTES = 32
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L

    data class ParsedArchiveHeader(
        val versionMajor: Int,
        val versionMinor: Int,
        val nextHeaderOffset: Long,
        val nextHeaderSize: Long,
        val nextHeaderBytes: ByteArray,
    )

    fun buildArchive(payloadBytes: ByteArray, nextHeaderBytes: ByteArray): ByteArray {
        val nextCrc = crc32LeInt(nextHeaderBytes)
        val startBody = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(payloadBytes.size.toLong())
            putLong(nextHeaderBytes.size.toLong())
            putInt(nextCrc)
        }.array()
        val startCrc = crc32LeInt(startBody)

        val out = ByteBuffer.allocate(HEADER_BYTES + payloadBytes.size + nextHeaderBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(MAGIC)
        out.put(0.toByte())
        out.put(4.toByte())
        out.putInt(startCrc)
        out.put(startBody)
        out.put(payloadBytes)
        out.put(nextHeaderBytes)
        return out.array()
    }

    fun validateAndInspect(archiveBytes: ByteArray): ParsedArchiveHeader {
        require(archiveBytes.size >= HEADER_BYTES) { "truncated 7z archive (< 32 bytes)" }
        for (i in MAGIC.indices) {
            require(archiveBytes[i] == MAGIC[i]) { "invalid 7z magic signature at byte $i" }
        }
        val major = archiveBytes[6].toInt() and 0xFF
        val minor = archiveBytes[7].toInt() and 0xFF
        require(major == 0) { "unsupported 7z major version $major" }

        val buf = ByteBuffer.wrap(archiveBytes).order(ByteOrder.LITTLE_ENDIAN)
        val declaredStartCrc = buf.getInt(8)
        val actualStartCrc = crc32LeInt(archiveBytes.copyOfRange(12, 32))
        require(declaredStartCrc == actualStartCrc) { "7z StartHeaderCRC mismatch (corrupt signature header)" }

        val nextOffset = buf.getLong(12)
        val nextSize = buf.getLong(20)
        val declaredNextCrc = buf.getInt(28)
        require(nextOffset >= 0L && nextSize >= 0L && nextOffset + nextSize <= MAX_ARCHIVE_BYTES) {
            "7z NextHeader offset/size overflow ($nextOffset + $nextSize)"
        }
        val absStart = HEADER_BYTES.toLong() + nextOffset
        val absEnd = absStart + nextSize
        require(absEnd <= archiveBytes.size.toLong()) { "7z NextHeader points past EOF ($absEnd > ${archiveBytes.size})" }
        val nextSlice = archiveBytes.copyOfRange(absStart.toInt(), absEnd.toInt())
        val actualNextCrc = crc32LeInt(nextSlice)
        require(declaredNextCrc == actualNextCrc) { "7z NextHeaderCRC mismatch" }
        return ParsedArchiveHeader(major, minor, nextOffset, nextSize, nextSlice)
    }

    private fun crc32LeInt(data: ByteArray): Int =
        CRC32().apply { update(data) }.value.toInt()
}
