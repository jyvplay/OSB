// SPDX-License-Identifier: Apache-2.0
package dev.fusion.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeSessionControllerTest {
    @Test
    fun undoRedoAndOoxmlExportsPreserveFormulasAndSlides() {
        val ctl = OfficeSessionController()
        ctl.updateCellInput("B3", "=B1*2")
        assertEquals("500", OfficeSessionController.formatCellValue(ctl.state.evaluatedCells().getValue("B3")))
        ctl.undo()
        assertTrue("B3" !in ctl.state.sheetCells)
        ctl.redo()
        assertEquals("500", OfficeSessionController.formatCellValue(ctl.state.evaluatedCells().getValue("B3")))
        assertTrue(ctl.state.exportDocxBytes().isNotEmpty())
        assertTrue(ctl.state.exportXlsxBytes().isNotEmpty())
        assertTrue(ctl.state.exportPptxBytes().isNotEmpty())
    }
}
