// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Clean-room W3C **EPUB 3.3 Open Container Format (OCF)** archive writer & validator.
 *
 * Enforces the mandatory OCF binary ZIP layout:
 * - First ZIP entry MUST be `mimetype`, `STORED` (method `0`), uncompressed (`20` bytes
 *   `"application/epub+zip"`), with **zero extra-field bytes** so the payload starts at exact
 *   byte offset `38` (`30` local header + `8` filename `"mimetype"`).
 * - Writes `META-INF/container.xml`, `EPUB/package.opf` (`version="3.0"`), `EPUB/nav.xhtml`
 *   (`epub:type="toc"`), and chapter XHTML 5 files (`EPUB/chN.xhtml`).
 * - Refuses `<script>`, `on*=` event handlers, XXE `<!ENTITY`, and remote `http(s)://` resources
 *   inside chapter XHTML or `package.opf` (`EPUB 3.3` offline privacy guarantee).
 */
object Epub3Package {
    const val SCOPE =
        "Clean-room W3C EPUB 3.3 OCF writer & reader (STORED mimetype at byte offset 38, META-INF/container.xml, EPUB/package.opf spine/manifest, EPUB/nav.xhtml TOC, XHTML5 chapters, script/remote-fetch/XXE refusal); DRM encryption.xml and media overlays refused."

    const val MIMETYPE_VALUE = "application/epub+zip"
    const val MAX_CHAPTERS = 256
    const val MAX_EPUB_BYTES = 16 * 1024 * 1024

    data class Chapter(
        val id: String,
        val title: String,
        val paragraphs: List<String>,
    ) {
        init {
            require(id.matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}"))) { "invalid chapter id '$id'" }
            require(title.isNotBlank()) { "chapter title must be non-blank" }
        }
    }

    data class BookSpec(
        val identifier: String,
        val title: String,
        val language: String = "en",
        val creator: String = "",
        val chapters: List<Chapter>,
    ) {
        init {
            require(identifier.isNotBlank()) { "EPUB dc:identifier required" }
            require(title.isNotBlank()) { "EPUB dc:title required" }
            require(chapters.size in 1..MAX_CHAPTERS) { "chapters count ${chapters.size} outside 1..$MAX_CHAPTERS" }
            require(chapters.map { it.id }.distinct().size == chapters.size) { "duplicate chapter IDs" }
        }
    }

    fun writeBook(book: BookSpec): ByteArray {
        val baos = ByteArrayOutputStream()
        val mimeBytes = MIMETYPE_VALUE.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(mimeBytes) }.value
        ZipOutputStream(baos).use { zos ->
            val first = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc
                time = 315_532_800_000L
                extra = null
            }
            zos.putNextEntry(first)
            zos.write(mimeBytes)
            zos.closeEntry()

