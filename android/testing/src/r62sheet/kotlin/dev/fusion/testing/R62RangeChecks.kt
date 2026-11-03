// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetRangeEdit as Range
import java.io.File
import java.util.Random

/** Executable contracts over production code; independent XLSX checks are separate. */
object R62RangeChecks {
    private var assertions = 0
    private fun checkThat(value: Boolean, why: String) { assertions++; check(value) { why } }
    private fun refuses(why: String, f: () -> Unit): Throwable {
        val error = runCatching(f).exceptionOrNull()
        checkThat(error is IllegalArgumentException, "Expected refusal: $why")
        return requireNotNull(error)
    }
    private fun text(s: String) = Sheet.Cell(Sheet.Kind.TEXT, s)
    private fun fill(d: Sheet.Draft, selected: String, target: String, protected: Boolean = true) =
        Range.prepare(d, selected, target, Range.Operation.FILL_LITERAL, protected)
    private fun clear(d: Sheet.Draft, selected: String, target: String) =
        Range.prepare(d, selected, target, Range.Operation.CLEAR_CONTENTS)

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val initial = linkedMapOf("A1" to Sheet.Cell(Sheet.Kind.NUMBER, "12.5"), "B1" to text("12.5"),
            "B2" to Sheet.Cell(Sheet.Kind.FORMULA, "=1+2"), "C3" to Sheet.Cell(Sheet.Kind.BOOLEAN, "TRUE"),
            "D4" to text("=1+1"), "E1" to text("outside"))
        val before = Sheet.restore(7, initial).draft
        val cleared = clear(before, "A1", " b2 : A1 ")
        checkThat(cleared.range == "A1:B2" && cleared.positions == 4 && cleared.cleared == 3 && cleared.unchanged == 1, "normalized clear counts")
        checkThat(cleared.formulasRemoved == 1 && cleared.calculated.draft.cells["B2"] == null && cleared.calculated.draft.cells["E1"] == text("outside"), "clear formula and preserve outside")
        checkThat(cleared.isCurrent(before, "A1") && !cleared.isCurrent(before, "B1"), "selection bound")
        val sameCopy = Sheet.restore(7, initial).draft
        checkThat(!cleared.isCurrent(sameCopy, "A1"), "equal but distinct snapshot is stale")
        val noOp = clear(before, "A1", "Z100")
        checkThat(noOp.changed == 0 && noOp.calculated.draft === before && noOp.unchanged == 1, "no-op identity")
        val safe = fill(before, "A1", "A1:A2")
        checkThat(safe.inserted == 1 && safe.unchanged == 1 && safe.calculated.draft.cells["A2"] == initial["A1"], "number fill preserves type and source")
        val late = refuses("late conflicting value") { fill(before, "A1", "A1:B2") }
        checkThat(late.message?.contains("B1") == true && before.cells == initial, "late conflict leaves source unchanged")
        val override = fill(before, "A1", "A1:B2", protected = false)
        checkThat(override.inserted == 1 && override.replaced == 2 && override.unchanged == 1 && override.formulasRemoved == 1, "explicit overwrite counts")
        val literal = fill(before, "D4", "D5")
        checkThat(literal.calculated.draft.cells["D5"] == text("=1+1"), "formula-like text stays literal")
        val bool = fill(before, "C3", "C4")
        checkThat(bool.calculated.draft.cells["C4"] == Sheet.Cell(Sheet.Kind.BOOLEAN, "TRUE"), "boolean type")
        refuses("formula source") { fill(before, "B2", "A3") }
        refuses("missing source") { fill(before, "F1", "A3") }
        val emptySource = Sheet.restore(1, mapOf("A1" to text(""))).draft
        refuses("populated empty text is ambiguous as fill") { fill(emptySource, "A1", "A2") }
        for (bad in listOf("", ":A1", "A1:", "A1:B2:C3", "A0", "A01", "AA1", "A101", "Z100:A101", "A١", "A1\nB2", "$" + "A1", "A1!B2", " ".repeat(33))) {
            refuses("invalid range $bad") { clear(before, "A1", bad) }
        }
        checkThat(clear(before, "A1", "A1:P16").positions == 256, "exact area")
        refuses("over area") { clear(before, "A1", "A1:Q16") }
        checkThat(clear(before, "A1", "Z100").range == "Z100", "last cell")
        val exhausted = Sheet.restore(Long.MAX_VALUE, initial).draft
        checkThat(clear(exhausted, "A1", "Z100").calculated.draft === exhausted, "revision max permits no-op")
        refuses("revision max rejects changes") { clear(exhausted, "A1", "A1") }
        val full = LinkedHashMap<String, Sheet.Cell>()
        for (row in 1..16) for (col in 'A'..'P') full["$col$row"] = text("v")
        val maxCells = Sheet.restore(0, full).draft
        refuses("sheet populated-cell cap") { fill(maxCells, "A1", "Q1") }
        checkThat(maxCells.cells == full, "capacity refusal did not mutate map")
        val large = LinkedHashMap<String, Sheet.Cell>()
        for (row in 1..64) large["A$row"] = text("x".repeat(4096))
        val maxBytes = Sheet.restore(0, large).draft
        refuses("sheet byte cap") { fill(maxBytes, "A1", "B1") }
        checkThat(maxBytes.cells == large, "byte refusal did not mutate map")

        val random = Random(62062)
        val sources = listOf("A1", "B1", "B2", "C3", "D4", "F1")
        val rows = mutableListOf("id,selected,range,operation,protect,status,inserted,replaced,cleared,unchanged,formulas")
        var accepted = 0; var rejected = 0; var id = 0
        for (operation in Range.Operation.entries) for (protect in listOf(false, true)) repeat(128) {
            val selected = sources[random.nextInt(sources.size)]
            val colA = 'A' + random.nextInt(5); val colB = 'A' + random.nextInt(5)
            val rowA = 1 + random.nextInt(5); val rowB = 1 + random.nextInt(5)
            val input = "$colA$rowA:$colB$rowB"
            val name = id++.toString().padStart(3, '0')
            val outcome = runCatching { Range.prepare(before, selected, input, operation, protect) }
            if (outcome.isSuccess) {
                val plan = outcome.getOrThrow(); accepted++
                checkThat(plan.inserted + plan.replaced + plan.cleared + plan.unchanged == plan.positions, "position accounting $name")
                checkThat(plan.calculated.draft.revision == if (plan.changed == 0) 7L else 8L, "one revision or no-op $name")
                checkThat(plan.isCurrent(before, selected) && before.cells == initial, "original and source binding $name")
                File(folder, "result-$name.xlsx").writeBytes(Sheet.xlsx(plan.calculated.draft))
                rows.add("$name,$selected,$input,$operation,$protect,accepted,${plan.inserted},${plan.replaced},${plan.cleared},${plan.unchanged},${plan.formulasRemoved}")
            } else {
                rejected++
                checkThat(outcome.exceptionOrNull() is IllegalArgumentException && before.cells == initial, "refusal/source integrity $name")
                rows.add("$name,$selected,$input,$operation,$protect,refused,,,,,")
            }
        }
        checkThat(id == 512 && accepted > 0 && rejected > 0, "positive and negative corpus")
        File(folder, "cases.csv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R62 range edits: $assertions assertions; $id cases ($accepted accepted/$rejected refused), independent reader pending")
    }
}
