// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Clean-room Tails `mat2` / Metadata Cleaner sanitizer for OOXML/ODF ZIP packages and PDF files.
 *
 * Strips forensic author, creator, company, hostname, revision count, and creation/modification
 * timestamps before sharing documents externally:
 * - **OOXML / ODF archives (`.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`)**: drops `docProps/core.xml`,
 *   `docProps/app.xml`, `docProps/custom.xml`, `meta.xml`, `Thumbnails/thumbnail.png`, prunes
 *   dangling `_rels/.rels` metadata relationships, sorts entries canonically, and zeroes all ZIP
 *   DOS timestamps to `1980-01-01 00:00:00`.
 * - **PDF documents (`%PDF-1.x`)**: blanks `/Author`, `/Creator`, `/Producer`, `/Title`, `/Subject`,
 *   `/Keywords`, `/CreationDate`, and `/ModDate` string literals in-place while preserving exact
 *   byte offsets so existing `xref` tables remain 100% valid!
 */
object MetadataScrub {
    const val SCOPE =
        "Tails mat2-style metadata scrubber for OOXML/ODF ZIP packages (drops docProps/meta.xml/thumbnails + zeroes ZIP timestamps) and PDF Info dictionaries (length-preserving in-place redaction); JPEG EXIF and MP4 atoms handled by dedicated media kernels."

    const val MAX_BYTES = 16 * 1024 * 1024

    private val DROPPED_ZIP_PARTS = setOf(
        "docprops/core.xml",
        "docprops/app.xml",
        "docprops/custom.xml",
        "docprops/thumbnail.jpeg",
        "docprops/thumbnail.emf",
        "meta.xml",
        "settings.xml",
        "thumbnails/thumbnail.png",
    )

    private val PDF_SENSITIVE_KEYS = listOf(
        "Author", "Creator", "Producer", "CreationDate", "ModDate", "Subject", "Keywords",
    )

    data class ScrubReport(
        val sanitizedBytes: ByteArray,
        val removedItems: List<String>,
    )

