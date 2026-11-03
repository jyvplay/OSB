// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.calc.SheetViewModel
import dev.fusion.editor.CopyDestinationContract
import dev.fusion.editor.CopyPickerOutcome
import dev.fusion.editor.CopyPickerRequest
import dev.fusion.editor.CopyPickerResult
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.FormulaXlsx
import dev.fusion.ooxml.OfficeSheetCodec
import dev.fusion.ooxml.OfficeSheetDraft
import dev.fusion.ooxml.OfficeSheetTextGrid
import dev.fusion.ooxml.OfficeSheetRangeEdit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Real consent, undo and MediaStore bytes on a disposable AVD; no system-picker or Office claim. */
@RunWith(AndroidJUnit4::class)
class R59SheetDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun sheet(): SheetViewModel {
        lateinit var model: SheetViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[SheetViewModel::class.java] }
        return model
    }
    private fun export(): OfficeExportViewModel {
        lateinit var model: OfficeExportViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[OfficeExportViewModel::class.java] }
        return model
    }
    private fun click(tag: String) = NativeVisualEvidence.revealAndClick(compose, tag)
    private fun typedReview() {
        click("sheet-text-grid")
        compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement("12.5\t0012\n\"42\"\t=1+1")
        val toggle = compose.onNodeWithTag("sheet-text-grid-decimals").performScrollTo()
        NativeVisualEvidence.assertMinimumTarget(compose, "sheet-text-grid-decimals")
        toggle.assertIsOff().assertHasClickAction().performClick().assertIsOn()
        click("sheet-text-grid-preview")
        compose.waitUntil(15_000) { sheet().state.value.textGridPreview != null }
        compose.onNodeWithText("1 numeric, 3 text and 0 blank fields", substring = true).assertExists()
        assertEquals(OfficeSheetTextGrid.Mode.DECIMALS, sheet().state.value.textGridPreview?.mode)
    }
    private fun destination(body: (Uri) -> Unit) {
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "fusion-r59-${UUID.randomUUID()}.xlsx")
            put(MediaStore.MediaColumns.MIME_TYPE, CopyDestinationContract.XLSX_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FusionR59")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try { requireNotNull(resolver.openOutputStream(uri, "wt")).use { }; body(uri) }
        finally { resolver.delete(uri, null, null) }
    }

    @Test fun blankSkipAndExistingCellProtectionAreReviewedAndUndoable() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-spreadsheet") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-spreadsheet")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("window-actions-spreadsheet").performClick()
        compose.onNodeWithText("Maximize").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextReplacement("KEEP")
        click("sheet-apply")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells["A1"]?.input == "KEEP" }
        val before = sheet().state.value.data.draft
        click("sheet-text-grid")
        compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement("replace\tnew")
        compose.onNodeWithTag("sheet-text-grid-protect-existing").performScrollTo().assertIsOff().performClick().assertIsOn()
        NativeVisualEvidence.assertMinimumTarget(compose, "sheet-text-grid-protect-existing")
        click("sheet-text-grid-preview")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.textGridError.isNotEmpty() }
        compose.onNodeWithTag("sheet-text-grid-error").performScrollTo().assertTextContains("Protected existing cell A1", substring = true)
        assertSame("Conflict must not publish a partial grid", before, sheet().state.value.data.draft)
        click("sheet-text-grid-input-cancel")

        fun review() {
            click("sheet-text-grid")
            compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement("\tnew")
            val skip = compose.onNodeWithTag("sheet-text-grid-skip-blanks").performScrollTo()
            NativeVisualEvidence.assertMinimumTarget(compose, "sheet-text-grid-skip-blanks")
            skip.assertIsOff().performClick().assertIsOn()
            val protect = compose.onNodeWithTag("sheet-text-grid-protect-existing").performScrollTo()
            NativeVisualEvidence.assertMinimumTarget(compose, "sheet-text-grid-protect-existing")
            protect.assertIsOff().performClick().assertIsOn()
            click("sheet-text-grid-preview")
            compose.waitUntil(15_000) { sheet().state.value.textGridPreview != null }
            val preview = requireNotNull(sheet().state.value.textGridPreview)
            assertTrue(preview.skipBlanks && preview.protectExisting)
            assertEquals(1, preview.skippedFields)
            assertSame(before, sheet().state.value.data.draft)
            compose.onNodeWithText("1 blank field skipped", substring = true).assertIsDisplayed()
        }
        review()
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r61-sheet-protection", "sheet-text-grid-apply", listOf("sheet-text-grid-cancel"))
        NativeVisualEvidence.assertFullTextVisible(compose, "sheet-text-grid-review-text")
        click("sheet-text-grid-cancel")
        assertSame(before, sheet().state.value.data.draft)
        review() // Each new review must reset both options.
        click("sheet-text-grid-apply")
        compose.waitUntil(15_000) { sheet().state.value.data.draft.revision == before.revision + 1 }
        val after = sheet().state.value.data.draft
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "KEEP"), after.cells["A1"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "new"), after.cells["B1"])
        click("sheet-undo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == before.cells }
        click("sheet-redo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == after.cells }
        val redone = sheet().state.value.data.draft
        val depth = sheet().state.value.undoDepth
        click("sheet-text-grid")
        compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement("\"\"")
        compose.onNodeWithTag("sheet-text-grid-skip-blanks").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("sheet-text-grid-protect-existing").performScrollTo().assertIsOff().performClick().assertIsOn()
        click("sheet-text-grid-preview")
        compose.waitUntil(15_000) { sheet().state.value.textGridPreview != null }
        assertEquals(0, requireNotNull(sheet().state.value.textGridPreview).changed)
        click("sheet-text-grid-apply")
        compose.waitUntil(15_000) { sheet().state.value.textGridPreview == null }
        assertSame("An all-skipped review is a no-op", redone, sheet().state.value.data.draft)
        assertEquals(depth, sheet().state.value.undoDepth)
        destination { uri ->
            val coordinator = export()
            val request = AtomicReference<CopyPickerRequest>()
            compose.runOnUiThread { coordinator.sheet(sheet().state.value.data.draft, emptyList()) { request.set(it) } }
            compose.waitUntil(15_000) { request.get() != null || !coordinator.state.value.busy }
            val issued = requireNotNull(request.get()) { coordinator.state.value.status }
            compose.runOnUiThread { coordinator.complete(CopyPickerResult(issued.requestId, CopyPickerOutcome.SELECTED, uri), emptyList()) }
            compose.waitUntil(30_000) { !coordinator.state.value.busy }
            assertTrue(coordinator.state.value.status, coordinator.state.value.status.contains("byte-for-byte"))
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val inputs = FormulaXlsx.read(bytes).inputs
            assertEquals(FormulaEval.CellInput.TextLiteral("KEEP"), inputs["A1"])
            assertEquals(FormulaEval.CellInput.TextLiteral("new"), inputs["B1"])
        }
    }

    @Test fun literalFillAndClearRangeAreReviewedProtectedUndoableAndExported() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-spreadsheet") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-spreadsheet")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("window-actions-spreadsheet").performClick()
        compose.onNodeWithText("Maximize").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextReplacement("=1+1")
        click("sheet-apply")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells["A1"]?.input == "=1+1" }
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("B1")
        click("sheet-select")
        compose.waitUntil(15_000) { sheet().state.value.selected == "B1" }
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextReplacement("other")
        click("sheet-apply")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells["B1"]?.input == "other" }
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("A1")
        click("sheet-select")
        compose.waitUntil(15_000) { sheet().state.value.selected == "A1" }
        val before = sheet().state.value.data.draft
        click("sheet-range-fill")
        compose.onNodeWithTag("sheet-range-input").performScrollTo().performTextReplacement("A1:B1")
        compose.onNodeWithTag("sheet-range-protect").performScrollTo().assertIsOn()
        NativeVisualEvidence.assertMinimumTarget(compose, "sheet-range-protect")
        click("sheet-range-preview")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.rangeError.isNotEmpty() }
        compose.onNodeWithTag("sheet-range-error").performScrollTo().assertTextContains("Protected existing cell B1", substring = true)
        assertSame(before, sheet().state.value.data.draft)
        click("sheet-range-input-cancel")

        fun reviewFill() {
            click("sheet-range-fill")
            compose.onNodeWithTag("sheet-range-input").performScrollTo().performTextReplacement("A1:B1")
            compose.onNodeWithTag("sheet-range-protect").performScrollTo().assertIsOn().performClick().assertIsOff()
            click("sheet-range-preview")
            compose.waitUntil(15_000) { sheet().state.value.rangePreview != null }
            val plan = requireNotNull(sheet().state.value.rangePreview)
            assertEquals(OfficeSheetRangeEdit.Operation.FILL_LITERAL, plan.operation)
            assertEquals(1, plan.replaced)
            assertFalse(plan.protectExisting)
            assertSame(before, sheet().state.value.data.draft)
            compose.onNodeWithTag("sheet-range-review-text").assertTextContains("protection is off", substring = true)
        }
        reviewFill()
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r62-sheet-range", "sheet-range-apply", listOf("sheet-range-cancel"))
        NativeVisualEvidence.assertFullTextVisible(compose, "sheet-range-review-text")
        click("sheet-range-cancel")
        assertSame(before, sheet().state.value.data.draft)
        reviewFill() // Protection must reset to on each time.
        click("sheet-range-apply")
        compose.waitUntil(15_000) { sheet().state.value.data.draft.revision == before.revision + 1 }
        val filled = sheet().state.value.data.draft
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "=1+1"), filled.cells["B1"])
        click("sheet-undo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == before.cells }
        click("sheet-redo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == filled.cells }
        click("sheet-range-clear")
        compose.onNodeWithTag("sheet-range-input").performScrollTo().performTextReplacement("B1:A1")
        click("sheet-range-preview")
        compose.waitUntil(15_000) { sheet().state.value.rangePreview != null }
        assertEquals("A1:B1", requireNotNull(sheet().state.value.rangePreview).range)
        assertEquals(2, requireNotNull(sheet().state.value.rangePreview).cleared)
        click("sheet-range-apply")
        compose.waitUntil(15_000) { sheet().state.value.data.draft.cells["A1"] == null && sheet().state.value.data.draft.cells["B1"] == null }
        click("sheet-undo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == filled.cells }
        val beforeNoOp = sheet().state.value.data.draft
        val depth = sheet().state.value.undoDepth
        click("sheet-range-clear")
        compose.onNodeWithTag("sheet-range-input").performScrollTo().performTextReplacement("Z100")
        click("sheet-range-preview")
        compose.waitUntil(15_000) { sheet().state.value.rangePreview != null }
        assertEquals(0, requireNotNull(sheet().state.value.rangePreview).changed)
        click("sheet-range-apply")
        compose.waitUntil(15_000) { sheet().state.value.rangePreview == null }
        assertSame(beforeNoOp, sheet().state.value.data.draft)
        assertEquals(depth, sheet().state.value.undoDepth)
        destination { uri ->
            val coordinator = export()
            val request = AtomicReference<CopyPickerRequest>()
            compose.runOnUiThread { coordinator.sheet(sheet().state.value.data.draft, emptyList()) { request.set(it) } }
            compose.waitUntil(15_000) { request.get() != null || !coordinator.state.value.busy }
            val issued = requireNotNull(request.get()) { coordinator.state.value.status }
            compose.runOnUiThread { coordinator.complete(CopyPickerResult(issued.requestId, CopyPickerOutcome.SELECTED, uri), emptyList()) }
            compose.waitUntil(30_000) { !coordinator.state.value.busy }
            assertTrue(coordinator.state.value.status, coordinator.state.value.status.contains("byte-for-byte"))
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val inputs = FormulaXlsx.read(bytes).inputs
            assertEquals(FormulaEval.CellInput.TextLiteral("=1+1"), inputs["A1"])
            assertEquals(FormulaEval.CellInput.TextLiteral("=1+1"), inputs["B1"])
        }
    }

    @Test fun transposeOptInPreviewCancelApplyUndoAndProviderReadback() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-spreadsheet") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-spreadsheet")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("window-actions-spreadsheet").performClick()
        compose.onNodeWithText("Maximize").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("B2")
        click("sheet-select")
        compose.waitUntil(15_000) { sheet().state.value.selected == "B2" }
        val before = sheet().state.value.data.draft
        fun review() {
            click("sheet-text-grid")
            compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement("12.5\t\"42\"\t=1+1\n0012\tword\t")
            compose.onNodeWithTag("sheet-text-grid-decimals").performScrollTo().assertIsOff().performClick().assertIsOn()
            val transpose = compose.onNodeWithTag("sheet-text-grid-transpose").performScrollTo()
            NativeVisualEvidence.assertMinimumTarget(compose, "sheet-text-grid-transpose")
            transpose.assertIsOff().performClick().assertIsOn()
            click("sheet-text-grid-preview")
            compose.waitUntil(15_000) { sheet().state.value.textGridPreview != null }
            compose.onNodeWithText("Rows and columns swapped. 3 rows × 2 columns", substring = true).assertIsDisplayed()
            assertTrue(sheet().state.value.textGridPreview?.transposed == true)
            assertSame(before, sheet().state.value.data.draft)
        }
        review()
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r60-sheet-transpose", "sheet-text-grid-apply", listOf("sheet-text-grid-cancel"))
        click("sheet-text-grid-cancel")
        assertSame("Cancel leaves draft untouched", before, sheet().state.value.data.draft)
        review() // Both options must have reset to off.
        click("sheet-text-grid-apply")
        compose.waitUntil(15_000) { sheet().state.value.data.draft.revision == before.revision + 1 }
        val after = sheet().state.value.data.draft
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.NUMBER, "12.5"), after.cells["B2"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "0012"), after.cells["C2"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "42"), after.cells["B3"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "word"), after.cells["C3"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "=1+1"), after.cells["B4"])
        assertNull(after.cells["C4"])
        click("sheet-undo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == before.cells }
        click("sheet-redo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == after.cells }
        val redone = sheet().state.value.data.draft
        destination { uri ->
            val coordinator = export()
            val request = AtomicReference<CopyPickerRequest>()
            compose.runOnUiThread { coordinator.sheet(redone, emptyList()) { request.set(it) } }
            compose.waitUntil(15_000) { request.get() != null || !coordinator.state.value.busy }
            val issued = requireNotNull(request.get()) { coordinator.state.value.status }
            compose.runOnUiThread { coordinator.complete(CopyPickerResult(issued.requestId, CopyPickerOutcome.SELECTED, uri), emptyList()) }
            compose.waitUntil(30_000) { !coordinator.state.value.busy }
            assertTrue(coordinator.state.value.status, coordinator.state.value.status.contains("byte-for-byte"))
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val inputs = FormulaXlsx.read(bytes).inputs
            assertEquals(FormulaEval.CellInput.NumberLiteral(12.5), inputs["B2"])
            assertEquals(FormulaEval.CellInput.TextLiteral("0012"), inputs["C2"])
            assertEquals(FormulaEval.CellInput.TextLiteral("42"), inputs["B3"])
            assertEquals(FormulaEval.CellInput.TextLiteral("word"), inputs["C3"])
            assertEquals(FormulaEval.CellInput.TextLiteral("=1+1"), inputs["B4"])
            assertNull(inputs["C4"])
        }
    }

    @Test fun optInReviewCancelApplyUndoAndProviderReadback() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-spreadsheet") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-spreadsheet")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("window-actions-spreadsheet").performClick()
        compose.onNodeWithText("Maximize").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("C1")
        click("sheet-select")
        compose.waitUntil(15_000) { sheet().state.value.selected == "C1" }
        click("sheet-kind")
        compose.onNodeWithText("Formula").performClick()
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextInput("=A1+1")
        click("sheet-apply")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells["C1"]?.input == "=A1+1" }
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("A1")
        click("sheet-select")
        compose.waitUntil(15_000) { sheet().state.value.selected == "A1" }
        val before = sheet().state.value.data.draft
        typedReview()
        assertSame("Preview must not publish the draft", before, sheet().state.value.data.draft)
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r59-typed-grid", "sheet-text-grid-apply", listOf("sheet-text-grid-cancel"))
        click("sheet-text-grid-cancel")
        assertSame("Cancel changes no cells", before, sheet().state.value.data.draft)
        // Reopening must start off, even though the preceding review opted in.
        typedReview()
        click("sheet-text-grid-apply")
        compose.waitUntil(15_000) { sheet().state.value.data.draft.revision == before.revision + 1 }
        val after = sheet().state.value.data.draft
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.NUMBER, "12.5"), after.cells["A1"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "0012"), after.cells["B1"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "42"), after.cells["A2"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "=1+1"), after.cells["B2"])
        assertEquals(FormulaEval.CellValue.Num(13.5), sheet().state.value.data.values["C1"])
        click("sheet-undo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == before.cells }
        click("sheet-redo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == after.cells }
        val redone = sheet().state.value.data.draft
        assertTrue(runBlocking { sheet().flush() })
        assertEquals(redone, AtomicFile(File(context.noBackupFilesDir, "sheet-draft-v1.bin")).openRead().use(OfficeSheetCodec::read))
        destination { uri ->
            val coordinator = export()
            val request = AtomicReference<CopyPickerRequest>()
            compose.runOnUiThread { coordinator.sheet(redone, emptyList()) { request.set(it) } }
            compose.waitUntil(15_000) { request.get() != null || !coordinator.state.value.busy }
            val issued = requireNotNull(request.get()) { coordinator.state.value.status }
            compose.runOnUiThread { coordinator.complete(CopyPickerResult(issued.requestId, CopyPickerOutcome.SELECTED, uri), emptyList()) }
            compose.waitUntil(30_000) { !coordinator.state.value.busy }
            assertTrue(coordinator.state.value.status, coordinator.state.value.status.contains("byte-for-byte"))
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val inputs = FormulaXlsx.read(bytes).inputs
            assertEquals(FormulaEval.CellInput.NumberLiteral(12.5), inputs["A1"])
            assertEquals(FormulaEval.CellInput.TextLiteral("0012"), inputs["B1"])
            assertEquals(FormulaEval.CellInput.TextLiteral("42"), inputs["A2"])
            assertEquals(FormulaEval.CellInput.TextLiteral("=1+1"), inputs["B2"])
            assertEquals(FormulaEval.CellInput.Formula("A1+1"), inputs["C1"])
        }
    }
}
