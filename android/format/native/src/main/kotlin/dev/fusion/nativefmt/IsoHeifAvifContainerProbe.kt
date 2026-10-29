// SPDX-License-Identifier: Apache-2.0
package dev.fusion.nativefmt

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-JVM ISO/IEC 23008-12 (`HEIF` / `AVIF`) & Matroska/WebM (`EBML`) binary container box/header
 * inspector for native format sniffing and Recall media metadata validation without native JNI codecs.
 */
object IsoHeifAvifContainerProbe {
    const val SCOPE =
        "ISO/IEC 23008-12 HEIF/AVIF (ftyp avif/avis/heic/mif1 + ispe spatial extents) and Matroska/WebM EBML magic header binary inspector."

    data class ContainerInspection(
        val formatFamily: String, // AVIF, HEIC, MP4, WEBM, MKV, UNKNOWN
        val majorBrand: String,
        val compatibleBrands: List<String>,
        val widthPx: Int?,
        val heightPx: Int?,
    )

    fun inspect(bytes: ByteArray): ContainerInspection {
        if (bytes.size >= 4 &&
            (bytes[0].toInt() and 0xFF) == 0x1A &&
            (bytes[1].toInt() and 0xFF) == 0x45 &&
            (bytes[2].toInt() and 0xFF) == 0xDF &&
            (bytes[3].toInt() and 0xFF) == 0xA3
        ) {
            val ascii = String(bytes.copyOfRange(0, minOf(bytes.size, 64)), Charsets.ISO_8859_1)
            val fam = if ("webm" in ascii.lowercase()) "WEBM" else "MKV"
            return ContainerInspection(fam, fam.lowercase(), listOf(fam.lowercase()), null, null)
        }
        if (bytes.size >= 16) {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val ftypLen = bb.getInt(0)
            val boxType = String(bytes, 4, 4, Charsets.US_ASCII)
            if (boxType == "ftyp" && ftypLen in 16..bytes.size) {
                val major = String(bytes, 8, 4, Charsets.US_ASCII)
                val compat = ArrayList<String>()
                var off = 16
                while (off + 4 <= ftypLen) {
                    compat.add(String(bytes, off, 4, Charsets.US_ASCII))
                    off += 4
                }
                val all = (listOf(major) + compat).map { it.trim() }
                val fam = when {
                    "avif" in all || "avis" in all -> "AVIF"
                    "heic" in all || "heix" in all || "mif1" in all -> "HEIC"
                    else -> "MP4"
                }
                val (w, h) = findIspeExtents(bytes)
                return ContainerInspection(fam, major.trim(), compat.map { it.trim() }, w, h)
            }
        }
        return ContainerInspection("UNKNOWN", "", emptyList(), null, null)
    }

    private fun findIspeExtents(bytes: ByteArray): Pair<Int?, Int?> {
        // Scan for ISO/IEC 23008-12 'ispe' FullBox: [size:4]['ispe':4][ver+flags:4][width:4][height:4]
        var i = 0
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        while (i + 20 <= bytes.size) {
            if (bytes[i + 4] == 'i'.code.toByte() &&
                bytes[i + 5] == 's'.code.toByte() &&
                bytes[i + 6] == 'p'.code.toByte() &&
                bytes[i + 7] == 'e'.code.toByte()
            ) {
                val len = bb.getInt(i)
                if (len == 20) {
                    val w = bb.getInt(i + 12)
                    val h = bb.getInt(i + 16)
                    if (w > 0 && h > 0) return w to h
                }
            }
            i++
        }
        return null to null
    }
}
