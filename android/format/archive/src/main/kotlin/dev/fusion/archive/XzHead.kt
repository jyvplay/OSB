// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.util.zip.CRC32

/**
 * XZ stream-header reader (the 12-byte `.xz` Stream Header).
 *
 * Sits next to [TarHeader] (ustar), [GzipHead] (gzip members), and
 * [ZlibHead] (RFC 1950): no reader covers XZ. The header is the 6-byte
 * magic `FD 37 7A 58 5A 00`, two Stream Flags bytes (the first must be
 * zero; the second selects the integrity Check), and a little-endian
 * CRC32 over the flags. [parse] validates all three, including the CRC,
 * and reports the Check; Check IDs the format reserves are refused, not
 * guessed. The second flags byte is compared whole, so a nonzero
 * reserved high nibble (spec: bits 4-7 MUST be zero) is refused by the
 * same lookup that refuses reserved IDs. Block Headers, the Index, and
 * decompression are outside.
 *
 * Plain Kotlin + JDK CRC32, no Android dependency, JVM unit-testable.
 */
object XzHead {
    const val HEADER_LEN = 12

    /**
     * XZ integrity Check IDs, assigned values only (xz file-format
     * spec 1.2.1 §2.1.1.2): 0x00 None, 0x01 CRC32, 0x04 CRC64,
     * 0x0A SHA-256. Every other ID in 0x02..0x0F is reserved by the
     * format and has no name here.
     */
    enum class Check(val id: Int) { NONE(0), CRC32(1), CRC64(4), SHA256(10) }

    /** A validated XZ stream header. */
    data class Header(val check: Check)

    private val MAGIC = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)

    /**
     * Parses the first 12 bytes of [bytes] as an XZ stream header.
     *
     * @throws IllegalArgumentException on a short buffer, bad magic, a
     * nonzero flags byte, a reserved Check ID, or a CRC32 mismatch.
     */
    fun parse(bytes: ByteArray): Header {
        require(bytes.size >= HEADER_LEN) { "short header" }
        for (i in MAGIC.indices) require(bytes[i] == MAGIC[i]) { "bad magic" }
        require(bytes[6] == 0.toByte()) { "nonzero flags" }
        val id = bytes[7].toInt() and 0xFF
        val check = Check.entries.firstOrNull { it.id == id }
        require(check != null) { "reserved check id" }
        val crc = CRC32()
        crc.update(bytes, 6, 2)
        val want = crc.value
        val got = (bytes[8].toLong() and 0xFF) or ((bytes[9].toLong() and 0xFF) shl 8) or
            ((bytes[10].toLong() and 0xFF) shl 16) or ((bytes[11].toLong() and 0xFF) shl 24)
        require(got == want) { "crc mismatch" }
        return Header(check)
    }
}
