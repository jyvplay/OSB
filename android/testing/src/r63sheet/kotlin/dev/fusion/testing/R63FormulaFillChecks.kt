// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetDraft as Sheet
import dev.fusion.ooxml.OfficeSheetRangeEdit as Range
import java.io.File
import java.util.Base64
import java.util.Random

/** Executes the real bounded formula-fill kernel. XLSX/oracle inspection is separate. */
object R63FormulaFillChecks {
    private var assertions = 0
    private fun verify(condition: Boolean, message: String) { assertions++; check(condition) { message } }
    private fun refuses(message: String, body: () -> Unit): Throwable {
        val error = runCatching(body).exceptionOrNull()
        verify(error is IllegalArgumentException, "Expected refusal: $message, got $error")
        return requireNotNull(error)
    }
    private fun f(s: String) = Sheet.Cell(Sheet.Kind.FORMULA, s)
    private fun t(s: String) = Sheet.Cell(Sheet.Kind.TEXT, s)
    private fun n(s: String) = Sheet.Cell(Sheet.Kind.NUMBER, s)
    private fun draft(vararg cells: Pair<String, Sheet.Cell>) = Sheet.restore(7, linkedMapOf(*cells)).draft
    private fun fill(d: Sheet.Draft, selected: String, range: String, protect: Boolean = true) =
        Range.prepare(d, selected, range, Range.Operation.FILL_FORMULA, protect)

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        val source = draft("M50" to f("=SUM(A1:\$B2)+C\$1+\$D\$2+1E2+LEN(\"A1 \"\"B2\"\"\")"), "Z100" to t("outside"))
        val plan = fill(source, "M50", "N51")
        verify(plan.calculated.draft.cells["N51"] == f("=SUM(B2:\$B3)+D\$1+\$D\$2+1E2+LEN(\"A1 \"\"B2\"\"\")"), "mixed references, range, strings and exponent")
        verify(plan.inserted == 1 && plan.formulasWritten == 1 && plan.formulasRemoved == 0 && plan.changed == 1, "formula accounting")
        verify(plan.calculated.draft.cells["Z100"] == t("outside") && source.cells["N51"] == null, "outside and original preserved")
        verify(plan.isCurrent(source, "M50") && !plan.isCurrent(source, "N51"), "selection bound")
        val equivalent = Sheet.restore(source.revision, source.cells).draft
        verify(!plan.isCurrent(equivalent, "M50"), "equal but distinct snapshot is stale")

        val cases = listOf(
            "=a1" to "=b2", "=\$a1" to "=\$a2", "=a\$1" to "=b\$1", "=\$a\$1" to "=\$a\$1",
            "=IF(A1>0,\"A1\",\"\"\"B2\"\"\")" to "=IF(B2>0,\"A1\",\"\"\"B2\"\"\")",
            "=1E2+1e-2+A1" to "=1E2+1e-2+B2", "=SUM(A1:\$B\$2)" to "=SUM(B2:\$B\$2)",
        )
        for ((original, expected) in cases) {
            val d = draft("M50" to f(original))
            val p = fill(d, "M50", "N51")
            verify(p.calculated.draft.cells["N51"] == f(expected), "translation of $original")
            verify(fill(d, "M50", "M50").calculated.draft === d, "source identity preserved: $original")
        }
        val constant = draft("B2" to f("=\$A\$1+1"), "C2" to f("=\$A\$1+1"))
        val identical = fill(constant, "B2", "B2:C2")
        verify(identical.changed == 0 && identical.unchanged == 2 && identical.calculated.draft === constant, "identical formula fill is no-op")
        val conflict = draft("B2" to f("=A2"), "D2" to t("do not overwrite"))
        val error = refuses("late protected target") { fill(conflict, "B2", "B2:D2") }
        verify(error.message?.contains("D2") == true && conflict.cells["C2"] == null, "late conflict leaves original untouched")
        val overridden = fill(conflict, "B2", "B2:D2", false)
        verify(overridden.inserted == 1 && overridden.replaced == 1 && overridden.formulasWritten == 2, "explicit override")
        refuses("text source is not a formula") { fill(draft("A1" to t("=1+1")), "A1", "A2") }
        refuses("missing source") { fill(draft("A1" to n("1")), "B1", "C1") }
        for ((from, formula, to) in listOf(
            Triple("B2", "=A2", "A2"), Triple("B2", "=B1", "B1"),
            Triple("Y99", "=Z99", "Z99"), Triple("Y99", "=Y100", "Y100"),
        )) {
            val before = draft(from to f(formula))
            val outside = refuses("translated reference outside grid") { fill(before, from, to) }
            verify(outside.message?.contains("outside A1:Z100") == true && before.cells.size == 1, "boundary and original")
        }
        val anchored = draft("B2" to f("=\$A\$1"))
        verify(fill(anchored, "B2", "A2").calculated.draft.cells["A2"] == f("=\$A\$1"), "absolute A1 survives leftward move")
        // An absolute self-reference at A1 is a cycle and is therefore refused.
        refuses("anchored formula would create cycle") { fill(anchored, "B2", "A1") }
        val cycle = draft("B2" to f("=A2"), "A3" to f("=B3"))
        refuses("indirect cycle") { fill(cycle, "B2", "B3") }
        verify(cycle.cells["B3"] == null, "cycle refusal is atomic")
        val maxFormulas = linkedMapOf<String, Sheet.Cell>()
        for (row in 1..64) maxFormulas["A$row"] = f("=1")
        val full = Sheet.restore(9, maxFormulas).draft
        refuses("65th formula") { fill(full, "A1", "B1") }
        verify(full.cells.size == 64 && full.cells["B1"] == null, "formula cap refusal is atomic")
        val long = draft("B9" to f("=A9" + " ".repeat(509)))
        refuses("translated formula grows beyond 512 characters") { fill(long, "B9", "B10") }
        verify(long.cells["B10"] == null, "length refusal is atomic")
        val maxRevision = Sheet.restore(Long.MAX_VALUE, mapOf("B2" to f("=1"))).draft
        verify(fill(maxRevision, "B2", "B2").calculated.draft === maxRevision, "maximum revision no-op")
        refuses("maximum revision change") { fill(maxRevision, "B2", "B3") }

