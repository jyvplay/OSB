// SPDX-License-Identifier: Apache-2.0
package dev.fusion.pdf

/**
 * Production-grade PDF `xref` table & `startxref` offset parser.
 */
class PdfXrefTrailerParser private constructor() {
    companion object {
        data class XrefEntry(val objNum: Int, val offset: Long, val generation: Int, val inUse: Boolean)

        fun parseStartXref(pdfText: String): Long? {
            val idx = pdfText.lastIndexOf("startxref")
            if (idx < 0) return null
            val tail = pdfText.substring(idx + "startxref".length).trim()
            val firstToken = tail.split(Regex("\\s+")).firstOrNull() ?: return null
            return firstToken.toLongOrNull()
        }

        fun parseXrefSubsection(lines: List<String>): List<XrefEntry> {
            require(lines.isNotEmpty()) { "empty xref subsection" }
            val header = lines[0].trim().split(Regex("\\s+"))
            require(header.size == 2) { "invalid xref header" }
            val startObj = header[0].toInt()
            val count = header[1].toInt()
            require(count >= 0 && lines.size - 1 >= count) { "xref count mismatch" }
            val out = mutableListOf<XrefEntry>()
            for (i in 0 until count) {
                val parts = lines[i + 1].trim().split(Regex("\\s+"))
                require(parts.size >= 3) { "invalid xref entry at $i" }
                out.add(
                    XrefEntry(
                        objNum = startObj + i,
                        offset = parts[0].toLong(),
                        generation = parts[1].toInt(),
                        inUse = parts[2] == "n"
                    )
                )
            }
            return out
        }
    }
}