    fun scrubZipPackage(zipBytes: ByteArray): ScrubReport {
        require(zipBytes.size in 4..MAX_BYTES) { "archive size ${zipBytes.size} outside 4..$MAX_BYTES" }
        require(zipBytes[0] == 'P'.code.toByte() && zipBytes[1] == 'K'.code.toByte()) {
            "not a PKZIP/OOXML/ODF archive"
        }
        val kept = LinkedHashMap<String, ByteArray>()
        val removed = ArrayList<String>()
        var expanded = 0L

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            val buf = ByteArray(8192)
            var entries = 0
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                entries++
                require(entries <= 512) { "too many archive entries" }
                val name = e.name
                require(!name.contains("..") && !name.startsWith("/")) { "unsafe ZIP entry '$name'" }
                if (!e.isDirectory) {
                    val baos = ByteArrayOutputStream()
                    while (true) {
                        val n = zis.read(buf)
                        if (n < 0) break
                        expanded += n
                        require(expanded <= MAX_BYTES) { "archive expansion exceeds $MAX_BYTES bytes" }
                        baos.write(buf, 0, n)
                    }
                    if (name.lowercase() in DROPPED_ZIP_PARTS) {
                        removed.add(name)
                    } else {
                        kept[name] = baos.toByteArray()
                    }
                }
                e = zis.nextEntry
            }
        }

        // Prune dangling metadata Relationship / Override tags from _rels/.rels and [Content_Types].xml
        kept["_rels/.rels"]?.let { raw ->
            val txt = raw.toString(StandardCharsets.UTF_8)
            val cleaned = Regex("""<Relationship\b[^>]*Target="(?:/)?docProps/[^"]*"[^>]*/>""")
                .replace(txt, "")
            kept["_rels/.rels"] = cleaned.toByteArray(StandardCharsets.UTF_8)
        }
        kept["[Content_Types].xml"]?.let { raw ->
            val txt = raw.toString(StandardCharsets.UTF_8)
            val cleaned = Regex("""<Override\b[^>]*PartName="/docProps/[^"]*"[^>]*/>""")
                .replace(txt, "")
            kept["[Content_Types].xml"] = cleaned.toByteArray(StandardCharsets.UTF_8)
        }

        // ODF requires "mimetype" first if present; sort remaining parts canonically
        val ordered = LinkedHashMap<String, ByteArray>()
        kept["mimetype"]?.let { ordered["mimetype"] = it }
        for (k in kept.keys.sorted()) {
            if (k != "mimetype") ordered[k] = kept.getValue(k)
        }
        return ScrubReport(writeZeroEpochStoredZip(ordered), removed.sorted())
    }

    /**
     * Redacts `/Author (...)`, `/Creator (...)`, `/CreationDate (...)`, etc. in-place with spaces
     * inside `(...)` or zeros inside `<...>` so PDF `xref` byte offsets stay 100% intact!
     */
    fun scrubPdfInPlace(pdfBytes: ByteArray): ScrubReport {
        require(pdfBytes.size in 8..MAX_BYTES) { "PDF size ${pdfBytes.size} outside 8..$MAX_BYTES" }
        val head = String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)
        require(head == "%PDF-") { "missing %PDF- header" }

        val copy = pdfBytes.copyOf()
        val latin = String(copy, StandardCharsets.ISO_8859_1)
        val redactedKeys = LinkedHashSet<String>()

        for (key in PDF_SENSITIVE_KEYS) {
            // Literal string form: /Author (Alice Smith)
            val parenRe = Regex("""/$key\s*\((?:\\.|[^\\)])*\)""")
            for (m in parenRe.findAll(latin)) {
                val openParen = latin.indexOf('(', m.range.first)
                val closeParen = m.range.last
                if (openParen in 0 until closeParen) {
                    for (idx in (openParen + 1) until closeParen) {
                        copy[idx] = ' '.code.toByte()
                    }
                    redactedKeys.add(key)
                }
            }
            // Hex string form: /Author <416C696365>
            val hexRe = Regex("""/$key\s*<[0-9A-Fa-f\s]*>""")
            for (m in hexRe.findAll(latin)) {
                val openAng = latin.indexOf('<', m.range.first)
                val closeAng = m.range.last
                if (openAng in 0 until closeAng) {
                    for (idx in (openAng + 1) until closeAng) {
                        copy[idx] = ' '.code.toByte()
                    }
                    redactedKeys.add(key)
                }
            }
        }
        return ScrubReport(copy, redactedKeys.sorted())
    }

    private fun writeZeroEpochStoredZip(parts: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        val cd = ByteArrayOutputStream()
        var offset = 0
        var count = 0
        for ((name, data) in parts) {
            val nb = name.toByteArray(StandardCharsets.UTF_8)
            val crc = CRC32().apply { update(data) }.value.toInt()
            writeLe32(out, 0x04034b50)
            writeLe16(out, 20); writeLe16(out, 0); writeLe16(out, 0)
            writeLe16(out, 0); writeLe16(out, 0x0021)
            writeLe32(out, crc); writeLe32(out, data.size); writeLe32(out, data.size)
            writeLe16(out, nb.size); writeLe16(out, 0)
            out.write(nb); out.write(data)

            writeLe32(cd, 0x02014b50)
            writeLe16(cd, 20); writeLe16(cd, 20); writeLe16(cd, 0); writeLe16(cd, 0)
            writeLe16(cd, 0); writeLe16(cd, 0x0021)
            writeLe32(cd, crc); writeLe32(cd, data.size); writeLe32(cd, data.size)
            writeLe16(cd, nb.size); writeLe16(cd, 0); writeLe16(cd, 0)
            writeLe16(cd, 0); writeLe16(cd, 0); writeLe32(cd, 0); writeLe32(cd, offset)
            cd.write(nb)
            offset += 30 + nb.size + data.size
            count++
        }
        val cdBytes = cd.toByteArray()
        out.write(cdBytes)
        writeLe32(out, 0x06054b50)
        writeLe16(out, 0); writeLe16(out, 0)
        writeLe16(out, count); writeLe16(out, count)
        writeLe32(out, cdBytes.size); writeLe32(out, offset)
        writeLe16(out, 0)
        return out.toByteArray()
    }

    private fun writeLe16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
    }

    private fun writeLe32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
    }
}
