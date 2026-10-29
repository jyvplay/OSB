// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import java.io.ByteArrayOutputStream

/**
 * Clean-room IETF RFC 8794 EBML VINT + WebM / Matroska (`0x1A45DFA3`) container probe and minimal
 * `EBML` + `Segment` + `Cluster` binary verifier for **Recall** WebM video/audio captures.
 */
object MatroskaWebmEbmlClusterProbe {
    const val SCOPE =
        "RFC 8794 EBML variable-size integer (VINT) reader/writer and WebM/Matroska 0x1A45DFA3 DocType/Segment/Cluster inspector."

    data class EbmlSummary(
        val docType: String,
        val docTypeVersion: Int,
        val hasSegment: Boolean,
        val clusterCount: Int,
        val simpleBlockCount: Int,
    )

    fun readVintLength(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        require(offset < bytes.size) { "EBML VINT EOF at $offset" }
        val first = bytes[offset].toInt() and 0xFF
        require(first != 0) { "Invalid zero-mask EBML VINT at $offset" }
        var width = 1
        var mask = 0x80
        while ((first and mask) == 0) {
            width++
            mask = mask ushr 1
        }
        require(offset + width <= bytes.size) { "Truncated $width-byte EBML VINT at $offset" }
        var value = (first and (mask - 1)).toLong()
        for (i in 1 until width) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        return value to width
    }

    fun readElementId(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        require(offset < bytes.size)
        val first = bytes[offset].toInt() and 0xFF
        require(first != 0) { "Invalid EBML Element ID leading 0x00" }
        var width = 1
        var mask = 0x80
        while ((first and mask) == 0 && width < 4) { width++; mask = mask ushr 1 }
        require(offset + width <= bytes.size)
        var id = 0L
        for (i in 0 until width) id = (id shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        return id to width
    }

    fun buildMinimalWebmClusterSample(docType: String = "webm", simpleBlocks: List<ByteArray>): ByteArray {
        require(docType in setOf("webm", "matroska") && simpleBlocks.isNotEmpty())
        val ebmlBody = ByteArrayOutputStream().apply {
            val dt = docType.toByteArray(Charsets.US_ASCII)
            write(byteArrayOf(0x42, 0x82.toByte())); write(encodeVintLen(dt.size)); write(dt) // DocType
            write(byteArrayOf(0x42, 0x87.toByte(), 0x81.toByte(), 0x04)) // DocTypeVersion = 4
        }.toByteArray()

        val clusterBody = ByteArrayOutputStream().apply {
            write(byteArrayOf(0xE7.toByte(), 0x81.toByte(), 0x00)) // Timestamp = 0
            for (sb in simpleBlocks) {
                write(byteArrayOf(0xA3.toByte())) // SimpleBlock
                write(encodeVintLen(sb.size))
                write(sb)
            }
        }.toByteArray()

        val segBody = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75)) // Cluster
            write(encodeVintLen(clusterBody.size))
            write(clusterBody)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())) // EBML Header
            write(encodeVintLen(ebmlBody.size))
            write(ebmlBody)
            write(byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67)) // Segment
            write(encodeVintLen(segBody.size))
            write(segBody)
        }.toByteArray()
    }

    fun inspectWebm(bytes: ByteArray): EbmlSummary {
        require(bytes.size >= 8 && readElementId(bytes, 0).first == 0x1A45DFA3L) { "Missing 0x1A45DFA3 EBML header" }
        var pos = 0
        var docType = ""
        var docVer = 1
        var hasSegment = false
        var clusters = 0
        var blocks = 0

        fun scanRange(start: Int, end: Int) {
            var p = start
            while (p < end) {
                val (id, idW) = readElementId(bytes, p); p += idW
                val (sz, szW) = readVintLength(bytes, p); p += szW
                val bodyEnd = (p + sz.toInt()).coerceAtMost(end)
                when (id) {
                    0x1A45DFA3L -> scanRange(p, bodyEnd)
                    0x4282L -> docType = String(bytes, p, bodyEnd - p, Charsets.US_ASCII).trimEnd('\u0000')
                    0x4287L -> docVer = bytes[p].toInt() and 0xFF
                    0x18538067L -> { hasSegment = true; scanRange(p, bodyEnd) }
                    0x1F43B675L -> { clusters++; scanRange(p, bodyEnd) }
                    0xA3L -> blocks++
                }
                p = bodyEnd
            }
        }
        scanRange(0, bytes.size)
        return EbmlSummary(docType, docVer, hasSegment, clusters, blocks)
    }

    private fun encodeVintLen(len: Int): ByteArray {
        require(len >= 0 && len < 0x0FFF_FFFF)
        return when {
            len < 0x7F -> byteArrayOf((0x80 or len).toByte())
            len < 0x3FFF -> byteArrayOf((0x40 or (len ushr 8)).toByte(), (len and 0xFF).toByte())
            else -> byteArrayOf(
                (0x20 or (len ushr 16)).toByte(),
                ((len ushr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte(),
            )
        }
    }
}