        val formulas = listOf(
            "=SUM(A1:B2)+\$C\$3+D\$4+\$E5+1E2",
            "=IF(A1>0,\"A1 \"\"B2\"\"\",B2)",
            "=COUNT(A1:B2)+ABS(-1E2)+\$C3",
            "=A1+\$B\$2+C\$3+\$D4",
            "=IFERROR(1/(A1-1),\"C3\")",
            "=SUM(\$A1:B\$2,\$C\$3)+2.5e-1",
            "=IF(A1=B1,TRUE,FALSE)",
            "=LEN(\"A1\"\"E2\")+1E2",
        )
        val random = Random(63063)
        val rows = mutableListOf("id\tformula64\trange\tprotect\tstatus\tinserted\treplaced\tunchanged\twritten")
        var accepted = 0; var rejected = 0; var id = 0
        for (formula in formulas) for (protect in listOf(false, true)) repeat(32) {
            val initial = linkedMapOf("A1" to n("1"), "A2" to n("2"), "B1" to n("3"), "B2" to n("4"),
                "C3" to n("5"), "D4" to n("6"), "E5" to n("7"), "M50" to f(formula),
                "O52" to t("existing"), "Z100" to t("outside"))
            val before = Sheet.restore(7, initial).draft
            val col = 'M' + random.nextInt(3); val row = 50 + random.nextInt(3)
            val target = if (random.nextBoolean()) "M50:$col$row" else "$col$row:M50"
            val name = (id++).toString().padStart(3, '0')
            val encoded = Base64.getEncoder().encodeToString(formula.toByteArray(Charsets.UTF_8))
            val outcome = runCatching { fill(before, "M50", target, protect) }
            if (outcome.isSuccess) {
                accepted++
                val p = outcome.getOrThrow()
                verify(p.inserted + p.replaced + p.unchanged == p.positions && p.cleared == 0, "position accounting $name")
                verify(p.calculated.draft.revision == if (p.changed == 0) 7L else 8L, "one revision/no-op $name")
                verify(p.isCurrent(before, "M50") && before.cells == initial, "snapshot binding $name")
                File(folder, "result-$name.xlsx").writeBytes(Sheet.xlsx(p.calculated.draft))
                rows.add("$name\t$encoded\t$target\t$protect\taccepted\t${p.inserted}\t${p.replaced}\t${p.unchanged}\t${p.formulasWritten}")
            } else {
                rejected++
                verify(outcome.exceptionOrNull() is IllegalArgumentException && before.cells == initial, "refusal/source integrity $name")
                rows.add("$name\t$encoded\t$target\t$protect\trefused\t\t\t\t")
            }
        }
        verify(id == 512 && accepted > 0 && rejected > 0, "positive/negative corpus")
        File(folder, "cases.tsv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R63 formula fill: $assertions assertions; $id cases ($accepted accepted/$rejected refused); independent reader pending")
    }
}
