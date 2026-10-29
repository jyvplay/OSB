// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

import kotlin.math.pow

/**
 * ECMA-376 DrawingML `<a:theme>` (`ppt/theme/theme1.xml`) 12-slot `<a:clrScheme>` + `<a:fontScheme>`
 * emitter with WCAG 2.2 relative-luminance contrast verification between dark (`dk1`) and light (`lt1`)
 * master slide slots.
 */
object PptxMasterThemeColorSchemeWriter {
    const val SCOPE =
        "ECMA-376 DrawingML <a:theme>/<a:clrScheme> 12-slot palette writer with WCAG 2.2 dk1/lt1 contrast verification."

    data class ThemePalette(
        val name: String,
        val dk1Hex: String,
        val lt1Hex: String,
        val dk2Hex: String,
        val lt2Hex: String,
        val accentsHex6: List<String>,
        val hlinkHex: String,
        val folHlinkHex: String,
        val majorFontLatin: String = "Inter",
        val minorFontLatin: String = "Inter",
    )

    data class ThemeXmlResult(val themeXml: String, val dk1Lt1ContrastRatio: Double)

    fun buildThemeXml(p: ThemePalette): ThemeXmlResult {
        require(p.accentsHex6.size == 6) { "ECMA-376 clrScheme requires 6 accent slots (accent1..accent6)" }
        val all = listOf(p.dk1Hex, p.lt1Hex, p.dk2Hex, p.lt2Hex, p.hlinkHex, p.folHlinkHex) + p.accentsHex6
        for (hex in all) require(Regex("^[0-9A-Fa-f]{6}$").matches(hex)) { "Invalid 6-hex RGB '$hex'" }
        val ratio = contrastRatio(p.dk1Hex, p.lt1Hex)
        require(ratio >= 4.5) { "Master slide dk1/lt1 contrast ${"%.2f".format(ratio)}:1 violates WCAG AA 4.5:1" }

        val xml = buildString {
            append("<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"${esc(p.name)}\">")
            append("<a:themeElements>")
            append("<a:clrScheme name=\"${esc(p.name)}\">")
            slot("dk1", p.dk1Hex); slot("lt1", p.lt1Hex); slot("dk2", p.dk2Hex); slot("lt2", p.lt2Hex)
            for ((i, acc) in p.accentsHex6.withIndex()) slot("accent${i + 1}", acc)
            slot("hlink", p.hlinkHex); slot("folHlink", p.folHlinkHex)
            append("</a:clrScheme>")
            append("<a:fontScheme name=\"${esc(p.name)}Fonts\">")
            append("<a:majorFont><a:latin typeface=\"${esc(p.majorFontLatin)}\"/></a:majorFont>")
            append("<a:minorFont><a:latin typeface=\"${esc(p.minorFontLatin)}\"/></a:minorFont>")
            append("</a:fontScheme>")
            append("</a:themeElements></a:theme>")
        }
        return ThemeXmlResult(xml, ratio)
    }

    private fun StringBuilder.slot(tag: String, hex: String) {
        append("<a:$tag><a:srgbClr val=\"${hex.uppercase()}\"/></a:$tag>")
    }

    fun contrastRatio(hexA: String, hexB: String): Double {
        val l1 = relLuminance(hexA)
        val l2 = relLuminance(hexB)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun relLuminance(hex: String): Double {
        fun ch(off: Int): Double {
            val s = hex.substring(off, off + 2).toInt(16) / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(0) + 0.7152 * ch(2) + 0.0722 * ch(4)
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
