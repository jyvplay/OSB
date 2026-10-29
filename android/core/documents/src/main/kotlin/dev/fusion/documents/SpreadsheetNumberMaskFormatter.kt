package dev.fusion.documents

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Clean-room ECMA-376 §18.8.31 4-section (`positive;negative;zero;text`) Spreadsheet Custom
 * Number Format Mask Compiler & Formatter (Apache-2.0).
 *
 * Supports currency/accounting (`$#,##0.00;($#,##0.00);"-"`), percentages (`0.00%`),
 * scientific (`0.00E+00`), conditional color tags (`[Red]`, `[Green]`, `[Blue]`),
 * thousands scaling (`#,##0,"K"`), fractions (`# ?/?`), and Excel 1900 serial dates (`yyyy-mm-dd`).
 */
object SpreadsheetNumberMaskFormatter {
    const val SCOPE =
        "ECMA-376 4-section (pos;neg;zero;text) spreadsheet custom number mask formatter with color tags, scaling, scientific, fraction & date masks"

    data class FormattedMaskCell(
        val text: String,
        val colorTag: String? = null,
        val sectionUsed: Int,
    )

    private val COLOR_TAG_RE = Regex("^\\[(Red|Green|Blue|Yellow|Cyan|Magenta|Black|White)\\]", RegexOption.IGNORE_CASE)

    fun formatNumber(value: Double, mask: String): FormattedMaskCell {
        val sections = splitUnquotedSections(mask)
        require(sections.size in 1..4) { "ECMA-376 number format allows at most 4 sections" }
        val (secIdx, rawSec, autoMinus) = when {
            value > 0.0 -> Triple(0, sections[0], false)
            value < 0.0 -> if (sections.size >= 2) Triple(1, sections[1], false) else Triple(0, sections[0], true)
            else -> if (sections.size >= 3) Triple(2, sections[2], false) else Triple(0, sections[0], false)
        }
        val (color, cleaned) = stripLeadingColorTag(rawSec)
        val rendered = renderNumericSection(abs(value), cleaned)
        return FormattedMaskCell(
            text = if (autoMinus) "-$rendered" else rendered,
            colorTag = color,
            sectionUsed = secIdx,
        )
    }

    fun formatText(text: String, mask: String): FormattedMaskCell {
        val sections = splitUnquotedSections(mask)
        val rawSec = if (sections.size >= 4) sections[3] else "@"
        val (color, cleaned) = stripLeadingColorTag(rawSec)
        val sb = StringBuilder()
        var i = 0
        while (i < cleaned.length) {
            when (val ch = cleaned[i]) {
                '"' -> {
                    i++
                    while (i < cleaned.length && cleaned[i] != '"') { sb.append(cleaned[i]); i++ }
                    i++
                }
                '@' -> { sb.append(text); i++ }
                else -> { sb.append(ch); i++ }
            }
        }
        return FormattedMaskCell(sb.toString(), color, if (sections.size >= 4) 3 else 0)
    }

