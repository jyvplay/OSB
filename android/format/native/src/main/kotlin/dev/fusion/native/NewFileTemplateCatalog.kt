// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

import java.nio.charset.StandardCharsets

/**
 * Clean-room zero-metadata New-File Template Catalog & context-menu initializer (`FM-13`).
 */
object NewFileTemplateCatalog {
    const val SCOPE =
        "Pure-JVM zero-author-metadata starter file template catalog (.md, .txt, .csv, .tsv, .json, .svg, .ics, .vcf) with filename & UTF-8 hygiene (FM-13)."

    data class TemplateSpec(
        val id: String,
        val label: String,
        val defaultExtension: String,
        val mimeType: String,
        val category: String,
    )

    data class MaterializedFile(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
    )

    private val TEMPLATES = listOf(
        TemplateSpec("markdown_note", "Markdown Document", "md", "text/markdown", "Documents"),
        TemplateSpec("plain_text", "Plain Text", "txt", "text/plain", "Documents"),
        TemplateSpec("csv_sheet", "CSV Table", "csv", "text/csv", "Spreadsheets"),
        TemplateSpec("tsv_sheet", "TSV Table", "tsv", "text/tab-separated-values", "Spreadsheets"),
        TemplateSpec("json_config", "JSON Object", "json", "application/json", "Developer"),
        TemplateSpec("svg_canvas", "Vector Drawing (SVG)", "svg", "image/svg+xml", "Graphics"),
        TemplateSpec("ics_event", "Calendar Event (iCalendar)", "ics", "text/calendar", "Productivity"),
        TemplateSpec("vcf_contact", "Contact Card (vCard 4.0)", "vcf", "text/vcard", "Productivity"),
    )

    fun catalog(): List<TemplateSpec> = TEMPLATES

    fun materialize(templateId: String, stemName: String): MaterializedFile {
        val spec = TEMPLATES.firstOrNull { it.id == templateId }
            ?: throw IllegalArgumentException("unknown template '$templateId'")
        val cleanStem = stemName.trim().removeSuffix(".${spec.defaultExtension}")
        require(cleanStem.isNotEmpty() && cleanStem.length <= 120) { "invalid stem '$stemName'" }
        require(cleanStem.none { it in charArrayOf('/', '\\', '\u0000', ':') } && cleanStem != "." && cleanStem != "..") {
            "unsafe filename stem '$stemName'"
        }
        val body = when (spec.id) {
            "markdown_note" -> "# $cleanStem\n\n"
            "plain_text" -> ""
            "csv_sheet" -> "column_a,column_b,column_c\n"
            "tsv_sheet" -> "column_a\tcolumn_b\tcolumn_c\n"
            "json_config" -> "{\n  \"title\": \"${cleanStem.replace("\"", "\\\"")}\"\n}\n"
            "svg_canvas" -> """<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360"></svg>""" + "\n"
            "ics_event" -> "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//FusionShell//Local//EN\r\nBEGIN:VEVENT\r\nSUMMARY:$cleanStem\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
            "vcf_contact" -> "BEGIN:VCARD\r\nVERSION:4.0\r\nFN:$cleanStem\r\nEND:VCARD\r\n"
            else -> error("unreachable")
        }
        return MaterializedFile("$cleanStem.${spec.defaultExtension}", spec.mimeType, body.toByteArray(StandardCharsets.UTF_8))
    }
}
