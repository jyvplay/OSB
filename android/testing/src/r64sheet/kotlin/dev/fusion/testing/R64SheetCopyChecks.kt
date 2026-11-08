// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetRangeCopy as Copy
import java.io.File

/** Actual production transactions and XLSX output. External comparisons are separate. */
object R64SheetCopyChecks {
    private var checks = 0
    private fun ok(value: Boolean, why: String) { checks++; check(value) { why } }
    private fun refused(why: String, action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        ok(error is IllegalArgumentException, "$why: expected atomic refusal, got $error")
    }
    private fun text(value: String) = Sheet.Cell(Sheet.Kind.TEXT, value)
    private fun number(value: String) = Sheet.Cell(Sheet.Kind.NUMBER, value)
    private fun bool(value: String) = Sheet.Cell(Sheet.Kind.BOOLEAN, value)
    private fun formula(value: String) = Sheet.Cell(Sheet.Kind.FORMULA, value)
    private fun draft(vararg cells: Pair<String, Sheet.Cell>) = Sheet.restore(9, linkedMapOf(*cells)).draft

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val before = draft("A1" to text("left"), "B1" to text("middle"), "C1" to text("right"))
        val overlap = Copy.prepare(before, "B1", "A1:C1", protectExisting = false)
        ok(overlap.calculated.draft.cells["B1"] == text("left") &&
            overlap.calculated.draft.cells["C1"] == text("middle") &&
            overlap.calculated.draft.cells["D1"] == text("right"), "overlap reads the original snapshot")
        ok(before.cells["B1"] == text("middle") && overlap.changed == 3, "original unchanged and accounting")
        ok(overlap.isCurrent(before, "B1") && !overlap.isCurrent(before, "A1"), "selection binding")
        ok(!overlap.isCurrent(Sheet.restore(before.revision, before.cells).draft, "B1"), "equal replacement is stale")
        refused("protected overlap") { Copy.prepare(before, "B1", "A1:C1") }
        ok(before.cells["D1"] == null, "conflict leaves draft untouched")

        val missing = draft("A1" to text("x"), "B1" to text(""), "C2" to text("keep"))
        val skip = Copy.prepare(missing, "A2", "A1:C1", protectExisting = false, skipMissing = true)
        ok(skip.skipped == 1 && skip.calculated.draft.cells["C2"] == text("keep"), "missing source skipped")
        ok(skip.calculated.draft.cells["B2"] == text(""), "stored empty text stays populated")
        val clear = Copy.prepare(missing, "A2", "A1:C1", protectExisting = false)
        ok(clear.cleared == 1 && clear.calculated.draft.cells["C2"] == null, "missing source clears")
        refused("protected clearing") { Copy.prepare(missing, "A2", "A1:C1") }
        val self = Copy.prepare(before, "A1", "C1:A1")
        ok(self.source == "A1:C1" && self.destination == "A1:C1" && self.changed == 0 && self.calculated.draft === before,
            "reversed self-copy is an identity")

        val f = draft("B2" to formula("=A1+\$A\$1+A\$2+\$A2"))
        val moved = Copy.prepare(f, "C3", "B2")
        ok(moved.calculated.draft.cells["C3"] == formula("=B2+\$A\$1+B\$2+\$A3"), "mixed anchors")
        ok(moved.formulasWritten == 1 && moved.formulasRemoved == 0, "formula accounting")
        val dependent = draft("A1" to number("2"), "B1" to number("4"), "B2" to formula("=A1+1"))
        val copied = Copy.prepare(dependent, "C2", "B2")
        ok(copied.calculated.values["C2"] == dev.fusion.ooxml.FormulaEval.CellValue.Num(5.0), "copied formula recalculates against moved reference")
        val edited = Sheet.replace(copied.calculated.draft, "B1", number("10"))
        ok(edited.values["C2"] == dev.fusion.ooxml.FormulaEval.CellValue.Num(11.0) &&
            copied.calculated.values["C2"] == dev.fusion.ooxml.FormulaEval.CellValue.Num(5.0) &&
            !copied.isCurrent(edited.draft, "C2"), "dependent recalc retains old snapshot and rejects stale preview")
        refused("out-of-grid relative reference") { Copy.prepare(f, "A1", "B2") }
        refused("destination outside grid") { Copy.prepare(f, "Z100", "A1:B2", false) }
        refused("more than 256 source positions") { Copy.prepare(f, "A1", "A1:K24", false) }
        refused("bad source") { Copy.prepare(f, "A1", "A1:B2:C3", false) }
        val cyclic = draft("B2" to formula("=\$A\$1"))
        refused("introduced cycle") { Copy.prepare(cyclic, "A1", "B2", false) }
        val max = Sheet.restore(Long.MAX_VALUE, mapOf("A1" to text("a"))).draft
        ok(Copy.prepare(max, "A1", "A1").calculated.draft === max, "maximum revision no-op")
        refused("revision exhausted") { Copy.prepare(max, "B1", "A1") }
        val many = linkedMapOf<String, Sheet.Cell>()
        for (row in 1..64) many["A$row"] = formula("=1")
        val sixtyFour = Sheet.restore(10, many).draft
        refused("formula cap") { Copy.prepare(sixtyFour, "B1", "A1", false) }
        ok(sixtyFour.cells["B1"] == null, "formula cap is atomic")

