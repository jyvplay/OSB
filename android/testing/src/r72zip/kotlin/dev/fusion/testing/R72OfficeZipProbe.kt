// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.DocxPackage
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.FormulaXlsx
import java.io.File

/**
 * Executes the real bounded Office readers against disposable archives that were built
 * by a separate program. Host-only: no Android storage provider, UI or isolation is implied.
 */
object R72OfficeZipProbe {
    private const val TEXT = "Office ZIP boundary: α <&>"

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Expected seed|read and an existing fixture directory" }
        val dir = File(args[1])
        require(dir.isDirectory && !java.nio.file.Files.isSymbolicLink(dir.toPath())) { "Bad fixture directory" }
        when (args[0]) {
            "seed" -> {
                val doc = DocxPackage.Document(blocks = listOf(DocxPackage.Block.Paragraph(listOf(DocxPackage.Run(TEXT)))))
                val sheet = linkedMapOf(
                    "A1" to FormulaEval.CellInput.TextLiteral(TEXT),
                    "B1" to FormulaEval.CellInput.NumberLiteral(2.0),
                    "C1" to FormulaEval.CellInput.Formula("=B1*2"),
                )
                File(dir, "seed.docx").writeBytes(DocxPackage.write(doc))
                File(dir, "seed.xlsx").writeBytes(FormulaXlsx.write("Boundary", sheet))
                println("Generated real DOCX and XLSX seeds; host-only")
            }
            "read" -> {
                val manifest = File(dir, "cases.tsv")
                require(manifest.isFile) { "Missing fixture manifest" }
                val lines = manifest.readLines(Charsets.UTF_8)
                require(lines.firstOrNull() == "id\tkind\tfile\texpected\treason\tsha256") { "Bad manifest header" }
                val results = mutableListOf("id\tstatus\treason")
                for (line in lines.drop(1)) {
                    val parts = line.split('\t')
                    require(parts.size == 6) { "Malformed fixture row" }
                    val (id, kind, filename) = parts
                    require(id.matches(Regex("[a-z0-9-]+")) && filename == "$id.zip") { "Unsafe fixture name" }
                    val file = File(dir, filename)
                    require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath())) { "Missing fixture" }
                    val outcome = if (kind !in setOf("docx", "xlsx")) "REFUSE\tCORRUPT_OR_OTHER" else try {
                        val input = file.readBytes()
                        if (kind == "docx") {
                            val p = DocxPackage.read(input).blocks.single() as DocxPackage.Block.Paragraph
                            check(p.runs.joinToString("") { it.text } == TEXT) { "DOCX sentinel changed" }
                        } else {
                            val book = FormulaXlsx.read(input)
                            check(book.sheetName == "Boundary") { "XLSX sheet name changed" }
                            check(book.inputs["A1"] == FormulaEval.CellInput.TextLiteral(TEXT)) { "XLSX text changed" }
                            check(book.inputs["B1"] == FormulaEval.CellInput.NumberLiteral(2.0)) { "XLSX number changed" }
                            check((book.inputs["C1"] as? FormulaEval.CellInput.Formula)?.body == "B1*2") { "XLSX formula changed" }
                        }
                        "ACCEPT\t-"
                    } catch (e: Exception) {
                        val message = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
                        "REFUSE\t" + when {
                            "expanded ZIP budget exceeded" in message -> "EXPANDED"
                            "duplicate ZIP entry" in message -> "DUPLICATE"
                            "too many ZIP entries" in message -> "COUNT"
                            "unsafe ZIP entry" in message -> "UNSAFE"
                            "macro part" in message -> "MACRO"
                            "exceeds 16777216 bytes" in message -> "INPUT"
                            else -> "CORRUPT_OR_OTHER"
                        }
                    }
                    results.add("$id\t$outcome")
                }
                File(dir, "observed.tsv").writeText(results.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                println("R72 ZIP probe executed ${results.size - 1} actual reader cases")
            }
            else -> error("Unknown probe mode")
        }
    }
}
