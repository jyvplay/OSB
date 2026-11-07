// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Export an editor's plain text as a new DOCX. This is not DOCX import, rich-text editing,
 * pagination, source-format preservation, or LibreOfficeKit. CRLF and CR become paragraph
 * separators; tabs, blank paragraphs, supplementary characters and surrounding spaces remain.
 */
object PlainWriterDocx {
    const val MAX_TEXT_BYTES = 128 * 1024
    const val MAX_PARAGRAPHS = 2_048
    const val MAX_OUTPUT_BYTES = 2 * 1024 * 1024

    fun write(text: String): ByteArray {
        OfficeSheetDraft.validateXml(text)
        require(OfficeSheetDraft.utf8(text).size <= MAX_TEXT_BYTES) { "DOCX export accepts at most 128 KiB of UTF-8 text" }
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        require(lines.size <= MAX_PARAGRAPHS) { "DOCX export accepts at most $MAX_PARAGRAPHS paragraphs" }
        val blocks = lines.map { line -> DocxPackage.Block.Paragraph(listOf(DocxPackage.Run(line))) }
        val bytes = DocxPackage.write(DocxPackage.Document(blocks = blocks))
        require(bytes.size <= MAX_OUTPUT_BYTES) { "DOCX copy exceeds the export budget" }
        return bytes
    }
}
