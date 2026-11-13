// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.NumberFormatRender
import java.io.File

/**
 * R77 spreadsheet number-format conditions. Host JVM only.
 *
 * A conditional format overrides the positive/negative/zero structure: sections are tested in
 * order and the first satisfied condition wins; a trailing section without a condition is the
 * fallback. Everything about the *rendering* of the chosen section (literals, sign, scaling) is
 * the same code path the unconditional formats use, so this probe asserts the selection rules and
 * the sign consequences, and writes every observation for the independent re-derivation in
 * tools/check_r77_sheet_conditions.py.
 *
 * The sign rule asserted here is the one documented by Microsoft and by two independent Excel
 * references: a section never gains an automatic minus sign, so a negative-valued cell whose
 * section does not contain a `-` literal displays the magnitude (`#,##0;#,##0` hides the sign,
 * and the standard fix is to write `#,##0;-#,##0`). Only a single-section format auto-signs.
 *
 * No spreadsheet application ran for this probe: the values below are derived from the grammar
 * and from public documentation, never observed in Excel.
 */
object R77SheetConditionChecks {
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

        // Two sections, the second conditioned: 100 is not > 100, so it takes the fallback.
        add("number", "[>100]0.00;-0.00", 150.0, 0.5, -5.0, 100.0, 100.0001, 0.0)
        // Three sections with a trailing unconditional fallback.
        add("number", "[>=1000]#,##0;[<0]0.00;0.0", 1234.0, -3.5, 12.0, 999.999, 1000.0, 0.0)
        // Colours travel with the condition and must not reach the text.
        add("number", "[Red][<100]0;[Blue][>=100]0", 50.0, 150.0, -20.0, 99.9, 100.0)
        // Explicit sign literals are the only way a conditional section shows a sign.
        add("number", "[Red][<0]-0.00;[Green][>=0]+0.00", -5.0, 5.0, 0.0, -0.004)
        // Equality and inequality conditions.
        add("number", "[=5]\"five\";0.0", 5.0, 6.0, 4.999, -5.0)
        add("number", "[<>0]\"nonzero\";\"zero\"", 0.0, 1.0, -1.0, 0.0001)
        // Currency annotation outside the condition, scaling inside the template.
        add("number", "[\$\u20ac-407][>1000]#,##0.00;0.00", 1234.5, 999.0, -2.0, 1000.0001)
        add("number", "[>=1000000]#,##0.0,,\"M\";#,##0.0", 2000000.0, 1234.5, 999999.0, 1000000.0)
        add("number", "[<1]0.00%;0.00", 0.5, 2.0, 0.999, 1.0)
        // A three-section format whose zero case is a literal-only fallback.
        add("number", "[>0]#,##0.000;[<0]#,##0.000;\"zero\"", 0.5, -0.5, 0.0, 1234.5678)

        // Controls without any condition: the same corpus must still agree with the reference
        // implementation exactly, so a broken harness cannot hide behind "conditions differ".
        add("number", "#,##0.00", 1234.567, -1234.567, 0.0, 0.005)
        add("number", "0.0%", 0.1234, -0.25, 0.5)

        // Refusals: shapes whose selection is not defined by the documented grammar.
        add("refusal", "[>100]General", 0.5)
        add("refusal", "[>100]0.00;[<0]0.00", 1.0)
        add("refusal", "[>100][<200]0.0;0", 1.0)
        add("refusal", "[>1e3]0.0;0", 1.0)
        add("refusal", "[abc]0.0", 1.0)
        add("refusal", "0.0;[>100]0.0", 1.0)
        add("refusal", "[>100]0.0;[<0]0.0;[=0]0.0;@", 1.0)
        add("refusal", "[>=]0.0;0", 1.0)
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
        File(out, "formats.tsv").writeText(rows.joinToString("\n", postfix = "\n"))

        // ---- section selection ---------------------------------------------------------------
        renders(150.0, "[>100]0.00;-0.00", "150.00")
        renders(100.0, "[>100]0.00;-0.00", "-100.00")
        renders(100.0001, "[>100]0.00;-0.00", "100.00")
        renders(0.5, "[>100]0.00;-0.00", "-0.50")
        renders(-5.0, "[>100]0.00;-0.00", "-5.00")

