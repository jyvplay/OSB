package dev.fusion.format.pdf

import java.nio.charset.StandardCharsets

/**
 * Clean-room ISO 32000-1 Annex F Linearized ("Fast Web View") PDF Parameterization Dictionary
 * Emitter & Byte-Length / Page-Count Integrity Validator (Apache-2.0).
 */
object PdfLinearizedHintValidator {
    const val SCOPE =
        "ISO 32000-1 Annex F Linearized Fast-Web-View dictionary (/Linearized 1, /L, /H, /O, /E, /N, /T) builder & byte-length auditor"

    data class LinearizationCheck(
        val isLinearized: Boolean,
        val declaredFileLength: Long,
        val actualFileLength: Long,
        val pageCountN: Int,
        val firstPageObjO: Int,
        val hintOffsetH: Pair<Long, Long>?,
        val issues: List<String>,
    )

    /** Builds a deterministic self-consistent Linearized PDF 1.7 whose `/L` equals the exact byte size! */
    fun buildLinearizedSamplePdf(pageCount: Int = 2, firstPageLabel: String = "Fast Web View Page 1"): ByteArray {
        require(pageCount in 1..64)
        // Fixed-width 10-digit /L and /T placeholders so exact byte length never shifts offset widths!
        fun renderWithLength(fileLen: Int, mainXrefOffset: Int): ByteArray {
            val lPad = "%010d".format(fileLen)
            val tPad = "%010d".format(mainXrefOffset)
            val header = buildString {
                append("%PDF-1.7\n")
                append("1 0 obj\n<< /Linearized 1 /L $lPad /H [0000000180 0000000064] /O 4 /E 0000000520 /N $pageCount /T $tPad >>\nendobj\n")
                append("2 0 obj\n<< /Type /Catalog /Pages 3 0 R >>\nendobj\n")
                append("3 0 obj\n<< /Type /Pages /Kids [4 0 R] /Count $pageCount >>\nendobj\n")
                append("4 0 obj\n<< /Type /Page /Parent 3 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n")
                val streamBody = "BT (${firstPageLabel.replace("(", "").replace(")", "")}) Tj ET"
                append("5 0 obj\n<< /Length ${streamBody.length} >>\nstream\n$streamBody\nendstream\nendobj\n")
            }
            val mainXrefPos = header.toByteArray(StandardCharsets.US_ASCII).size
            val tail = "xref\n0 6\n0000000000 65535 f \ntrailer\n<< /Size 6 /Root 2 0 R >>\nstartxref\n$mainXrefPos\n%%EOF\n"
            return (header + tail).toByteArray(StandardCharsets.US_ASCII)
        }
        val pass1 = renderWithLength(0, 0)
        val text1 = String(pass1, StandardCharsets.US_ASCII)
        val xrefIdx = text1.indexOf("\nxref\n") + 1
        return renderWithLength(pass1.size, xrefIdx)
    }

    fun inspect(pdfBytes: ByteArray): LinearizationCheck {
        val issues = mutableListOf<String>()
        val headWindow = String(pdfBytes, 0, minOf(pdfBytes.size, 1024), StandardCharsets.ISO_8859_1)
        val dictMatch = Regex("<<[^>]*?/Linearized\\s+1(?:\\.0)?[^>]*?>>").find(headWindow)
            ?: return LinearizationCheck(false, 0L, pdfBytes.size.toLong(), 0, 0, null, listOf("NOT_LINEARIZED"))

        val dict = dictMatch.value
        val lVal = Regex("/L\\s+([0-9]+)").find(dict)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        val nVal = Regex("/N\\s+([0-9]+)").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val oVal = Regex("/O\\s+([0-9]+)").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val hMatch = Regex("/H\\s*\\[\\s*([0-9]+)\\s+([0-9]+)\\s*\\]").find(dict)
        val hPair = hMatch?.let { it.groupValues[1].toLong() to it.groupValues[2].toLong() }

        if (lVal != pdfBytes.size.toLong()) issues.add("LENGTH_MISMATCH_DECLARED_${lVal}_ACTUAL_${pdfBytes.size}")
        if (nVal <= 0) issues.add("INVALID_PAGE_COUNT_N")
        if (oVal <= 0) issues.add("MISSING_FIRST_PAGE_OBJ_O")
        if (hPair == null || hPair.first + hPair.second > pdfBytes.size) issues.add("INVALID_HINT_STREAM_BOUNDS_H")

        return LinearizationCheck(
            isLinearized = issues.isEmpty(),
            declaredFileLength = lVal,
            actualFileLength = pdfBytes.size.toLong(),
            pageCountN = nVal,
            firstPageObjO = oVal,
            hintOffsetH = hPair,
            issues = issues,
        )
    }
}
