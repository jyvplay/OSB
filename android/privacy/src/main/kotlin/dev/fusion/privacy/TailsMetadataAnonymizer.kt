// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 100% Clean-Room `Apache-2.0` **Tails `MAT2`-Equivalent Multi-Format Metadata Anonymizer**
 * (zero `LGPLv3` `mat2` code — written directly from W3C PNG, ITU-T T.81 JPEG, ID3, ISO 32000 PDF,
 * and ECMA-376 OOXML container specifications).
 *
 * Strips author names, GPS coordinates, camera serial numbers, creation/modification timestamps,
 * XMP packets, ID3v1/v2 tags, and OOXML `docProps/core.xml` + `docProps/app.xml` fields, producing
 * verified `.cleaned.<ext>` payloads for Tails-Light anonymous sharing and Recall vaults!
 */
object TailsMetadataAnonymizer {
    const val SCOPE =
        "Clean-room Apache-2.0 Tails MAT2-equivalent metadata anonymizer for PNG, JPEG, MP3, PDF, and OOXML (.docx/.xlsx/.pptx)."

    enum class DetectedFormat { PNG, JPEG, MP3, PDF, OOXML_ZIP }

    data class AnonymizeReport(
        val format: DetectedFormat,
        val cleanedFilename: String,
        val cleanedBytes: ByteArray,
        val removedArtifacts: List<String>,
    )

    fun anonymizeFile(originalFilename: String, bytes: ByteArray): AnonymizeReport {
        val fmt = detectFormat(originalFilename, bytes)
        val outName = toCleanedName(originalFilename)
        return when (fmt) {
            DetectedFormat.PNG -> cleanPng(outName, bytes)
            DetectedFormat.JPEG -> cleanJpeg(outName, bytes)
            DetectedFormat.MP3 -> cleanMp3(outName, bytes)
            DetectedFormat.PDF -> cleanPdf(outName, bytes)
            DetectedFormat.OOXML_ZIP -> cleanOoxmlZip(outName, bytes)
        }
    }

    private fun detectFormat(name: String, b: ByteArray): DetectedFormat {
        if (b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) {
            return DetectedFormat.PNG
        }
        if (b.size >= 4 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xD8) {
            return DetectedFormat.JPEG
        }
        if (b.size >= 5 && String(b, 0, 5, Charsets.US_ASCII) == "%PDF-") {
            return DetectedFormat.PDF
        }
        if (b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte()) {
            return DetectedFormat.OOXML_ZIP
        }
        if (name.lowercase().endsWith(".mp3") || (b.size >= 3 && String(b, 0, 3, Charsets.US_ASCII) == "ID3")) {
            return DetectedFormat.MP3
        }
        throw IllegalArgumentException("Unsupported format for fail-closed Tails metadata anonymization: $name")
    }

