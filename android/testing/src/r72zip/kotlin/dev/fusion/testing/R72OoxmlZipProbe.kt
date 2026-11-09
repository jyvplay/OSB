// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.Ooxml
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Executes the production import extractor on independently built archives; host-only. */
object R72OoxmlZipProbe {
    private const val DOC_TEXT = "Import boundary α <&>"
    private const val CELL_TEXT = "42"

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Expected seed|read and an existing fixture directory" }
        val dir = File(args[1])
        require(dir.isDirectory && !java.nio.file.Files.isSymbolicLink(dir.toPath())) { "Bad fixture directory" }
        when (args[0]) {
            "seed" -> {
                write(dir, "seed.docx", arrayOf(
                    "word/document.xml" to """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>$DOC_TEXT</w:t></w:r></w:p></w:body></w:document>""",
                    "[Content_Types].xml" to """<Types/>""",
                ))
                write(dir, "seed.xlsx", arrayOf(
                    "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1"><v>$CELL_TEXT</v></c></row></sheetData></worksheet>""",
                    "[Content_Types].xml" to """<Types/>""",
                ))
                println("Generated real import seeds; host-only")
            }
            "read" -> {
                val manifest = File(dir, "cases.tsv")
                require(manifest.isFile) { "Missing manifest" }
                val lines = manifest.readLines(Charsets.UTF_8)
                require(lines.firstOrNull() == "id\tkind\tfile\texpected\treason\tsha256") { "Bad manifest header" }
                val results = mutableListOf("id\tstatus\treason")
                for (line in lines.drop(1)) {
                    val parts = line.split('\t')
                    require(parts.size == 6) { "Malformed row" }
                    val (id, kind, filename) = parts
                    require(id.matches(Regex("[a-z0-9-]+")) && filename == "$id.zip") { "Unsafe fixture name" }
                    val file = File(dir, filename)
                    require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath())) { "Missing fixture" }
                    val bytes = file.readBytes()
                    results.add("$id\t" + try {
                        when (kind) {
                            "docx" -> {
                                val text = Ooxml.extractDocxText(ByteArrayInputStream(bytes)).getOrThrow()
                                check(text.contains(DOC_TEXT)) { "Extracted DOCX text changed" }
                            }
                            "xlsx" -> {
                                val sheet = Ooxml.extractXlsxSheet(ByteArrayInputStream(bytes)).getOrThrow()
                                check(sheet.rows.flatten().contains(CELL_TEXT)) { "Extracted cell changed" }
                            }
                            else -> error("Unknown kind")
                        }
                        "ACCEPT\t-"
                    } catch (e: Exception) {
                        val message = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
                        "REFUSE\t" + when {
                            "too many entries" in message -> "COUNT"
                            "Unsafe part name" in message -> "UNSAFE"
                            "Duplicate part name" in message -> "DUPLICATE"
                            "budget exceeded" in message -> "EXPANDED"
                            else -> "CORRUPT_OR_OTHER"
                        }
                    })
                }
                File(dir, "observed.tsv").writeText(results.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                println("R72 import probe executed ${results.size - 1} actual extractor cases")
            }
            else -> error("Unknown mode")
        }
    }

    private fun write(dir: File, name: String, parts: Array<Pair<String, String>>) {
        require(!File(dir, name).exists()) { "Refusing to overwrite $name" }
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((part, content) in parts) {
                zip.putNextEntry(ZipEntry(part))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        File(dir, name).writeBytes(bytes.toByteArray())
    }
}