        renders(1234.0, "[>=1000]#,##0;[<0]0.00;0.0", "1,234")
        renders(1000.0, "[>=1000]#,##0;[<0]0.00;0.0", "1,000")
        renders(999.999, "[>=1000]#,##0;[<0]0.00;0.0", "1000.0")
        renders(-3.5, "[>=1000]#,##0;[<0]0.00;0.0", "3.50")
        renders(12.0, "[>=1000]#,##0;[<0]0.00;0.0", "12.0")
        renders(0.0, "[>=1000]#,##0;[<0]0.00;0.0", "0.0")

        // ---- colours attach to the condition and never reach the text ------------------------
        renders(50.0, "[Red][<100]0;[Blue][>=100]0", "50")
        renders(100.0, "[Red][<100]0;[Blue][>=100]0", "100")
        renders(-20.0, "[Red][<100]0;[Blue][>=100]0", "20")

        // ---- an explicit literal is the only sign a conditional section shows ----------------
        renders(-5.0, "[Red][<0]-0.00;[Green][>=0]+0.00", "-5.00")
        renders(5.0, "[Red][<0]-0.00;[Green][>=0]+0.00", "+5.00")
        renders(0.0, "[Red][<0]-0.00;[Green][>=0]+0.00", "+0.00")
        renders(-0.004, "[Red][<0]-0.00;[Green][>=0]+0.00", "-0.00")

        // ---- equality / inequality ----------------------------------------------------------
        renders(5.0, "[=5]\"five\";0.0", "five")
        renders(4.999, "[=5]\"five\";0.0", "5.0")
        renders(0.0, "[<>0]\"nonzero\";\"zero\"", "zero")
        renders(0.0001, "[<>0]\"nonzero\";\"zero\"", "nonzero")
        renders(-1.0, "[<>0]\"nonzero\";\"zero\"", "nonzero")

        // ---- annotations, scaling and percentages still apply inside the template -----------
        renders(1234.5, "[\$\u20ac-407][>1000]#,##0.00;0.00", "\u20ac1,234.50")
        renders(999.0, "[\$\u20ac-407][>1000]#,##0.00;0.00", "999.00")
        renders(2000000.0, "[>=1000000]#,##0.0,,\"M\";#,##0.0", "2.0M")
        renders(1234.5, "[>=1000000]#,##0.0,,\"M\";#,##0.0", "1,234.5")
        renders(0.5, "[<1]0.00%;0.00", "50.00%")
        renders(2.0, "[<1]0.00%;0.00", "2.00")
        renders(0.0, "[>0]#,##0.000;[<0]#,##0.000;\"zero\"", "zero")
        renders(1234.5678, "[>0]#,##0.000;[<0]#,##0.000;\"zero\"", "1,234.568")
        renders(-0.5, "[>0]#,##0.000;[<0]#,##0.000;\"zero\"", "0.500")

        // ---- one section with a condition is legal; two conditions per section are not ------
        renders(150.0, "[>100]0.00", "150.00")
        refuses(0.5, "[>100]General")
        refuses(1.0, "[>100]0.00;[<0]0.00")
        refuses(1.0, "[>100][<200]0.0;0")
        refuses(1.0, "[>1e3]0.0;0")
        refuses(1.0, "[>=]0.0;0")
        refuses(1.0, "[abc]0.0")
        refuses(1.0, "0.0;[>100]0.0")
        refuses(1.0, "[>100]0.0;[<0]0.0;[=0]0.0;@")

        // ---- the date path obeys the same selection -----------------------------------------
        check(NumberFormatRender.usesDateTokens("[>0]yyyy-mm-dd;@", 1.0)) { "conditional date section" }
        check(!NumberFormatRender.usesDateTokens("[>0]yyyy-mm-dd;@", -1.0)) { "fallback is not a date section" }

        val refusedRows = rows.count { it.endsWith("\t$REFUSED") }
        println(
            "R77_SHEET_CONDITIONS_OK assertions=$assertions rows=${rows.size} refused=$refusedRows " +
                "artifacts=${out.absolutePath}",
        )
    }
}