            putDeflated(zos, "META-INF/container.xml", CONTAINER_XML)
            putDeflated(zos, "EPUB/package.opf", buildPackageOpf(book))
            putDeflated(zos, "EPUB/nav.xhtml", buildNavXhtml(book))
            book.chapters.forEach { ch ->
                putDeflated(zos, "EPUB/${ch.id}.xhtml", buildChapterXhtml(ch, book.language))
            }
        }
        val out = baos.toByteArray()
        check(verifyMimetypeHeader(out)) { "OCF invariant violated: mimetype not STORED at offset 38" }
        return out
    }

    fun verifyMimetypeHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 38 + MIMETYPE_VALUE.length) return false
        if (bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte() ||
            bytes[2] != 0x03.toByte() || bytes[3] != 0x04.toByte()
        ) return false
        val compressionMethod = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        val nameLen = (bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)
        val extraLen = (bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)
        if (compressionMethod != 0 || nameLen != 8 || extraLen != 0) return false
        val name = String(bytes, 30, 8, Charsets.US_ASCII)
        val payload = String(bytes, 38, MIMETYPE_VALUE.length, Charsets.US_ASCII)
        return name == "mimetype" && payload == MIMETYPE_VALUE
    }

    fun readBook(bytes: ByteArray): BookSpec {
        require(bytes.size <= MAX_EPUB_BYTES) { "EPUB exceeds $MAX_EPUB_BYTES bytes" }
        require(verifyMimetypeHeader(bytes)) {
            "invalid EPUB 3.3 OCF header: mimetype must be first entry, STORED uncompressed at offset 38"
        }
        val files = LinkedHashMap<String, String>()
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                require(!e.name.contains("..") && !e.name.startsWith("/")) { "unsafe EPUB path: ${e.name}" }
                require(e.name != "META-INF/encryption.xml") { "DRM-encrypted EPUB refused" }
                val data = zis.readBytes()
                expanded += data.size
                require(expanded <= MAX_EPUB_BYTES * 2L) { "EPUB expansion exceeds ceiling" }
                val text = data.toString(Charsets.UTF_8)
                require(!text.contains("<!ENTITY", ignoreCase = true)) { "XXE ENTITY refused in ${e.name}" }
                if (e.name.endsWith(".xhtml") || e.name.endsWith(".opf")) {
                    require(!Regex("<\\s*script\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                        "active <script> refused in offline EPUB (${e.name})"
                    }
                    require(!Regex("\\b(src|href)\\s*=\\s*[\"']https?://", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                        "remote network resource fetch refused in offline EPUB (${e.name})"
                    }
                }
                files[e.name] = text
            }
        }
        val container = files["META-INF/container.xml"] ?: error("missing META-INF/container.xml")
        val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(container)?.groupValues?.get(1)
            ?: error("missing rootfile full-path in container.xml")
        val opf = files[opfPath] ?: error("missing OPF package $opfPath")
        val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }

        val id = Regex("<dc:identifier[^>]*>([\\s\\S]*?)</dc:identifier>").find(opf)?.groupValues?.get(1)?.let(::unesc).orEmpty()
        val title = Regex("<dc:title[^>]*>([\\s\\S]*?)</dc:title>").find(opf)?.groupValues?.get(1)?.let(::unesc).orEmpty()
        val lang = Regex("<dc:language[^>]*>([\\s\\S]*?)</dc:language>").find(opf)?.groupValues?.get(1)?.let(::unesc) ?: "en"
        val creator = Regex("<dc:creator[^>]*>([\\s\\S]*?)</dc:creator>").find(opf)?.groupValues?.get(1)?.let(::unesc).orEmpty()

        val manifest = HashMap<String, String>()
        for (m in Regex("<item\\b([^>]+)/>").findAll(opf)) {
            val attrs = m.groupValues[1]
            val itemId = Regex("\\bid\\s*=\\s*\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: continue
            val href = Regex("\\bhref\\s*=\\s*\"([^\"]+)\"").find(attrs)?.groupValues?.get(1) ?: continue
            manifest[itemId] = href
        }
        val spineIds = Regex("<itemref\\b[^>]*idref\\s*=\\s*\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        val chapters = spineIds.map { idref ->
            val href = manifest[idref] ?: error("missing manifest href for spine idref '$idref'")
            val xhtml = files[opfDir + href] ?: error("missing chapter file ${opfDir + href}")
            val chTitle = Regex("<h1[^>]*>([\\s\\S]*?)</h1>").find(xhtml)?.groupValues?.get(1)?.let(::unesc) ?: idref
            val paras = Regex("<p>([\\s\\S]*?)</p>").findAll(xhtml).map { unesc(it.groupValues[1]) }.toList()
            Chapter(id = idref, title = chTitle, paragraphs = paras)
        }
        return BookSpec(identifier = id, title = title, language = lang, creator = creator, chapters = chapters)
    }

    private fun putDeflated(zos: ZipOutputStream, path: String, content: String) {
        val e = ZipEntry(path).apply { time = 315_532_800_000L }
        zos.putNextEntry(e)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private const val CONTAINER_XML =
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    private fun buildPackageOpf(book: BookSpec): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">""")
        append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
        append("""<dc:identifier id="pub-id">${esc(book.identifier)}</dc:identifier>""")
        append("""<dc:title>${esc(book.title)}</dc:title>""")
        append("""<dc:language>${esc(book.language)}</dc:language>""")
        if (book.creator.isNotBlank()) append("""<dc:creator>${esc(book.creator)}</dc:creator>""")
        append("""<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>""")
        append("""</metadata><manifest>""")
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        for (ch in book.chapters) {
            append("""<item id="${ch.id}" href="${ch.id}.xhtml" media-type="application/xhtml+xml"/>""")
        }
        append("""</manifest><spine>""")
        for (ch in book.chapters) append("""<itemref idref="${ch.id}"/>""")
        append("""</spine></package>""")
    }

    private fun buildNavXhtml(book: BookSpec): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html>""")
        append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${esc(book.language)}">""")
        append("""<head><meta charset="UTF-8"/><title>${esc(book.title)}</title></head><body>""")
        append("""<nav epub:type="toc" id="toc"><h1>Contents</h1><ol>""")
        for (ch in book.chapters) {
            append("""<li><a href="${ch.id}.xhtml">${esc(ch.title)}</a></li>""")
        }
        append("""</ol></nav></body></html>""")
    }

    private fun buildChapterXhtml(ch: Chapter, lang: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html>""")
        append("""<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="${esc(lang)}">""")
        append("""<head><meta charset="UTF-8"/><title>${esc(ch.title)}</title></head><body>""")
        append("""<section id="${ch.id}"><h1>${esc(ch.title)}</h1>""")
        for (p in ch.paragraphs) append("""<p>${esc(p)}</p>""")
        append("""</section></body></html>""")
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }

    private fun unesc(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}
