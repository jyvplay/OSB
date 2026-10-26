// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * EDID base-block parser pinned to VESA E-EDID structure version 1
 * (release A rev 2 table 3.1; field layout as publicly documented):
 *
 * - Bytes 0..7: fixed header pattern 00 FF FF FF FF FF FF 00.
 * - Bytes 8..9: manufacturer ID, a BIG-endian 16-bit value whose top bit
 *   is reserved 0 and whose remaining 15 bits are three 5-bit letters
 *   (00001 = A .. 11010 = Z).
 * - Bytes 10..11: product code, 16-bit LITTLE-endian.
 * - Bytes 12..15: serial number, 32-bit little-endian.
 * - Byte 16: week of manufacture (0 = unspecified, 1..54, 0xFF = the
 *   year field is a model year instead).
 * - Byte 17: year value; year = 1990 + value.
 * - Bytes 18..19: EDID version and revision (version must be 1 here).
 * - Byte 126: extension block count; byte 127: checksum — the sum of
 *   all 128 bytes must be 0 mod 256.
 *
 * Pure JVM logic over a 128-byte array; refusal is
 * IllegalArgumentException. Parsing an EDID block says nothing about a
 * real display being attached — this kernel exists so stored transcripts
 * can be audited offline.
 */
object EdidHead {
    data class Info(
        val manufacturer: String,   // three letters, e.g. "DEL"
        val productCode: Int,
        val serial: Long,
        val week: Int?,             // null when unspecified (0)
        val year: Int,
        val modelYearFlag: Boolean, // true when week byte was 0xFF
        val version: Int,
        val revision: Int,
        val extensionBlocks: Int,
    )

    val HEADER = byteArrayOf(0, -1, -1, -1, -1, -1, -1, 0)

    fun parse(block: ByteArray): Info {
        require(block.size == 128) { "EDID base block must be 128 bytes, got ${block.size}" }
        for (i in 0 until 8) {
            require(block[i] == HEADER[i]) { "bad header byte at $i" }
        }
        var sum = 0
        for (b in block) sum = (sum + (b.toInt() and 0xFF)) and 0xFF
        require(sum == 0) { "checksum: 128-byte sum is $sum, must be 0 mod 256" }
        val mfr = ((block[8].toInt() and 0xFF) shl 8) or (block[9].toInt() and 0xFF)
        require(mfr and 0x8000 == 0) { "manufacturer ID reserved bit set" }
        val letters = CharArray(3)
        for (i in 0 until 3) {
            val v = (mfr shr (10 - 5 * i)) and 0x1F
            require(v in 1..26) { "manufacturer letter ${i + 1} out of 1..26: $v" }
            letters[i] = ('A' + v - 1)
        }
        val product = (block[10].toInt() and 0xFF) or ((block[11].toInt() and 0xFF) shl 8)
        var serial = 0L
        for (i in 0 until 4) serial = serial or ((block[12 + i].toLong() and 0xFF) shl (8 * i))
        val weekByte = block[16].toInt() and 0xFF
        val modelYear = weekByte == 0xFF
        require(modelYear || weekByte <= 54) { "week of manufacture out of 0..54: $weekByte" }
        val week = if (modelYear || weekByte == 0) null else weekByte
        val year = 1990 + (block[17].toInt() and 0xFF)
        val version = block[18].toInt() and 0xFF
        val revision = block[19].toInt() and 0xFF
        require(version == 1) { "unsupported EDID structure version $version" }
        val ext = block[126].toInt() and 0xFF
        return Info(String(letters), product, serial, week, year, modelYear, version, revision, ext)
    }
}
