// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.NumberFormatRender
import java.io.File

/**
 * R76 spreadsheet number/date/text format contracts. Host JVM only.
 *
 * The probe renders every (value, format) pair through the production renderer and writes the
 * observations to `formats.tsv`. The independent comparison is
 * tools/check_r76_sheet_formats.py, which replays the same corpus through the Apache-2.0 `ssf`
 * port of SheetJS (an implementation this repository does not share code with) and adjudicates
 * every difference against a declared divergence table.
 *
 * Nothing here is Excel or LibreOffice recalculation evidence: both would be a stronger verifier
 * and neither exists in this host.
 */
object R76SheetFormatChecks {
    private const val REFUSED = "REFUSED"
    private var assertions = 0

    private inline fun check(ok: Boolean, message: () -> String) {
        assertions++
        require(ok) { "FAILED: " + message() }
    }

    private fun renders(value: Double, format: String, expected: String) {
        val actual = NumberFormatRender.renderNumber(value, format)
        check(actual == expected) { "render($value, \"$format\") = \"$actual\", expected \"$expected\"" }
    }

    private fun refuses(value: Double, format: String) {
        assertions++
        val failure = runCatching { NumberFormatRender.renderNumber(value, format) }.exceptionOrNull()
        require(failure != null) { "expected a refusal for \"$format\"" }
        require(failure is IllegalArgumentException) { "refusal for \"$format\" must be IllegalArgumentException, was $failure" }
    }

    private data class Case(val kind: String, val value: Double, val format: String)

