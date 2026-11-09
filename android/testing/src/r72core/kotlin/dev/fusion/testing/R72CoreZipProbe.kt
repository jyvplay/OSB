// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.ooxml.MultiSheetBook
import dev.fusion.ooxml.XlsxStyledBook
import java.io.File

/**
 * Reads independently built workbooks through two production readers.
 * Host-only: a refusal means an IllegalArgumentException from the reader.
 */
object R72CoreZipProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Expected read <directory>" }
        require(args[0] == "read") { "Unknown mode ${args[0]}" }
        val directory = File(args[1])
        val cases = File(directory, "cases.tsv")
        require(cases.isFile) { "Missing cases.tsv" }
        val lines = cases.readLines().drop(1).filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "Empty case list" }
        val report = StringBuilder()
        for (line in lines) {
            val cells = line.split('\t')
            require(cells.size == 7) { "Malformed case row: $line" }
            val id = cells[0]
            val bytes = File(directory, cells[2]).readBytes()
            val outcome = try {
                when (cells[1]) {
                    "book" -> MultiSheetBook.read(bytes)
                    "styled" -> XlsxStyledBook.read(bytes)
                    else -> error("Unknown reader ${cells[1]}")
                }
                "ACCEPT"
            } catch (e: IllegalArgumentException) {
                "REFUSE"
            }
            report.append(id).append('|').append(outcome).append('\n')
        }
        File(directory, "candidate.txt").writeText(report.toString())
        println("R72 core-reader probe executed ${lines.size} actual XLSX cases; host-only")
    }
}
