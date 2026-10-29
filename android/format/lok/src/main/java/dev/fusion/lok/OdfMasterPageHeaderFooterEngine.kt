// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lok

/**
 * Pure-JVM OASIS OpenDocument 1.3 (`ISO/IEC 26300`) Master-Page (`<style:master-page>`)
 * header/footer and odd/even/first-page running-head resolver with dynamic page-field
 * substitution (`<text:page-number/>`, `<text:page-count/>`, `<text:chapter/>`, `<text:date/>`).
 */
object OdfMasterPageHeaderFooterEngine {
    const val SCOPE =
        "OASIS ODF 1.3 style:master-page First / Left (Even) / Right (Odd) running header & footer resolver with dynamic page-number, page-count, chapter, and ISO date field expansion."

    data class MasterPageSpec(
        val name: String,
        val headerOddTemplate: String,
        val headerEvenTemplate: String? = null,
        val headerFirstTemplate: String? = null,
        val footerOddTemplate: String,
        val footerEvenTemplate: String? = null,
        val footerFirstTemplate: String? = null,
    )

    data class ResolvedPageChrome(
        val pageNumber: Int,
        val headerText: String,
        val footerText: String,
    )

    fun resolveDocumentPages(
        spec: MasterPageSpec,
        totalPages: Int,
        chapterByPage: Map<Int, String> = emptyMap(),
        isoDate: String = "2026-10-28",
    ): List<ResolvedPageChrome> {
        require(totalPages >= 1) { "totalPages must be >= 1" }
        return (1..totalPages).map { p ->
            val isFirst = p == 1
            val isEven = p % 2 == 0
            val hTpl = when {
                isFirst && spec.headerFirstTemplate != null -> spec.headerFirstTemplate
                isEven && spec.headerEvenTemplate != null -> spec.headerEvenTemplate
                else -> spec.headerOddTemplate
            }
            val fTpl = when {
                isFirst && spec.footerFirstTemplate != null -> spec.footerFirstTemplate
                isEven && spec.footerEvenTemplate != null -> spec.footerEvenTemplate
                else -> spec.footerOddTemplate
            }
            val ch = chapterByPage[p] ?: ""
            ResolvedPageChrome(
                pageNumber = p,
                headerText = expandFields(hTpl, p, totalPages, ch, isoDate),
                footerText = expandFields(fTpl, p, totalPages, ch, isoDate),
            )
        }
    }

    fun toOdfStylesXml(spec: MasterPageSpec): String = buildString {
        append("<office:master-styles xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" ")
        append("xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" ")
        append("xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\">")
        append("<style:master-page style:name=\"").append(spec.name).append("\">")
        append("<style:header><text:p>").append(spec.headerOddTemplate).append("</text:p></style:header>")
        if (spec.headerEvenTemplate != null) {
            append("<style:header-left><text:p>").append(spec.headerEvenTemplate).append("</text:p></style:header-left>")
        }
        if (spec.headerFirstTemplate != null) {
            append("<style:header-first><text:p>").append(spec.headerFirstTemplate).append("</text:p></style:header-first>")
        }
        append("<style:footer><text:p>").append(spec.footerOddTemplate).append("</text:p></style:footer>")
        if (spec.footerEvenTemplate != null) {
            append("<style:footer-left><text:p>").append(spec.footerEvenTemplate).append("</text:p></style:footer-left>")
        }
        if (spec.footerFirstTemplate != null) {
            append("<style:footer-first><text:p>").append(spec.footerFirstTemplate).append("</text:p></style:footer-first>")
        }
        append("</style:master-page></office:master-styles>")
    }

    private fun expandFields(tpl: String, page: Int, count: Int, chapter: String, isoDate: String): String =
        tpl.replace("<text:page-number/>", page.toString())
            .replace("<text:page-count/>", count.toString())
            .replace("<text:chapter/>", chapter)
            .replace("<text:date/>", isoDate)
}
