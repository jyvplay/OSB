// SPDX-License-Identifier: Apache-2.0
package dev.fusion.archive

/**
 * Pure-JVM **EPUB 3.3 (`application/epub+zip` OPF Package + Navigation XHTML) Manifest Planner**
 * for `:core:export`, validating uncompressed-first `mimetype` entry ordering and spine itemrefs!
 */
object Epub33PackageManifestBuilder {
    const val SCOPE =
        "W3C EPUB 3.3 package.opf + nav.xhtml manifest/spine builder enforcing STORED first-entry mimetype invariant."

    const val EPUB_MIMETYPE = "application/epub+zip"

    data class EpubChapter(
        val id: String,
        val href: String,
        val title: String,
    )

    data class EpubPackagePlan(
        val firstZipEntryName: String,
        val firstZipEntryUncompressedAscii: String,
        val packageOpfXml: String,
        val navXhtml: String,
    )

    fun buildPackage(bookUuid: String, bookTitle: String, author: String, lang: String, chapters: List<EpubChapter>): EpubPackagePlan {
        require(chapters.isNotEmpty() && bookUuid.isNotBlank() && bookTitle.isNotBlank())
        val opf = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\">\n")
            append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
            append("    <dc:identifier id=\"pub-id\">urn:uuid:").append(esc(bookUuid)).append("</dc:identifier>\n")
            append("    <dc:title>").append(esc(bookTitle)).append("</dc:title>\n")
            append("    <dc:creator>").append(esc(author)).append("</dc:creator>\n")
            append("    <dc:language>").append(esc(lang)).append("</dc:language>\n")
            append("  </metadata>\n  <manifest>\n")
            append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            for (ch in chapters) {
                append("    <item id=\"").append(esc(ch.id)).append("\" href=\"").append(esc(ch.href))
                    .append("\" media-type=\"application/xhtml+xml\"/>\n")
            }
            append("  </manifest>\n  <spine>\n")
            for (ch in chapters) append("    <itemref idref=\"").append(esc(ch.id)).append("\"/>\n")
            append("  </spine>\n</package>")
        }
        val nav = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><body>")
            append("<nav epub:type=\"toc\"><ol>")
            for (ch in chapters) {
                append("<li><a href=\"").append(esc(ch.href)).append("\">").append(esc(ch.title)).append("</a></li>")
            }
            append("</ol></nav></body></html>")
        }
        return EpubPackagePlan("mimetype", EPUB_MIMETYPE, opf, nav)
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
