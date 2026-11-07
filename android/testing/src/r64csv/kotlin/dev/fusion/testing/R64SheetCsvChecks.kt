// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.OfficeSheetCsv
import dev.fusion.ooxml.OfficeSheetDraft
import dev.fusion.ooxml.OfficeSheetDraft.Cell
import dev.fusion.ooxml.OfficeSheetDraft.Draft
import dev.fusion.ooxml.OfficeSheetDraft.Kind
import java.io.File

/** Values-only exports from real sheet drafts. Consumer verification is independent. */
object R64SheetCsvChecks {
    private var checks = 0
    private fun ok(value: Boolean, why: String) { checks++; check(value) { why } }
    private fun refused(why: String, body: () -> Unit) {
        ok(runCatching(body).exceptionOrNull() is IllegalArgumentException, "Expected refusal: $why")
    }
    private fun draft(cells: Map<String, Cell> = emptyMap(), revision: Long = 0): Draft =
        OfficeSheetDraft.restore(revision, cells).draft
    private val texts = listOf("hello", " spaced ", "a,b\"c\nx", "=1+1", "\tword", "  +1", "＝evil", "'-7")
    private val formulas = listOf("=A1*2", "=IF(A2,\"safe\",\"other\")", "=IF(A2,\"=1+2\",\"safe\")", "=1/0")
    private val extra = listOf("C3", "C6", "H3", "H6")

    @JvmStatic fun main(args: Array<String>) {
        val folder = File(args.single()).also { check(it.exists() || it.mkdirs()) }
        ok(OfficeSheetCsv.write(draft()).contentEquals("\"\"\r\n".toByteArray()), "empty draft has one blank field")
        for (danger in listOf("=1", "+1", "-1", "@x", "＝1", "＋1", "－1", "＠x", "−1", "\tword", "\rword", "\nword",
                "  =1", "\u00a0=1", "\u200e=1", "'=1", "\"=1")) {
            refused("dangerous prefix") { OfficeSheetCsv.write(draft(cells = mapOf("A1" to Cell(Kind.TEXT, danger)))) }
        }
        for (safe in listOf("hello", " hello", "value=1", "a,b\"c\nx", "'quoted", "", "α")) {
            ok(OfficeSheetCsv.write(draft(cells = mapOf("A1" to Cell(Kind.TEXT, safe)))).isNotEmpty(), "safe text survives")
        }
        ok(String(OfficeSheetCsv.write(draft(cells = mapOf("A1" to Cell(Kind.NUMBER, "-1")))), Charsets.UTF_8) == "\"-1\"\r\n", "typed negative is data, not source text")
        val bottom = OfficeSheetCsv.write(draft(cells = mapOf("Z100" to Cell(Kind.TEXT, "last"))))
        ok(bottom.toString(Charsets.UTF_8).lines().size == 101 && bottom.size > 7800, "bounded furthest address")
        val amplified = linkedMapOf<String, Cell>()
        val addresses = (1..5).flatMap { row -> ('A'..'Z').map { column -> "$column$row" } }
        addresses.take(63).forEach { amplified[it] = Cell(Kind.TEXT, "\"".repeat(4096)) }
        addresses.drop(63).take(64).forEach { amplified[it] = Cell(Kind.FORMULA, "=A1") }
        amplified["Z100"] = Cell(Kind.TEXT, "\"".repeat(3904))
        val large = draft(amplified)
        refused("derived values exceed CSV byte budget") { OfficeSheetCsv.write(large) }
        val rows = mutableListOf("id,text,formula,negative,shape,status")
        var id = 0; var accepted = 0; var rejected = 0
        for (text in texts.indices) for (formula in formulas.indices) for (negative in listOf(false, true)) for (shape in extra.indices) {
            val input = draft(revision = 7, cells = mapOf(
                "A1" to Cell(Kind.NUMBER, if (negative) "-12.5" else "12.5"),
                "B1" to Cell(Kind.TEXT, texts[text]), "C1" to Cell(Kind.FORMULA, formulas[formula]),
                "A2" to Cell(Kind.BOOLEAN, "TRUE"), extra[shape] to Cell(Kind.TEXT, "end,$id"),
            ))
            val before = input.cells.toMap()
            val result = runCatching { OfficeSheetCsv.write(input) }
            val name = (id++).toString().padStart(3, '0')
            if (result.isSuccess) {
                accepted++
                val bytes = result.getOrThrow()
                ok(bytes.contentEquals(OfficeSheetCsv.write(input)), "deterministic $name")
                ok(bytes.size <= OfficeSheetCsv.MAX_OUTPUT_BYTES, "size $name")
                File(folder, "sheet-$name.csv").writeBytes(bytes)
                rows.add("$name,$text,$formula,$negative,$shape,accepted")
            } else {
                rejected++
                ok(result.exceptionOrNull() is IllegalArgumentException, "bounded refusal $name")
                rows.add("$name,$text,$formula,$negative,$shape,refused")
            }
            ok(input.cells == before && input.revision == 7L, "draft not modified $name")
        }
        ok(id == 256 && accepted > 0 && rejected > 0, "positive and negative corpus")
        File(folder, "cases.csv").writeText(rows.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        println("R64 sheet CSV: $checks assertions; $id cases ($accepted accepted/$rejected refused); independent reader pending")
    }
}
