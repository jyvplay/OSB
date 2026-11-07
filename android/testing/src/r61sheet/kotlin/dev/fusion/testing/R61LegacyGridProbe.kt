// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetTextGrid as Grid
import java.io.File
import java.util.Random

/** Uses only the pre-R61 overloads; compiled separately against old and new production sources. */
object R61LegacyGridProbe {
    @JvmStatic fun main(args: Array<String>) {
        val directory = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val random = Random(61061)
        val tokens = listOf("", "\"\"", "abc", "12.5", "\"42\"", "0012", "=1+1", " ", "α")
        repeat(128) { index ->
            val rows = 1 + random.nextInt(4)
            val columns = 2 + random.nextInt(4)
            val raw = List(rows) { List(columns) { tokens[random.nextInt(tokens.size)] }.joinToString("\t") }.joinToString("\n")
            val before = Sheet.restore(7, mapOf("B2" to Sheet.Cell(Sheet.Kind.TEXT, "old"),
                "D5" to Sheet.Cell(Sheet.Kind.FORMULA, "=1+2"))).draft
            val mode = if (index % 2 == 0) Grid.Mode.TEXT_ONLY else Grid.Mode.DECIMALS
            val plan = if (index % 4 < 2) Grid.prepare(before, "B2", raw, mode)
                else Grid.prepare(before, "B2", raw, mode, true)
            File(directory, "case-${index.toString().padStart(3, '0')}.xlsx").writeBytes(Sheet.xlsx(plan.calculated.draft))
        }
    }
}
