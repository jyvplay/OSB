// SPDX-License-Identifier: Apache-2.0
package dev.fusion.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CleanRoomPoiEngineTest {
    @Test
    fun docxXlsxFormulasPptxRoundTripAndRefuseMacrosAndXxe() {
        // 1. DOCX round-trip with headings + paragraphs + structured table
        val doc = DocxDocument(
            paragraphs = listOf(
                DocxParagraph("Quarterly Architecture Review", headingLevel = 1),
                DocxParagraph("All telemetry defaults to zero external connections.")
            ),
            tables = listOf(listOf(listOf("Module", "License"), listOf("format:poi", "Apache-2.0")))
        )
        val docxBytes = CleanRoomPoiEngine.writeDocx(doc)
        val docxParsed = CleanRoomPoiEngine.readDocx(docxBytes)
        assertEquals(2, docxParsed.paragraphs.size)
        assertEquals(1, docxParsed.paragraphs[0].headingLevel)
        assertEquals("Quarterly Architecture Review", docxParsed.paragraphs[0].text)
        assertEquals(listOf(listOf("Module", "License"), listOf("format:poi", "Apache-2.0")), docxParsed.tables.single())

        // 2. XLSX multi-sheet formulas, cross-sheet lookups, cycle & div-by-zero safety
        val wb = CleanWorkbook(
            listOf(
                WorkbookSheet(
                    "Inputs",
                    mapOf(
                        "A1" to SheetCell("A1", CellValue.Number(12.5)),
                        "A2" to SheetCell("A2", CellValue.Number(7.5)),
                        "A3" to SheetCell("A3", CellValue.Number(30.0))
                    )
                ),
                WorkbookSheet(
                    "Summary",
                    mapOf(
                        "B1" to SheetCell("B1", CellValue.Blank, "SUM(Inputs!A1:A3)"),
                        "B2" to SheetCell("B2", CellValue.Blank, "IF(B1>=50, ROUND(B1/3, 2), 0)"),
                        "C1" to SheetCell("C1", CellValue.Blank, "C2+1"),
                        "C2" to SheetCell("C2", CellValue.Blank, "C1+1"),
                        "D1" to SheetCell("D1", CellValue.Blank, "10/0")
                    )
                )
            )
        )
        val xlsxBytes = CleanRoomPoiEngine.writeXlsx(wb)
        val reloaded = CleanRoomPoiEngine.readXlsx(xlsxBytes)
        val eval = CleanRoomPoiEngine.evaluateWorkbook(reloaded).getValue("Summary")
        assertEquals(CellValue.Number(50.0), eval["B1"])
        assertEquals(CellValue.Number(16.67), eval["B2"])
        assertEquals(CellValue.Error("#CYCLE!"), eval["C1"])
        assertEquals(CellValue.Error("#DIV/0!"), eval["D1"])

        // 3. PPTX multi-slide deck round-trip
        val deck = SlideDeck(
            listOf(
                SlideDeck.SlidePage(1, "Fusion R26 Architecture", listOf("56 modules", "Zero mocks")),
                SlideDeck.SlidePage(2, "Amnesic Posture", listOf("RAM-only AES-256-GCM", "Fail-closed Tor SOCKS5h"))
            )
        )
        val pptxBytes = CleanRoomPoiEngine.writePptx(deck)
        val parsedDeck = CleanRoomPoiEngine.readPptx(pptxBytes)
        assertEquals(2, parsedDeck.slides.size)
        assertEquals("Amnesic Posture", parsedDeck.slides[1].title)
        assertEquals(listOf("RAM-only AES-256-GCM", "Fail-closed Tor SOCKS5h"), parsedDeck.slides[1].bullets)

        // 4. Macro-bearing archive (`vbaProject.bin`) strictly refused
        val macroZip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("word/vbaProject.bin"))
                zos.write("FAKEVBA".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { CleanRoomPoiEngine.readDocx(macroZip) }

        // 5. XXE DOCTYPE archive strictly refused
        val xxeZip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write("""<?xml version="1.0"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><w:document xmlns:w="x">&xxe;</w:document>""".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        assertTrue(runCatching { CleanRoomPoiEngine.readDocx(xxeZip) }.isFailure)
    }
}
