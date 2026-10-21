// SPDX-License-Identifier: Apache-2.0
package dev.fusion.office

import dev.fusion.poi.CellValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeDocumentModelTest {
    @Test
    fun spreadsheetFormulasUndoRedoAndAllThreeOoxmlExportsRoundTrip() {
        val controller = OfficeSessionController()
        assertEquals(CellValue.Number(250.0), controller.state.evaluatedCells()["B1"])
        assertEquals(CellValue.Number(83.33), controller.state.evaluatedCells()["B2"])

        controller.updateCellInput("B3", "=B1*2")
        assertEquals(CellValue.Number(500.0), controller.state.evaluatedCells()["B3"])

        assertTrue(controller.undo())
        assertEquals(null, controller.state.evaluatedCells()["B3"])
        assertTrue(controller.redo())
        assertEquals(CellValue.Number(500.0), controller.state.evaluatedCells()["B3"])

        val xlsx = controller.state.exportXlsxBytes()
        val fresh = OfficeSessionController()
        fresh.importOoxmlBytes("budget.xlsx", xlsx)
        assertEquals(CellValue.Number(500.0), fresh.state.evaluatedCells()["B3"])

        fresh.updateDocument(" Executive Brief ", "Paragraph alpha\nParagraph beta")
        val docx = fresh.state.exportDocxBytes()
        val docReader = OfficeSessionController()
        docReader.importOoxmlBytes("brief.docx", docx)
        assertEquals("Executive Brief", docReader.state.docTitle.trim())
        assertTrue(docReader.state.docBody.contains("Paragraph beta"))

        docReader.addSlide("Security Guarantees", "Zero macros; Explicit loss report")
        val pptx = docReader.state.exportPptxBytes()
        val slideReader = OfficeSessionController()
        slideReader.importOoxmlBytes("deck.pptx", pptx)
        assertEquals(3, slideReader.state.slides.size)
        assertEquals("Security Guarantees", slideReader.state.slides.last().title)
    }
}
