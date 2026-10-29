// SPDX-License-Identifier: Apache-2.0
package dev.fusion.psd

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Clean-room Adobe PSD **`8BIM` Image Resource Section Parser & EXIF/XMP/GPS Privacy Stripper**
 * (`0x0422` EXIF1, `0x0423` EXIF3, `0x0424` XMP, `0x040B` URL, `0x0404` IPTC-NAA stripped while
 * preserving safe layout resources like `0x03ED` `ResolutionInfo` DPI!).
 */
object PsdColorModeResources {
    const val SCOPE =
        "Clean-room Adobe PSD 8BIM Image Resource Block parser, DPI extractor (0x03ED), and EXIF/XMP/IPTC/URL privacy metadata stripper."

    const val RES_RESOLUTION_INFO = 0x03ED
    private val SENSITIVE_RESOURCE_IDS = setOf(0x0404, 0x0409, 0x040A, 0x040B, 0x0422, 0x0423, 0x0424, 0x0425)

    data class ResourceBlock(
        val id: Int,
        val pascalName: String,
        val data: ByteArray,
    )

    fun encodeSection(blocks: List<ResourceBlock>): ByteArray {
        val out = ByteArrayOutputStream()
        for (b in blocks) {
            out.write("8BIM".toByteArray(Charsets.US_ASCII))
            out.write((b.id ushr 8) and 0xFF); out.write(b.id and 0xFF)
            val nameBytes = b.pascalName.toByteArray(Charsets.US_ASCII)
            require(nameBytes.size <= 255) { "Pascal string > 255 bytes" }
            out.write(nameBytes.size)
            out.write(nameBytes)
            // Pascal string (1 + len) is padded to an EVEN byte count!
            if ((1 + nameBytes.size) % 2 != 0) out.write(0)
            val sz = b.data.size
            out.write((sz ushr 24) and 0xFF); out.write((sz ushr 16) and 0xFF)
            out.write((sz ushr 8) and 0xFF); out.write(sz and 0xFF)
            out.write(b.data)
            if (sz % 2 != 0) out.write(0) // resource data padded to even length!
        }
        return out.toByteArray()
    }

    fun parseSection(sectionBytes: ByteArray): List<ResourceBlock> {
        val out = ArrayList<ResourceBlock>()
        var pos = 0
        val buf = ByteBuffer.wrap(sectionBytes).order(ByteOrder.BIG_ENDIAN)
        while (pos < sectionBytes.size) {
            require(pos + 7 <= sectionBytes.size) { "truncated 8BIM header at $pos" }
            val sig = String(sectionBytes, pos, 4, Charsets.US_ASCII)
            require(sig == "8BIM") { "expected 8BIM signature, got '$sig'" }
            val id = buf.getShort(pos + 4).toInt() and 0xFFFF
            val nameLen = sectionBytes[pos + 6].toInt() and 0xFF
            val paddedNameField = 1 + nameLen + (if ((1 + nameLen) % 2 != 0) 1 else 0)
            require(pos + 6 + paddedNameField + 4 <= sectionBytes.size) { "truncated 8BIM name/size" }
            val name = String(sectionBytes, pos + 7, nameLen, Charsets.US_ASCII)
            val sizeOff = pos + 6 + paddedNameField
            val dataLen = buf.getInt(sizeOff)
            val dataStart = sizeOff + 4
            require(dataLen >= 0 && dataStart + dataLen <= sectionBytes.size) { "truncated 8BIM payload" }
            val data = sectionBytes.copyOfRange(dataStart, dataStart + dataLen)
            out.add(ResourceBlock(id, name, data))
            pos = dataStart + dataLen + (if (dataLen % 2 != 0) 1 else 0)
        }
        return out
    }

    fun stripPrivacyMetadata(sectionBytes: ByteArray): Pair<ByteArray, List<Int>> {
        val blocks = parseSection(sectionBytes)
        val removed = blocks.filter { it.id in SENSITIVE_RESOURCE_IDS }.map { it.id }
        val kept = blocks.filterNot { it.id in SENSITIVE_RESOURCE_IDS }
        return encodeSection(kept) to removed
    }
}