        val formulas = listOf(
            "=A1+\$B\$2+C\$3+\$D4",
            "=SUM(A1:B2)+1E2",
            "=IF(A1>0,\"A1\",B2)",
            "=COUNT(\$A1:B\$2)+\$C\$3",
            "=IFERROR(1/(A1-1),\"C3\")",
            "=LEN(\"A1\"\"B2\")+1E2",
            "=A1+\$B\$2+C\$3+\$D4+2.5e-1",
            "=IF(A1=B1,TRUE,FALSE)",
        )
        val destinations = listOf("M50", "N50", "M51", "N51", "O52", "P50", "L49", "Y100")
        val rows = mutableListOf("id\tformula\tdestination\tprotect\tskip\treversed\tstatus\tinserted\treplaced\tcleared\tunchanged\tskipped\tremoved\twritten")
        var id = 0; var accepted = 0; var rejected = 0
        for ((formulaIndex, expression) in formulas.withIndex()) for (destination in destinations)
            for (protect in listOf(false, true)) for (skipMissing in listOf(false, true)) for (reversed in listOf(false, true)) {
                val initial = linkedMapOf("A1" to number("1"), "B2" to number("2"), "C3" to number("3"), "D4" to number("4"),
                    "M50" to formula(expression), "N50" to text("alpha, \"β\""), "M51" to bool("TRUE"),
                    "O52" to text("occupied"), "Z100" to text("outside"))
                val source = Sheet.restore(7, initial).draft
                val sourceRange = if (reversed) "N51:M50" else "M50:N51"
                val name = (id++).toString().padStart(3, '0')
                val result = runCatching { Copy.prepare(source, destination, sourceRange, protect, skipMissing) }
                if (result.isSuccess) {
                    accepted++
                    val p = result.getOrThrow()
                    ok(p.positions == 4 && p.inserted + p.replaced + p.cleared + p.unchanged + p.skipped == 4, "count conservation $name")
                    ok(p.isCurrent(source, destination) && source.cells == initial, "snapshot integrity $name")
                    ok(p.calculated.draft.revision == if (p.changed == 0) 7L else 8L, "one revision $name")
                    File(folder, "result-$name.xlsx").writeBytes(Sheet.xlsx(p.calculated.draft))
                    rows.add("$name\t$formulaIndex\t$destination\t$protect\t$skipMissing\t$reversed\taccepted\t${p.inserted}\t${p.replaced}\t${p.cleared}\t${p.unchanged}\t${p.skipped}\t${p.formulasRemoved}\t${p.formulasWritten}")
                } else {
                    rejected++
                    ok(result.exceptionOrNull() is IllegalArgumentException && source.cells == initial, "refusal is atomic $name")
                    rows.add("$name\t$formulaIndex\t$destination\t$protect\t$skipMissing\t$reversed\trefused\t\t\t\t\t\t\t")
                }
            }
        ok(id == 512 && accepted > 0 && rejected > 0, "positive and negative corpus")
        File(folder, "cases.tsv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R64 sheet range copy: $checks assertions; $id cases ($accepted accepted/$rejected refused); independent reader pending")
    }
}
