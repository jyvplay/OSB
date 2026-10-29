// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * OASIS OpenDocument 1.3 Presentation (`.odp`) package builder writing an uncompressed `STORED`
 * first-entry `mimetype` (`application/vnd.oasis.opendocument.presentation`), `META-INF/manifest.xml`,
 * `styles.xml`, and `<office:presentation><draw:page>` slides with speaker `<presentation:notes>`.
 */
object OdfPresentationOdpBuilder {
    const val SCOPE =
        "OASIS ODF 1.3 .odp presentation ZIP builder enforcing uncompressed first-entry mimetype and <draw:page>/<presentation:notes>."

    const val ODP_MIME = "application/vnd.oasis.opendocument.presentation"

    data class OdpSlide(val title: String, val bullets: List<String>, val speakerNotes: String = "")

    fun buildOdpArchive(slides: List<OdpSlide>): ByteArray {
        require(slides.isNotEmpty()) { "ODP presentation requires at least 1 slide" }
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val mimeBytes = ODP_MIME.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimeBytes) }.value
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            putDeflated(zos, "META-INF/manifest.xml", buildManifestXml())
            putDeflated(zos, "styles.xml", "<office:document-styles xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" office:version=\"1.3\"/>")
            putDeflated(zos, "content.xml", buildContentXml(slides))
        }
        return baos.toByteArray()
    }

    fun inspectOdpArchive(odpBytes: ByteArray): Pair<String, Int> {
        ZipInputStream(ByteArrayInputStream(odpBytes)).use { zis ->
            val first = zis.nextEntry ?: error("Empty ODP zip")
            require(first.name == "mimetype") { "ODF §3.3 requires 'mimetype' as first ZIP entry, got ${first.name}" }
            val mime = String(zis.readBytes(), Charsets.US_ASCII)
            var pages = 0
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.name == "content.xml") {
                    val xml = String(zis.readBytes(), Charsets.UTF_8)
                    pages = Regex("<draw:page\\b").findAll(xml).count()
                }
            }
            return mime to pages
        }
    }

    private fun buildManifestXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.3\">" +
            "<manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.3\" manifest:media-type=\"$ODP_MIME\"/>" +
            "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>" +
            "<manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/>" +
            "</manifest:manifest>"

    private fun buildContentXml(slides: List<OdpSlide>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" ")
        append("xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" ")
        append("xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" ")
        append("xmlns:presentation=\"urn:oasis:names:tc:opendocument:xmlns:presentation:1.0\" office:version=\"1.3\">")
        append("<office:body><office:presentation>")
        for ((idx, s) in slides.withIndex()) {
            append("<draw:page draw:name=\"page${idx + 1}\">")
            append("<draw:frame presentation:class=\"title\"><draw:text-box><text:p>${esc(s.title)}</text:p></draw:text-box></draw:frame>")
            append("<draw:frame presentation:class=\"outline\"><draw:text-box><text:list>")
            for (b in s.bullets) append("<text:list-item><text:p>${esc(b)}</text:p></text:list-item>")
            append("</text:list></draw:text-box></draw:frame>")
            if (s.speakerNotes.isNotBlank()) {
                append("<presentation:notes><draw:frame><draw:text-box><text:p>${esc(s.speakerNotes)}</text:p></draw:text-box></draw:frame></presentation:notes>")
            }
            append("</draw:page>")
        }
        append("</office:presentation></office:body></office:document-content>")
    }

    private fun putDeflated(zos: ZipOutputStream, name: String, xml: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(xml.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