    private fun renderNumericSection(absVal: Double, raw: String): String {
        // Date mask detection (unquoted yyyy / mm / dd)
        val unquoted = stripQuotedLiterals(raw).lowercase(Locale.ROOT)
        if (unquoted.contains("yyyy") && unquoted.contains("dd")) {
            val serial = floor(absVal).toLong()
            val d = LocalDate.of(1899, 12, 30).plusDays(serial)
            return raw
                .replace("yyyy", "%04d".format(d.year), ignoreCase = true)
                .replace("mm", "%02d".format(d.monthValue), ignoreCase = true)
                .replace("dd", "%02d".format(d.dayOfMonth), ignoreCase = true)
                .replace("\"", "")
        }
        // Scientific mask: 0.00E+00
        val sciMatch = Regex("^(0+)(\\.0+)?E([+-])(0+)$", RegexOption.IGNORE_CASE).matchEntire(unquoted.trim())
        if (sciMatch != null) {
            val decs = sciMatch.groupValues[2].removePrefix(".").length
            val expWidth = sciMatch.groupValues[4].length
            if (absVal == 0.0) {
                val frac = if (decs > 0) "." + "0".repeat(decs) else ""
                return "0${frac}E+${"0".repeat(expWidth)}"
            }
            val exp = floor(log10(absVal)).toInt()
            val mant = absVal / 10.0.pow(exp.toDouble())
            val mantStr = BigDecimal.valueOf(mant).setScale(decs, RoundingMode.HALF_UP).toPlainString()
            val sign = if (exp >= 0) "+" else "-"
            return "${mantStr}E$sign${abs(exp).toString().padStart(expWidth, '0')}"
        }
        // Simple fraction mask: # ?/?
        if (unquoted.trim() == "# ?/?") {
            val whole = floor(absVal).toLong()
            val rem = absVal - whole
            var bestN = 0; var bestD = 1; var bestErr = Double.MAX_VALUE
            for (d in 1..9) {
                val n = (rem * d).roundToLong().toInt()
                val err = abs(rem - n.toDouble() / d)
                if (err < bestErr - 1e-12) { bestErr = err; bestN = n; bestD = d }
            }
            if (bestN == bestD) return (whole + 1).toString()
            if (bestN == 0) return whole.toString()
            return if (whole == 0L) "$bestN/$bestD" else "$whole $bestN/$bestD"
        }

        // General numeric placeholder mask with prefix/suffix literals, %, thousands grouping, and trailing comma scaling
        var coreStart = -1; var coreEnd = -1
        var inQ = false
        for (i in raw.indices) {
            val c = raw[i]
            if (c == '"') { inQ = !inQ; continue }
            if (!inQ && (c == '0' || c == '#' || c == '.' || c == ',')) {
                if (coreStart == -1) coreStart = i
                coreEnd = i + 1
            }
        }
        if (coreStart == -1) return raw.replace("\"", "")

        val prefix = unquoteLiterals(raw.substring(0, coreStart))
        val suffixRaw = raw.substring(coreEnd)
        val hasPercent = suffixRaw.contains('%') || prefix.contains('%')
        val suffix = unquoteLiterals(suffixRaw)
        var core = raw.substring(coreStart, coreEnd)

        var scaleThousands = 0
        while (core.endsWith(",")) {
            scaleThousands++
            core = core.dropLast(1)
        }
        val useGrouping = core.contains(',')
        val cleanCore = core.replace(",", "")
        val dot = cleanCore.indexOf('.')
        val intMask = if (dot >= 0) cleanCore.substring(0, dot) else cleanCore
        val fracMask = if (dot >= 0) cleanCore.substring(dot + 1) else ""

        var scaled = absVal * (if (hasPercent) 100.0 else 1.0) / 1000.0.pow(scaleThousands.toDouble())
        val minFrac = fracMask.count { it == '0' }
        val maxFrac = fracMask.length
        val bd = BigDecimal.valueOf(scaled).setScale(maxFrac, RoundingMode.HALF_UP)
        val parts = bd.toPlainString().split('.')
        val minIntDigits = intMask.count { it == '0' }
        val rawInt = parts[0].trimStart('0').padStart(minIntDigits, '0')
        val groupedInt = if (useGrouping && rawInt.length > 3) {
            rawInt.reversed().chunked(3).joinToString(",").reversed()
        } else {
            rawInt
        }
        var fracOut = if (parts.size == 2) parts[1] else ""
        while (fracOut.length > minFrac && fracOut.endsWith('0')) fracOut = fracOut.dropLast(1)
        val numStr = if (fracOut.isNotEmpty()) "$groupedInt.$fracOut" else groupedInt
        return prefix + numStr + suffix
    }

    private fun stripLeadingColorTag(sec: String): Pair<String?, String> {
        val m = COLOR_TAG_RE.find(sec.trimStart()) ?: return null to sec
        val rest = sec.trimStart().substring(m.range.last + 1)
        return m.groupValues[1].uppercase(Locale.ROOT) to rest
    }

    private fun splitUnquotedSections(mask: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQ = false
        for (c in mask) {
            if (c == '"') { inQ = !inQ; cur.append(c) }
            else if (c == ';' && !inQ) { out.add(cur.toString()); cur.setLength(0) }
            else cur.append(c)
        }
        out.add(cur.toString())
        return out
    }

    private fun stripQuotedLiterals(s: String): String {
        val sb = StringBuilder(); var inQ = false
        for (c in s) { if (c == '"') inQ = !inQ else if (!inQ) sb.append(c) }
        return sb.toString()
    }

    private fun unquoteLiterals(s: String): String = s.replace("\"", "").replace("_)", " ").replace("_(", " ")
}
