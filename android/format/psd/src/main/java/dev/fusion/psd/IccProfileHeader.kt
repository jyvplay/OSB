// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room ISO 15076-1 / ICC.1:2022 128-byte ICC Color Profile header & tag-table validator (`IccProfileHeader`).
 *
 * Powers `:format:psd` embedded color-profile (`Image Resource 0x040F`) inspection and bounds validation:
 * - Verifies exact `profileSize` (`>= 132` bytes), `'acsp'` (`0x61637370`) magic signature at offset `36..39`,
 *   device class (`scnr`, `mntr`, `prtr`, `link`, `spac`, `abst`, `nmcl`), data color space (`RGB `, `CMYK`,
 *   `GRAY`, `Lab `, `XYZ `), PCS (`XYZ ` or `Lab `), rendering intent (`0..3`), and tag-table byte bounds
 *   (defeating integer-overflow out-of-bounds reads in malformed embedded ICC profiles!).
 */
object IccProfileHeader {
    const val SCOPE =
        "ISO 15076-1 ICC v2/v4 128-byte header + tag-table bounds inspector ('acsp' magic, class/space/PCS/intent, overflow-safe tag slices); LUT color-transform math out of scope."

    const val HEADER_SIZE = 128
    const val ACSP_MAGIC = 0x61637370 // 'acsp'

    private val VALID_CLASSES = setOf("scnr", "mntr", "prtr", "link", "spac", "abst", "nmcl")
    private val VALID_SPACES = setOf("XYZ ", "Lab ", "RGB ", "GRAY", "CMYK", "HLS ", "HSV ", "YCbr")
    private val VALID_PCS = setOf("XYZ ", "Lab ")

    data class TagEntry(val signature: String, val offset: Int, val size: Int)

    data class ProfileSummary(
        val profileSize: Int,
        val majorVersion: Int,
        val minorVersion: Int,
        val deviceClass: String,
        val colorSpace: String,
        val pcs: String,
        val renderingIntent: Int,
        val tags: List<TagEntry>,
    )

    fun parseAndValidate(iccBytes: ByteArray): ProfileSummary {
        require(iccBytes.size >= HEADER_SIZE + 4) { "ICC profile shorter than 132 bytes (${iccBytes.size})" }
        val bb = ByteBuffer.wrap(iccBytes).order(ByteOrder.BIG_ENDIAN)
        val declaredSize = bb.getInt(0)
        require(declaredSize == iccBytes.size) {
            "ICC header size ($declaredSize) != buffer length (${iccBytes.size})"
        }
        require(bb.getInt(36) == ACSP_MAGIC) { "Missing ICC 'acsp' (0x61637370) signature at offset 36" }

        val verByte = iccBytes[8].toInt() and 0xFF
        val major = verByte
        val minor = (iccBytes[9].toInt() ushr 4) and 0x0F
        require(major in setOf(2, 4)) { "Unsupported ICC major version: $major" }

        val devClass = ascii4(iccBytes, 12)
        val colorSpace = ascii4(iccBytes, 16)
        val pcs = ascii4(iccBytes, 20)
        require(devClass in VALID_CLASSES) { "Invalid ICC profile class: '$devClass'" }
        require(colorSpace in VALID_SPACES) { "Invalid ICC color space: '$colorSpace'" }
        require(pcs in VALID_PCS) { "Invalid ICC PCS: '$pcs'" }

        val intent = bb.getInt(64)
        require(intent in 0..3) { "Invalid ICC rendering intent (must be 0..3): $intent" }

        val tagCount = bb.getInt(128)
        require(tagCount in 0..256) { "Unreasonable ICC tag count: $tagCount" }
        val tableEnd = 132L + tagCount.toLong() * 12L
        require(tableEnd <= iccBytes.size.toLong()) { "ICC tag directory overflows profile size" }

        val tags = ArrayList<TagEntry>(tagCount)
        for (i in 0 until tagCount) {
            val base = 132 + i * 12
            val sig = ascii4(iccBytes, base)
            val off = bb.getInt(base + 4)
            val sz = bb.getInt(base + 8)
            require(off >= tableEnd && sz >= 0 && off.toLong() + sz.toLong() <= iccBytes.size.toLong()) {
                "ICC tag '$sig' slice ($off + $sz) overflows profile (${iccBytes.size})"
            }
            tags.add(TagEntry(sig, off, sz))
        }

        return ProfileSummary(
            profileSize = declaredSize,
            majorVersion = major,
            minorVersion = minor,
            deviceClass = devClass,
            colorSpace = colorSpace,
            pcs = pcs,
            renderingIntent = intent,
            tags = tags,
        )
    }

    fun buildMinimalSrgbHeader(descriptionAscii: String = "Fusion sRGB"): ByteArray {
        val descBytes = descriptionAscii.toByteArray(StandardCharsets.US_ASCII)
        // Minimal 1-tag ('desc') ICC v4 profile: 128 header + 4 count + 12 tag entry + aligned 'mluc' tag payload
        val tagOffset = 144
        val utf16 = descriptionAscii.toByteArray(StandardCharsets.UTF_16BE)
        val tagSize = 28 + utf16.size
        val paddedTotal = ((tagOffset + tagSize + 3) / 4) * 4
        val buf = ByteArray(paddedTotal)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(0, paddedTotal)
        buf[8] = 4 // ICC v4.3
        buf[9] = 0x30
        putAscii4(buf, 12, "mntr")
        putAscii4(buf, 16, "RGB ")
        putAscii4(buf, 20, "XYZ ")
        bb.putInt(36, ACSP_MAGIC)
        // D50 PCS illuminant s15Fixed16Number: X=0.9642 (0x0000F6D6), Y=1.0000 (0x00010000), Z=0.8249 (0x0000D32D)
        bb.putInt(68, 0x0000F6D6)
        bb.putInt(72, 0x00010000)
        bb.putInt(76, 0x0000D32D)
        // Tag table: 1 tag ('desc')
        bb.putInt(128, 1)
        putAscii4(buf, 132, "desc")
        bb.putInt(136, tagOffset)
        bb.putInt(140, tagSize)
        // 'mluc' multiLocalizedUnicodeType payload
        putAscii4(buf, tagOffset, "mluc")
        bb.putInt(tagOffset + 8, 1) // 1 record
        bb.putInt(tagOffset + 12, 12) // record size
        buf[tagOffset + 16] = 'e'.code.toByte(); buf[tagOffset + 17] = 'n'.code.toByte()
        buf[tagOffset + 18] = 'U'.code.toByte(); buf[tagOffset + 19] = 'S'.code.toByte()
        bb.putInt(tagOffset + 20, utf16.size)
        bb.putInt(tagOffset + 24, 28)
        System.arraycopy(utf16, 0, buf, tagOffset + 28, utf16.size)
        check(descBytes.isNotEmpty())
        return buf
    }

    private fun ascii4(b: ByteArray, off: Int): String = String(b, off, 4, StandardCharsets.US_ASCII)
    private fun putAscii4(b: ByteArray, off: Int, s: String) {
        val raw = s.toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(raw, 0, b, off, 4)
    }
}
