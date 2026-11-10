// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.NumberFormatRender
import java.io.File

/**
 * R75 sheet fraction-format contracts. Host JVM only: this executes the production renderer and
 * writes every observation to `fractions.tsv`. The independent comparison is
 * tools/check_r75_sheet_fractions.py, which re-derives the nearest fraction by brute force and
 * checks the documented Excel examples; neither is Excel or LibreOffice recalculation evidence.
 */
object R75SheetFractionChecks {
    private var assertions = 0

    private fun check(ok: Boolean, label: String) {
        assertions++
        require(ok) { "FAILED: $label" }
    }

    private fun renders(value: Double, format: String, expected: String) {
        val actual = NumberFormatRender.renderNumber(value, format)
        check(actual == expected) { "render($value, \"$format\") = \"$actual\", expected \"$expected\"" }
    }

    private fun refuses(value: Double, format: String) {
        assertions++
        require(runCatching { NumberFormatRender.renderNumber(value, format) }.isFailure) {
            "expected refusal for \"$format\""
        }
    }

    private val values = listOf(
        0.0, 0.1, 0.25, 1.0 / 3.0, 0.5, 2.0 / 3.0, 0.75, 0.9,
        1.0, 1.25, 1.5, 2.0, 2.5, 3.14159265358979, 7.0625, 100.5, 1234.5,
    )
    private val formats = listOf("# ?/?", "# ??/??", "?/?", "??/??", "# ?/16", "?/8", "0 ?/?", "# ???/???")

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.single()).apply { mkdirs() }
        val lines = ArrayList<String>()
        for (value in values) {
            for (format in formats) {
                val rendered = NumberFormatRender.renderNumber(value, format)
                check(rendered.isNotEmpty(), "empty render for $value / $format")
                check(rendered.length <= NumberFormatRender.MAX_OUTPUT_CHARS, "output bound")
                lines.add("$value\t$format\t$rendered")
            }
        }
        File(out, "fractions.tsv").writeText(lines.joinToString("\n", postfix = "\n"))

        // Documented behaviour every spreadsheet user relies on. These are the only hard-coded
        // strings in this file; everything else is checked structurally by the python oracle.
        renders(1.25, "# ?/?", "1 1/4")
        renders(0.5, "# ?/?", "1/2")
        renders(2.5, "?/?", "5/2")
        renders(3.5, "?/?", "7/2")
        renders(1.0, "# ?/?", "1")
        renders(2.0, "# ?/?", "2")
        renders(0.0, "# ?/?", "0")
        renders(0.0, "0 ?/?", "0")
        // `??` pads the numerator to two characters, exactly as Excel aligns the digits.
        renders(0.0625, "# ??/??", " 1/16")
        renders(1.5, "# ?/8", "1 4/8")
        renders(0.75, "# ?/16", "12/16")
        renders(-1.25, "# ?/?", "-1 1/4")
        renders(0.5, "\u0022about\u0022 # ?/?", "about 1/2")
        renders(0.75, "# ?/?%", "75%")
        renders(0.9, "# ?/?", "8/9")
        renders(0.1, "# ?/?", "1/9")

        // Boundary discipline: shapes that are not fractions stay refused rather than guessed.
        refuses(1.5, "# ?/?.")
        refuses(1.5, "?/?E+00")
        refuses(1.5, "#,### ?/?")
        refuses(1.5, "?????/?????")
        check(NumberFormatRender.renderNumber(1.5, "???/???").trim() == "3/  2".trim()) {
            "three-digit placeholders pad on both sides: " + NumberFormatRender.renderNumber(1.5, "???/???")
        }
        refuses(1.5, "# ?/")
        refuses(1.5, "/?")
        refuses(1.5, "# ?/00000")

        // Determinism and monomorphism: the same call twice, and two widths of the same value.
        check(NumberFormatRender.renderNumber(1.25, "# ??/??") ==
            NumberFormatRender.renderNumber(1.25, "# ??/??"), "deterministic")
        check(NumberFormatRender.renderNumber(1.25, "# ?/?") == "1 1/4", "one-digit bound")
        // `??` widens both parts, so Excel-aligned padding shows up as doubled spaces.
        check(NumberFormatRender.renderNumber(1.25, "# ??/??") == "1  1/ 4", "two-digit bound keeps 1/4 with padding")

        println("R75_SHEET_FRACTIONS_OK assertions=$assertions rows=${lines.size} artifacts=${out.absolutePath}")
    }
}
