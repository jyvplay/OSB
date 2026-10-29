// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room recursive file metadata anonymizer inspired by Tails OS `mat2` / Metadata Cleaner
 * (`https://tails.net/doc/sensitive_documents/metadata/index.en.html`).
 *
 * Specifically solves the nested-media gap highlighted by Tails documentation: inside OOXML/ODF
 * ZIP containers (`.docx`, `.xlsx`, `.pptx`, `.odt`), scrubs BOTH top-level `docProps/core.xml` +
 * `docProps/app.xml` + `docProps/custom.xml` AND recursively strips `APP1` (EXIF/GPS/XMP), `APP13`
 * (IPTC), and `COM` segments from embedded JPEGs plus `tEXt`, `zTXt`, `iTXt`, `eXIf`, and `tIME`
 * chunks from embedded PNGs, resetting all ZIP entry modification timestamps to zero-epoch!
 */
object Mat2RecursiveMetadataScrubber {
    const val SCOPE =
        "Pure-JVM recursive metadata scrubber for JPEG (APP1/APP13/COM), PNG (tEXt/zTXt/iTXt/eXIf/tIME), and nested OOXML/ODF ZIP archives with zero-epoch entry normalization."

    data class ScrubReport(
        val scrubbedBytes: ByteArray,
        val removedSegmentsOrParts: Int,
        val nestedMediaCleaned: Int,
        val formatDetected: String,
    )

    fun scrub(bytes: ByteArray): ScrubReport = when {
        isJpeg(bytes) -> scrubJpeg(bytes)
        isPng(bytes) -> scrubPng(bytes)
        isZip(bytes) -> scrubZipContainer(bytes)
        else -> throw IllegalArgumentException("unsupported container/image header for metadata scrubbing")
    }

    fun isJpeg(b: ByteArray): Boolean = b.size >= 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()

    fun isPng(b: ByteArray): Boolean = b.size >= 8 &&
        b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()

    fun isZip(b: ByteArray): Boolean = b.size >= 4 &&
        b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte()

    fun scrubJpeg(bytes: ByteArray): ScrubReport {
        require(isJpeg(bytes)) { "invalid JPEG SOI marker" }
        val out = ByteArrayOutputStream(bytes.size)
        out.write(0xFF); out.write(0xD8)
        var pos = 2
        var removed = 0
        while (pos + 1 < bytes.size) {
            if (bytes[pos] != 0xFF.toByte()) {
                out.write(bytes, pos, bytes.size - pos)
                break
            }
            while (pos < bytes.size && bytes[pos] == 0xFF.toByte()) pos++
            if (pos >= bytes.size) break
            val marker = bytes[pos++].toInt() and 0xFF
            if (marker == 0xD9) { // EOI
                out.write(0xFF); out.write(0xD9)
                break
            }
            if (marker == 0x00 || marker in 0xD0..0xD7 || marker == 0x01) {
                out.write(0xFF); out.write(marker)
                continue
            }
            require(pos + 2 <= bytes.size) { "truncated JPEG segment length" }
            val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
            require(segLen >= 2 && pos + segLen <= bytes.size) { "malformed JPEG segment length $segLen" }
            val isMetadataMarker = marker in 0xE1..0xEF || marker == 0xFE // APP1..APP15 (EXIF/XMP/IPTC/ICC) + COM
            if (isMetadataMarker) {
                removed++
            } else {
                out.write(0xFF); out.write(marker)
                out.write(bytes, pos, segLen)
            }
            pos += segLen
            if (marker == 0xDA) { // SOS: copy entropy-coded scan through end
                out.write(bytes, pos, bytes.size - pos)
                break
            }
        }
        return ScrubReport(out.toByteArray(), removed, 0, "JPEG")
    }

    fun scrubPng(bytes: ByteArray): ScrubReport {
        require(isPng(bytes)) { "invalid PNG 8-byte signature" }
        val out = ByteArrayOutputStream(bytes.size)
        out.write(bytes, 0, 8)
        var pos = 8
        var removed = 0
        val stripTypes = setOf("tEXt", "zTXt", "iTXt", "eXIf", "tIME")
        while (pos + 12 <= bytes.size) {
            val len = ByteBuffer.wrap(bytes, pos, 4).order(ByteOrder.BIG_ENDIAN).int
            require(len >= 0 && pos.toLong() + 12L + len <= bytes.size) { "malformed PNG chunk length $len" }
            val type = String(bytes, pos + 4, 4, StandardCharsets.US_ASCII)
            val totalChunk = 12 + len
            if (type in stripTypes) {
                removed++
            } else {
                out.write(bytes, pos, totalChunk)
            }
            pos += totalChunk
            if (type == "IEND") break
        }
        return ScrubReport(out.toByteArray(), removed, 0, "PNG")
    }

    fun scrubZipContainer(bytes: ByteArray): ScrubReport {
        require(isZip(bytes) && bytes.size <= 32 * 1024 * 1024) { "invalid or oversized ZIP container" }
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..")) { "zip-slip path refused: ${e.name}" }
                if (!e.isDirectory) {
                    entries[e.name] = zis.readBytes()
                }
            }
        }

        var removed = 0
        var nestedCleaned = 0
        val cleanedEntries = LinkedHashMap<String, ByteArray>()
        for ((name, raw) in entries) {
            val lower = name.lowercase()
            when {
                lower == "docprops/custom.xml" || lower == "meta.xml" -> {
                    if (lower == "docprops/custom.xml") {
                        removed++
                        continue
                    }
                    removed++
                    cleanedEntries[name] = """<?xml version="1.0" encoding="UTF-8"?><office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"/>""".toByteArray(StandardCharsets.UTF_8)
                }
                lower == "docprops/core.xml" -> {
                    removed++
                    cleanedEntries[name] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"/>""".toByteArray(StandardCharsets.UTF_8)
                }
                lower == "docprops/app.xml" -> {
                    removed++
                    cleanedEntries[name] = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"/>""".toByteArray(StandardCharsets.UTF_8)
                }
                isJpeg(raw) -> {
                    val sub = scrubJpeg(raw)
                    if (sub.removedSegmentsOrParts > 0) {
                        nestedCleaned++
                        removed += sub.removedSegmentsOrParts
                    }
                    cleanedEntries[name] = sub.scrubbedBytes
                }
                isPng(raw) -> {
                    val sub = scrubPng(raw)
                    if (sub.removedSegmentsOrParts > 0) {
                        nestedCleaned++
                        removed += sub.removedSegmentsOrParts
                    }
                    cleanedEntries[name] = sub.scrubbedBytes
                }
                lower.endsWith(".xml") -> {
                    val xmlTxt = String(raw, StandardCharsets.UTF_8)
                    val strippedRsid = Regex("""\s+w:rsid[A-Za-z]*="[0-9A-Fa-f]+"""").replace(xmlTxt, "")
                    if (strippedRsid != xmlTxt) removed++
                    cleanedEntries[name] = strippedRsid.toByteArray(StandardCharsets.UTF_8)
                }
                else -> cleanedEntries[name] = raw
            }
        }

        val bos = ByteArrayOutputStream(bytes.size)
        ZipOutputStream(bos).use { zos ->
            for (name in cleanedEntries.keys.sorted()) {
                val data = cleanedEntries.getValue(name)
                val ze = ZipEntry(name).apply {
                    time = 0L // Tails/mat2 deterministic zero-epoch normalization!
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    val crc = CRC32(); crc.update(data)
                    this.crc = crc.value
                }
                zos.putNextEntry(ze)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return ScrubReport(bos.toByteArray(), removed, nestedCleaned, "ZIP_CONTAINER")
    }
}