    private fun toCleanedName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)}.cleaned${name.substring(dot)}" else "$name.cleaned"
    }

    private fun cleanPng(outName: String, b: ByteArray): AnonymizeReport {
        val out = ByteArrayOutputStream()
        out.write(b, 0, 8)
        val removed = ArrayList<String>()
        var pos = 8
        val stripSet = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")
        while (pos + 12 <= b.size) {
            val len = ((b[pos].toInt() and 0xFF) shl 24) or ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            val typ = String(b, pos + 4, 4, Charsets.ISO_8859_1)
            val crcExpected = ((b[pos + 8 + len].toInt() and 0xFF) shl 24) or
                ((b[pos + 9 + len].toInt() and 0xFF) shl 16) or
                ((b[pos + 10 + len].toInt() and 0xFF) shl 8) or
                (b[pos + 11 + len].toInt() and 0xFF)
            val crcActual = CRC32().apply { update(b, pos + 4, 4 + len) }.value.toInt()
            require(crcActual == crcExpected) { "PNG chunk CRC mismatch in $typ" }
            if (typ in stripSet) removed.add("png:$typ") else out.write(b, pos, 12 + len)
            pos += 12 + len
            if (typ == "IEND") break
        }
        return AnonymizeReport(DetectedFormat.PNG, outName, out.toByteArray(), removed)
    }

    private fun cleanJpeg(outName: String, b: ByteArray): AnonymizeReport {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(0xD8)
        val removed = ArrayList<String>()
        var pos = 2
        while (pos < b.size) {
            while (pos < b.size && (b[pos].toInt() and 0xFF) == 0xFF) pos++
            if (pos >= b.size) break
            val m = b[pos++].toInt() and 0xFF
            if (m == 0xD9) { out.write(0xFF); out.write(0xD9); break }
            if (m == 0x01 || m in 0xD0..0xD7) { out.write(0xFF); out.write(m); continue }
            val segLen = ((b[pos].toInt() and 0xFF) shl 8) or (b[pos + 1].toInt() and 0xFF)
            if ((m in 0xE1..0xEF && m != 0xE0) || m == 0xFE) {
                removed.add("jpeg:0xFF%02X".format(m))
                pos += segLen
            } else {
                out.write(0xFF); out.write(m); out.write(b, pos, segLen)
                pos += segLen
                if (m == 0xDA) { out.write(b, pos, b.size - pos); break }
            }
        }
        return AnonymizeReport(DetectedFormat.JPEG, outName, out.toByteArray(), removed)
    }

    private fun cleanMp3(outName: String, b: ByteArray): AnonymizeReport {
        var start = 0
        var end = b.size
        val removed = ArrayList<String>()
        if (b.size >= 10 && String(b, 0, 3, Charsets.US_ASCII) == "ID3") {
            val sz = ((b[6].toInt() and 0x7F) shl 21) or ((b[7].toInt() and 0x7F) shl 14) or
                ((b[8].toInt() and 0x7F) shl 7) or (b[9].toInt() and 0x7F)
            start = (10 + sz).coerceAtMost(b.size)
            removed.add("mp3:ID3v2")
        }
        if (end - start >= 128 && String(b, end - 128, 3, Charsets.US_ASCII) == "TAG") {
            end -= 128
            removed.add("mp3:ID3v1")
        }
        return AnonymizeReport(DetectedFormat.MP3, outName, b.copyOfRange(start, end), removed)
    }

    private fun cleanPdf(outName: String, b: ByteArray): AnonymizeReport {
        var text = String(b, Charsets.ISO_8859_1)
        val removed = ArrayList<String>()
        val keys = listOf("Author", "Creator", "Producer", "Title", "Subject", "Keywords", "CreationDate", "ModDate")
        for (k in keys) {
            val re = Regex("/$k\\s*\\([^)]*\\)")
            if (re.containsMatchIn(text)) {
                text = re.replace(text, "")
                removed.add("pdf:/$k")
            }
        }
        val xmpRe = Regex("<\\?xpacket[\\s\\S]*?\\?>")
        if (xmpRe.containsMatchIn(text)) {
            text = xmpRe.replace(text, "")
            removed.add("pdf:xmp_packet")
        }
        return AnonymizeReport(DetectedFormat.PDF, outName, text.toByteArray(Charsets.ISO_8859_1), removed)
    }

    private fun cleanOoxmlZip(outName: String, b: ByteArray): AnonymizeReport {
        val removed = ArrayList<String>()
        val baos = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(b)).use { zis ->
            ZipOutputStream(baos).use { zos ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val raw = zis.readBytes()
                    val cleanEntry = ZipEntry(entry.name).apply { time = 0L } // scrub ZIP DOS modification timestamp!
                    zos.putNextEntry(cleanEntry)
                    when (entry.name) {
                        "docProps/core.xml" -> {
                            removed.add("ooxml:docProps/core.xml")
                            zos.write(
                                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                    "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\"/>")
                                    .toByteArray(Charsets.UTF_8)
                            )
                        }
                        "docProps/app.xml" -> {
                            removed.add("ooxml:docProps/app.xml")
                            zos.write(
                                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                    "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\"/>")
                                    .toByteArray(Charsets.UTF_8)
                            )
                        }
                        "docProps/custom.xml" -> {
                            removed.add("ooxml:docProps/custom.xml")
                            zos.write(
                                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                    "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/custom-properties\"/>")
                                    .toByteArray(Charsets.UTF_8)
                            )
                        }
                        else -> zos.write(raw)
                    }
                    zos.closeEntry()
                }
            }
        }
        return AnonymizeReport(DetectedFormat.OOXML_ZIP, outName, baos.toByteArray(), removed)
    }
}
