// SPDX-License-Identifier: Apache-2.0
package dev.fusion.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanRoomPoiEngineTest {
    @Test
    fun roundTripsDocxXlsxPptxEvaluatesFormulasAndFlagsCircularReference() {
        val wb = CleanWorkbook(
            listOf(
                WorkbookSheet(
                    "Sheet1",
                    mapOf(
                        "A1" to SheetCell("A1", CellValue.Number(25.0)),
                        "B1" to SheetCell("B1", CellValue.Blank, "A1*4"),
                        "C1" to SheetCell("C1", CellValue.Blank, "SUM(A1:B1)"),
                        "D1" to SheetCell("D1", CellValue.Blank, "D1")
                    )
                )
            )
        )
        val bytes = CleanRoomPoiEngine.writeXlsx(wb)
        val parsed = CleanRoomPoiEngine.readXlsx(bytes)
        val out = CleanRoomPoiEngine.evaluateWorkbook(parsed).getValue("Sheet1")
        assertEquals(CellValue.Number(100.0), out["B1"])
        assertEquals(CellValue.Number(125.0), out["C1"])
        assertTrue(out["D1"] is CellValue.Error)
    }
}
