// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetTextGrid as Grid
import java.io.File
import java.util.Random

/** Real production grid transformation; independent Python and Android checks are separate. */
object R61SheetPolicyChecks {
    private var assertions = 0
    private fun verify(value: Boolean, message: String) { assertions++; check(value) { message } }
    private fun failure(message: String, block: () -> Unit): Throwable {
        val ex = runCatching(block).exceptionOrNull()
        verify(ex is IllegalArgumentException, "Expected refusal: $message")
        return ex!!
    }
    private fun text(value: String) = Sheet.Cell(Sheet.Kind.TEXT, value)
    private fun options(draft: Sheet.Draft, raw: String, skip: Boolean, protect: Boolean, transpose: Boolean = false) =
        Grid.prepare(draft, "A1", raw, Grid.Mode.TEXT_ONLY, transpose, skip, protect)

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val initial = linkedMapOf("A1" to text("KEEP"), "B1" to Sheet.Cell(Sheet.Kind.NUMBER, "12.5"),
            "B2" to Sheet.Cell(Sheet.Kind.FORMULA, "=1+2"), "C3" to text("touch"), "E1" to text("outside"))
        val before = Sheet.restore(7, initial).draft
        val skip = options(before, "\tnew\n\"\"\t ", skip = true, protect = false)
        verify(skip.skipBlanks && !skip.protectExisting && skip.skippedFields == 2 && skip.emptyFields == 2, "both kinds of blank skipped")
        verify(skip.calculated.draft.cells["A1"] == text("KEEP") && skip.calculated.draft.cells["A2"] == null, "skips preserve old and empty")
        verify(skip.calculated.draft.cells["B1"] == text("new") && skip.calculated.draft.cells["B2"] == text(" "), "spaces are not blank")
        verify(skip.inserted == 0 && skip.replaced == 2 && skip.cleared == 0 && skip.unchanged == 0, "skip counts are separate")
        val allSkipped = options(before, "\t", skip = true, protect = true)
        verify(allSkipped.changed == 0 && allSkipped.skippedFields == 2 && allSkipped.calculated.draft === before, "all-skipped identity")
        val identical = options(before, "KEEP", skip = false, protect = true)
        verify(identical.changed == 0 && identical.unchanged == 1 && identical.calculated.draft === before, "identical populated value allowed")
        val changed = failure("protected replacement") { options(before, "changed", skip = false, protect = true) }
        verify(changed.message == "Protected existing cell A1 would change; no grid applied", "explicit conflict")
        failure("protected blank clears") { options(before, "\tnew", skip = false, protect = true) }
        failure("same text but different cell kind") { options(before, "KEEP\t12.5", skip = false, protect = true) }
        val equivalent = Grid.prepare(before, "A1", "KEEP\t12.5", Grid.Mode.DECIMALS, false, false, true)
        verify(equivalent.changed == 0 && equivalent.unchanged == 2, "identical numeric kind and text allowed")
        val combo = options(before, "\tnew", skip = true, protect = true, transpose = true)
        verify(combo.skippedFields == 1 && combo.inserted == 1 && combo.calculated.draft.cells["A2"] == text("new"), "transpose plus both policies")
        verify(combo.calculated.draft.cells["A1"] == text("KEEP") && combo.isCurrent(before, "A1") && !combo.isCurrent(before, "B1"), "anchor and untouched source")
        val default = Grid.prepare(before, "A1", "KEEP\t12.5")
        val explicit = Grid.prepare(before, "A1", "KEEP\t12.5", Grid.Mode.TEXT_ONLY, false, false, false)
        verify(default.calculated.draft == explicit.calculated.draft && Sheet.xlsx(default.calculated.draft).contentEquals(Sheet.xlsx(explicit.calculated.draft)), "old four-argument default")
        val oldTranspose = Grid.prepare(before, "A1", "a\tb\nc\td", Grid.Mode.TEXT_ONLY, true)
        val newTranspose = Grid.prepare(before, "A1", "a\tb\nc\td", Grid.Mode.TEXT_ONLY, true, false, false)
        verify(Sheet.xlsx(oldTranspose.calculated.draft).contentEquals(Sheet.xlsx(newTranspose.calculated.draft)), "old transpose overload")
        val last = Grid.prepare(before, "Z100", "\"\"", Grid.Mode.TEXT_ONLY, true, true, true)
        verify(last.changed == 0 && last.skippedFields == 1 && last.calculated.draft === before, "last valid blank destination")
        failure("skipped fields do not bypass output bounds") { Grid.prepare(before, "Z100", "\t", Grid.Mode.TEXT_ONLY, false, true, true) }
        failure("source rectangle must be valid") { options(before, "\"bad", skip = true, protect = true) }
        val emptySheet = Sheet.empty()
        val exactBytes = ("x".repeat(4096) + "\t").repeat(15) + "y".repeat(4081)
        verify(exactBytes.toByteArray(Charsets.UTF_8).size == Grid.MAX_INPUT_BYTES &&
            Grid.prepare(emptySheet, "A1", exactBytes, Grid.Mode.TEXT_ONLY, false, true, true).inserted == 16,
            "exact byte budget with both protections")
        failure("over byte budget with both protections") {
            Grid.prepare(emptySheet, "A1", exactBytes + "y", Grid.Mode.TEXT_ONLY, false, true, true)
        }
        verify(Grid.prepare(emptySheet, "A1", "é".repeat(2048), Grid.Mode.TEXT_ONLY, false, true, true).inserted == 1,
            "exact multibyte cell with both protections")
        failure("over multibyte cell with both protections") {
            Grid.prepare(emptySheet, "A1", "é".repeat(2049), Grid.Mode.TEXT_ONLY, false, true, true)
        }
        failure("too many skipped source columns") {
            Grid.prepare(emptySheet, "A1", "\t".repeat(26), Grid.Mode.TEXT_ONLY, true, true, true)
        }
        val max = (1..16).joinToString("\n") { (1..16).joinToString("\t") { "" } }
        verify(options(before, max, skip = true, protect = true).skippedFields == 256, "exact field cap even if skipped")
        val over = (1..17).joinToString("\n") { (1..16).joinToString("\t") { "" } }
        verify(failure("over field cap") { options(before, over, skip = true, protect = true) }.message == "Text grid exceeds 256 fields", "specific limit")
        val exhausted = Sheet.restore(Long.MAX_VALUE, initial).draft
        verify(options(exhausted, "\"\"", skip = true, protect = true).calculated.draft === exhausted, "revision exhaustion allows no-op")
        failure("revision exhaustion refuses insertion") { options(exhausted, "\tnew", skip = true, protect = true, transpose = true) }
        verify(before.revision == 7L && before.cells == initial, "all refusals preserve the source")

