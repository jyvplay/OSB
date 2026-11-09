// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.FormulaEval
import java.io.File

/**
 * Evaluates R73 formula-essentials cases through the production engine.
 * Host-only: no Android runtime, workbook file, or UI is involved.
 */
object R73FormulaProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2 && args[0] == "read") { "Expected read <directory>" }
        val directory = File(args[1])
        val cases = File(directory, "cases.tsv")
        require(cases.isFile) { "Missing cases.tsv" }
        val env = environment()
        val report = StringBuilder()
        var count = 0
        for (line in cases.readLines()) {
            if (line.isBlank()) continue
            val fields = line.split('\t')
            require(fields.size == 2) { "Malformed case row: $line" }
            val id = fields[0]
            val formula = fields[1]
            count++
            var scalar = ""
            var gridText = ""
            try {
                val grid = FormulaEval.evaluateFormulaAsGrid(formula, env)
                scalar = if (grid.size == 1 && grid[0].size == 1) encode(grid[0][0])
                         else "MULTI:" + grid.size + "x" + grid[0].size
                gridText = encodeGrid(grid)
            } catch (e: IllegalArgumentException) {
                scalar = "R:" + e.message
                gridText = "R"
            } catch (e: IllegalStateException) {
                scalar = "R:" + e.message
                gridText = "R"
            }
            report.append(id).append('|').append(scalar).append('|').append(gridText).append('\n')
        }
        File(directory, "candidate.txt").writeText(report.toString())
        println("R73 formula probe executed $count actual formulas; host-only")
    }

    private fun encodeGrid(grid: List<List<FormulaEval.CellValue>>): String =
        grid.joinToString(";") { row -> row.joinToString(",") { escape(encode(it)) } }

    private fun escape(text: String): String {
        val out = StringBuilder()
        for (ch in text) {
            if (ch == '\\' || ch == ',' || ch == ';') out.append('\\')
            out.append(ch)
        }
        return out.toString()
    }

    private fun encode(value: FormulaEval.CellValue): String = when (value) {
        FormulaEval.CellValue.Empty -> "BLANK"
        is FormulaEval.CellValue.Num -> "N:" + value.value
        is FormulaEval.CellValue.Text -> "T:" + value.value
        is FormulaEval.CellValue.Bool -> "B:" + value.value
        is FormulaEval.CellValue.Err -> "E:" + value.error.literal
    }

    /** The fixed evaluation environment is duplicated verbatim in the Python oracle. */
    private fun environment(): Map<String, FormulaEval.CellValue> = buildMap {
        val numbers = listOf("A1" to 3.0, "A2" to -2.0, "B1" to 1.0, "B2" to 2.0, "B3" to 2.0,
            "B4" to 3.0, "B5" to 3.0, "B6" to 3.0, "C1" to 10.0, "C2" to 20.0, "C3" to 30.0,
            "C4" to 40.0, "C5" to 50.0, "C6" to 60.0, "E1" to 44927.0, "E2" to 44928.0,
            "E3" to 44957.0, "E4" to 43831.0, "E5" to 44927.0, "E6" to 44562.0,
            "E7" to 44197.0, "E8" to 45292.0, "F1" to 0.75, "F2" to 0.5, "F3" to 0.0000001)
        for ((ref, value) in numbers) put(ref, FormulaEval.CellValue.Num(value))
        put("A3", FormulaEval.CellValue.Text("5"))
        put("A4", FormulaEval.CellValue.Bool(true))
        put("A6", FormulaEval.CellValue.Text("Hello"))
        put("A7", FormulaEval.CellValue.Text("  spaced  "))
        put("A8", FormulaEval.CellValue.Text("1,234.5"))
        put("A9", FormulaEval.CellValue.Text("2023-01-01"))
        put("A10", FormulaEval.CellValue.Text("50%"))
        put("A11", FormulaEval.CellValue.Text("(12)"))
        put("D1", FormulaEval.CellValue.Text("a"))
        put("D2", FormulaEval.CellValue.Text("b"))
        put("D3", FormulaEval.CellValue.Text("b"))
        put("D4", FormulaEval.CellValue.Text("c"))
        put("D5", FormulaEval.CellValue.Text("c"))
        put("G1", FormulaEval.CellValue.Bool(false))
        put("H1", FormulaEval.CellValue.Text("apple"))
        put("H2", FormulaEval.CellValue.Text("Apple pie"))
        put("H3", FormulaEval.CellValue.Text("a*b"))
        put("H4", FormulaEval.CellValue.Text("a?c"))
    }
}
