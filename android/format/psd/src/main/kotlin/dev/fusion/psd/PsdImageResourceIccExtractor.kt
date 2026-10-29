// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Adobe Photoshop `.psd` (`8BPS`) Image Resource Block (`8BIM`) parser extracting embedded ICC
 * color profiles (`resourceId = 0x040F`, ISO 15076-1 `acsp` signature) and DPI resolution (`0x03ED`).
 */
object PsdImageResourceIccExtractor {
    const val SCOPE =
        "Photoshop 8BPS Image Resource Section (8BIM) parser extracting ICC color profile (0x040F acsp) and resolution info (0x03ED)."

    data class PsdIccSummary(
        val widthPx: Int,
        val heightPx: Int,
        val resourceIds: List<Int>,
        val iccColorSpaceFourCc: String?,
        val iccProfileSizeBytes: Int,
    )

    fun extractFromPsd(psdBytes: ByteArray): PsdIccSummary {
        require(psdBytes.size >= 34 && String(psdBytes, 0, 4, Charsets.US_ASCII) == "8BPS") {
            "Missing 8BPS PSD header"
        }
        val buf = ByteBuffer.wrap(psdBytes).order(ByteOrder.BIG_ENDIAN)
        buf.position(4)
        val ver = buf.short.toInt()
        require(ver == 1 || ver == 2) { "Unsupported PSD/PSB version $ver" }
        buf.position(14)
        val height = buf.int
        val width = buf.int
        buf.position(26)
        val colorModeLen = buf.int
        buf.position(buf.position() + colorModeLen)
        val irbLen = buf.int
        val irbEnd = buf.position() + irbLen
        require(irbEnd <= psdBytes.size) { "Truncated PSD Image Resource Section" }

        val ids = ArrayList<Int>()
        var iccSpace: String? = null
        var iccSize = 0
        while (buf.position() + 12 <= irbEnd) {
            val sig = ByteArray(4); buf.get(sig)
            require(String(sig, Charsets.US_ASCII) == "8BIM") { "Expected 8BIM resource signature" }
            val resId = buf.short.toInt() and 0xFFFF
            val nameLen = buf.get().toInt() and 0xFF
            // Pascal string padded to even total length (1 + nameLen)
            val nameAdvance = if ((1 + nameLen) % 2 == 1) nameLen + 1 else nameLen
            buf.position(buf.position() + nameAdvance)
            val dataLen = buf.int
            require(dataLen >= 0 && buf.position() + dataLen <= irbEnd)
            ids.add(resId)
            if (resId == 0x040F && dataLen >= 40) {
                val start = buf.position()
                val acsp = String(psdBytes, start + 36, 4, Charsets.US_ASCII)
                require(acsp == "acsp") { "Invalid ICC profile missing 'acsp' signature at +36" }
                iccSpace = String(psdBytes, start + 16, 4, Charsets.US_ASCII).trim()
                iccSize = dataLen
            }
            val paddedData = dataLen + (dataLen % 2)
            buf.position((buf.position() + paddedData).coerceAtMost(irbEnd))
        }
        return PsdIccSummary(width, height, ids, iccSpace, iccSize)
    }
}