        val random = Random(61002)
        val tokens = listOf("", "\"\"", "KEEP", "12.5", "NEW", "=1+2", "\"12.5\"", " ")
        val rows = mutableListOf("id,mode,transpose,skip,protect,status,inserted,replaced,cleared,unchanged,skipped,numeric,text,empty")
        var id = 0
        var accepted = 0
        var refused = 0
        for (mode in Grid.Mode.values()) for (transpose in listOf(false, true)) for (skipFlag in listOf(false, true))
            for (protect in listOf(false, true)) repeat(32) {
                val raw = List(2) { List(2) { tokens[random.nextInt(tokens.size)] }.joinToString("\t") }.joinToString("\n")
                val name = id++.toString().padStart(3, '0')
                File(folder, "input-$name.tsv").writeText(raw, Charsets.UTF_8)
                val outcome = runCatching { Grid.prepare(before, "A1", raw, mode, transpose, skipFlag, protect) }
                if (outcome.isSuccess) {
                    val plan = outcome.getOrThrow(); accepted++
                    verify(plan.rows == 2 && plan.columns == 2 && plan.isCurrent(before, "A1"), "matrix dimensions and identity $name")
                    verify(plan.inserted + plan.replaced + plan.cleared + plan.unchanged + plan.skippedFields == 4, "accounting $name")
                    verify(plan.numericFields + plan.textFields + plan.emptyFields == 4, "input classification $name")
                    verify(plan.calculated.draft.revision == if (plan.changed == 0) 7L else 8L, "revision $name")
                    verify(plan.calculated.draft.cells["E1"] == text("outside") && before.cells == initial, "out-of-range/source $name")
                    File(folder, "result-$name.xlsx").writeBytes(Sheet.xlsx(plan.calculated.draft))
                    rows.add("$name,$mode,$transpose,$skipFlag,$protect,accepted,${plan.inserted},${plan.replaced},${plan.cleared},${plan.unchanged},${plan.skippedFields},${plan.numericFields},${plan.textFields},${plan.emptyFields}")
                } else {
                    refused++
                    verify(protect && outcome.exceptionOrNull() is IllegalArgumentException &&
                        outcome.exceptionOrNull()?.message?.startsWith("Protected existing cell ") == true, "only conflict refusal $name")
                    verify(before.cells == initial && before.revision == 7L, "refusal leaves original $name")
                    rows.add("$name,$mode,$transpose,$skipFlag,$protect,refused,,,,,,,,")
                }
            }
        verify(accepted > 0 && refused > 0 && id == 512, "finite positive/negative corpus")
        File(folder, "cases.csv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R61 sheet policies: $assertions host assertions; $id cases ($accepted accepted/$refused refused), independent reader pending")
    }
}
