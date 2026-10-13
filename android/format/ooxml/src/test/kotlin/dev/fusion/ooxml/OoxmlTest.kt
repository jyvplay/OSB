package dev.fusion.ooxml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OoxmlTest {

    private fun zipOf(vararg parts: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun docxParagraphsBecomeLines() {
        val xml = """<?xml version="1.0"?><w:document><w:body>
            <w:p><w:r><w:t>Hello</w:t></w:r><w:r><w:t xml:space="preserve"> world</w:t></w:r></w:p>
            <w:p><w:r><w:t>Second &amp; last</w:t></w:r></w:p>
            </w:body></w:document>"""
        val text = Ooxml.extractDocxText(ByteArrayInputStream(zipOf("word/document.xml" to xml))).getOrThrow()
        assertEquals("Hello world\nSecond & last", text)
    }

    @Test fun docxMissingPartFailsLoudly() {
        val result = Ooxml.extractDocxText(ByteArrayInputStream(zipOf("other.xml" to "<a/>")))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("word/document.xml"))
    }

    @Test fun xlsxResolvesSharedStringsAndNumbers() {
        val shared = """<sst><si><t>Name</t></si><si><t>Beam</t></si></sst>"""
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><v>42</v></c></row>
            <row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2"><v>7.5</v></c></row>
            </sheetData></worksheet>"""
        val bytes = zipOf("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet)
        val result = Ooxml.extractXlsxSheet(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(listOf("Name", "42"), result.rows[0])
        assertEquals(listOf("Beam", "7.5"), result.rows[1])
    }

    @Test fun unescapeHandlesAmpersandLast() {
        assertEquals("a&lt;b", Ooxml.unescape("a&amp;lt;b"))
    }

    @Test fun emptyCellsArePreserved() {
        val sheet = """<worksheet><sheetData>
            <row r="1"><c r="A1"/><c r="B1"><v>5</v></c></row></sheetData></worksheet>"""
        val rows = Ooxml.parseSheetXml(sheet, emptyList())
        assertEquals(listOf("", "5"), rows[0])
    }
}