    private fun cases(): List<Case> {
        val out = ArrayList<Case>()
        fun add(kind: String, format: String, vararg values: Double) {
            for (v in values) out.add(Case(kind, v, format))
        }

        // --- numbers: currency, accounting, grouping, scaling, scientific, percent, padding ---
        for (f in listOf("\$#,##0.00", "_(\$* #,##0.00_)", "#,##0.00_);(#,##0.00)", "#,##0.00_);[Red](#,##0.00)")) {
            add("number", f, 1234.567, -1234.567, 0.0)
        }
        for (f in listOf("#,##0.0,,", "#,##0.000,,\"M\"", "#,##0,", "#,##0")) {
            add("number", f, 12200000.0, 1000000.0, 1234.0)
        }
        for (f in listOf("0.00E+00", "0.00E-00", "0.0E+0")) {
            add("number", f, 1234.0, 0.000123, 0.0, 123456789.0)
        }
        for (f in listOf("0.0%", "#,###.00%", "0%")) add("number", f, 0.1234, 0.5, -0.25)
        for (f in listOf("000", "???", "0.000", "#.##", "#,##0.00")) add("number", f, 1.0, 0.5, 99.999, 123456789.0)
        for (f in listOf(
            "\"Total: \"0.00", "0\" units\"", "\"X\"0\"Y\"",
            "[Blue]#,##0.00", "[\$€-407]#,##0.00", "[\$-409]0.00", "[\$-409]#,##0",
        )) {
            add("number", f, 5.0, 1234.5678, 0.0)
        }
        for (f in listOf("@", "\"pre \"@", "@\" post\"")) add("number", f, 1.0, 2.5, 0.0)
        add("number", "0.00", 2.675, 99.999, -0.005)
        add("number", "0.00;0.00", -1.5)
        add("number", "General", 3.14159, 0.0, 1e15, 1.234e-5)

        // --- dates: the 1900 system including day zero and the fictitious leap day ---
        for (v in listOf(0.0, 1.0, 59.0, 60.0, 61.0, 45000.0, 2958465.0)) {
            for (f in listOf("yyyy-mm-dd", "m/d/yy", "dddd", "mmmmm", "d-mmm-yy")) add("date", f, v)
        }
        add("date", "dddd, mmmm d, yyyy", 45000.0, 44927.0)
        add("date", "yyyy-mm-dd", 45000.999999, 45000.5, 45000.0416666667)
        add("date", "[\$-409]d-mmm-yy", 1.0, 45000.0)
        add("date", "mmm-yy", 45000.0)
        add("date", "mmmm", 45000.0, 1.0, 60.0)

        // --- times: elapsed brackets, 12/24 hour, sub-second precision ---
        for (v in listOf(0.0, 0.25, 0.5, 0.75, 0.000005787, 0.999999, 45000.5, 45000.999, 1.5)) {
            for (f in listOf(
                "hh:mm", "h:mm AM/PM", "h:mm:ss", "mm:ss", "[h]:mm:ss", "[mm]:ss", "[hh]:mm", "A/P",
                "hh:mm:ss.00", "mm:ss.000",
            )) {
                add("time", f, v)
            }
        }

        // --- refusals: conditions, ambiguous numeric colons, unsupported elapsed tokens ---
        for (f in listOf("[>100]General", "[>=1000]#,##0;[<1000]0", "00:00", "0:00", "ss.0000", "[hx]:mm")) {
            add("refusal", f, 0.5)
        }
        for (v in listOf(-1.0, 2958466.0)) add("refusal", "yyyy-mm-dd", v)
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val rows = ArrayList<String>()
        for (case in cases()) {
            val rendered = runCatching { NumberFormatRender.renderNumber(case.value, case.format) }
                .getOrElse { REFUSED }
            check(rendered.length <= NumberFormatRender.MAX_OUTPUT_CHARS) { "output bound for ${case.format}" }
            if (rendered != REFUSED) {
                check(rendered.isNotEmpty()) { "empty render for ${case.value} / ${case.format}" }
                val again = NumberFormatRender.renderNumber(case.value, case.format)
                check(again == rendered) { "non-deterministic render for ${case.value} / ${case.format}" }
            }
            rows.add("${case.kind}\t${case.value}\t${case.format}\t$rendered")
        }
        // Written as UTF-8 on purpose: currency symbols and the euro sign must survive the probe.
        File(out, "formats.tsv").writeText(rows.joinToString("\n", postfix = "\n"))

        // ---- documented expectations, each verified against the independent ssf oracle --------
        // Microsoft, "Review guidelines for customizing a number format": a comma after a digit
        // placeholder scales by 1,000, and `#.0,,` of 12,200,000 displays 12.2.
        renders(12200000.0, "#,##0.0,,", "12.2")
        renders(12200000.0, "#,##0.000,,\"M\"", "12.200M")
        renders(1234.567, "\$#,##0.00", "\$1,234.57")
        renders(-1234.567, "\$#,##0.00", "-\$1,234.57")
        renders(0.0, "\$#,##0.00", "\$0.00")
        renders(1234.567, "_(\$* #,##0.00_)", " \$1,234.57 ")
        renders(-1234.567, "#,##0.00_);(#,##0.00)", "(1,234.57)")
        renders(2.675, "0.00", "2.68")
        renders(99.999, "0.00", "100.00")
        renders(1.0, "000", "001")
        renders(1.0, "???", "  1")
        renders(1.0, "#.##", "1.")
        renders(1.0, "0.000", "1.000")
        renders(0.1234, "0.0%", "12.3%")
        renders(0.5, "#,###.00%", "50.00%")
        renders(1234.0, "0.00E+00", "1.23E+03")
        renders(0.000123, "0.00E+00", "1.23E-04")
        renders(0.0, "0.00E+00", "0.00E+00")
        renders(5.0, "\"Total: \"0.00", "Total: 5.00")
        renders(5.0, "0\" units\"", "5 units")
        renders(1234.5678, "[Blue]#,##0.00", "1,234.57")
        renders(1234.5678, "[\$€-407]#,##0.00", "€1,234.57")
        renders(5.0, "[\$-409]0.00", "5.00")
        renders(3.14159, "General", "3.14159")
        renders(1e15, "General", "1E+15")
        renders(1.0, "@", "1")

        // Dates: serial 1 is 1900-01-01, serial 60 is Excel's fictitious 1900-02-29, and the
        // weekday clock is Excel's own (serial 1 is a Sunday).
        renders(0.0, "yyyy-mm-dd", "1900-01-00")
        renders(1.0, "yyyy-mm-dd", "1900-01-01")
        renders(59.0, "yyyy-mm-dd", "1900-02-28")
        renders(60.0, "yyyy-mm-dd", "1900-02-29")
        renders(61.0, "yyyy-mm-dd", "1900-03-01")
        renders(2958465.0, "yyyy-mm-dd", "9999-12-31")
        renders(1.0, "dddd", "Sunday")
        renders(61.0, "dddd", "Thursday")
        renders(45000.0, "dddd, mmmm d, yyyy", "Wednesday, March 15, 2023")
        renders(45000.0, "mmmmm", "M")
        renders(1.0, "mmmmm", "J")
        renders(60.0, "mmmmm", "F")
        renders(45000.0, "mmm-yy", "Mar-23")
        renders(1.0, "[\$-409]d-mmm-yy", "1-Jan-00")
        renders(45000.999999, "yyyy-mm-dd", "2023-03-15")

        // Times: `[h]` is elapsed hours, `m` before `ss` is minutes, and the day fraction is
        // rounded at second granularity before coarser units are truncated.
        renders(0.5, "[h]:mm:ss", "12:00:00")
        renders(0.5, "[mm]:ss", "720:00")
        renders(1.5, "[hh]:mm", "36:00")
        renders(0.5, "h:mm AM/PM", "12:00 PM")
        renders(0.0, "h:mm AM/PM", "12:00 AM")
        renders(0.75, "h:mm AM/PM", "6:00 PM")
        renders(0.999999, "hh:mm", "00:00")
        renders(45000.999, "hh:mm", "23:58")
        renders(0.25, "hh:mm", "06:00")
        renders(45000.0, "mm:ss", "00:00")
        renders(45000.999, "h:mm:ss.00", "23:58:33.60")
        renders(0.5, "A/P", "P")
        renders(0.0, "A/P", "A")

        // ---- refusals stay refusals (a silent approximation would be worse than a failure) ----
        refuses(0.5, "[>100]General")
        refuses(0.5, "[>=1000]#,##0;[<1000]0")
        refuses(0.5, "00:00")
        refuses(0.5, "0:00")
        refuses(0.5, "ss.0000")
        refuses(0.5, "[hx]:mm")
        refuses(-1.0, "yyyy-mm-dd")
        refuses(2958466.0, "yyyy-mm-dd")

        // ---- text path still substitutes, and date formats never touch text ----
        check(NumberFormatRender.renderText("hello", "@") == "hello") { "text @ substitution" }
        check(NumberFormatRender.renderText("hello", "\"pre \"@\" post\"") == "pre hello post") { "text literals" }
        check(NumberFormatRender.renderText("hello", "0.00") == "hello") { "numeric section leaves text alone" }

        val refusedRows = rows.count { it.endsWith("\t$REFUSED") }
        println(
            "R76_SHEET_FORMATS_OK assertions=$assertions rows=${rows.size} refused=$refusedRows " +
                "artifacts=${out.absolutePath}",
        )
    }
}
