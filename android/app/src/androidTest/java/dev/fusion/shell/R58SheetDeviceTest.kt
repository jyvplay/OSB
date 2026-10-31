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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Real UI, recovery file and provider output on an explicitly disposable AVD. */
@RunWith(AndroidJUnit4::class)
class R58SheetDeviceTest {
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
    private fun select(ref: String) {
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement(ref)
        click("sheet-select")
        compose.waitUntil(15_000) { sheet().state.value.selected == ref }
    }
    private fun inputAndReview(raw: String) {
        click("sheet-text-grid")
        compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement(raw)
        click("sheet-text-grid-preview")
        compose.waitUntil(15_000) { sheet().state.value.textGridPreview != null }
    }
    private fun destination(body: (Uri) -> Unit) {
        val resolver = context.contentResolver
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "fusion-r58-${UUID.randomUUID()}.xlsx")
            put(MediaStore.MediaColumns.MIME_TYPE, CopyDestinationContract.XLSX_MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FusionR58")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { }
            body(uri)
        } finally { resolver.delete(uri, null, null) }
    }

    @Test fun textGridReviewCancelApplyUndoRedoCheckpointAndRealProviderReadback() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-spreadsheet") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("shelf-spreadsheet")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("window-actions-spreadsheet").performClick()
        compose.onNodeWithText("Maximize").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        select("B1")
        click("sheet-kind")
        compose.onNodeWithText("Formula").performClick()
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextInput("=1+2")
        click("sheet-apply")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells["B1"]?.input == "=1+2" }
        select("A1")
        val before = sheet().state.value.data.draft
        val raw = "first\t=2+2\nsecond\t"
        inputAndReview(raw)
        compose.onNodeWithText("2 rows × 2 columns", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 new, 1 replaced, 0 cleared, 1 unchanged", substring = true).assertExists()
        assertSame("Preview must not publish the draft", before, sheet().state.value.data.draft)
        // An attempted programmatic edit while the review is open is rejected too.
        val sheetModel = sheet()
        compose.runOnUiThread { sheetModel.updateInput("must not replace pending input") }
        assertFalse(sheet().state.value.unapplied)
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r58-text-grid", "sheet-text-grid-apply")
        click("sheet-text-grid-cancel")
        assertSame("Cancel must preserve original draft", before, sheet().state.value.data.draft)
        inputAndReview(raw)
        click("sheet-text-grid-apply")
        compose.waitUntil(15_000) { sheet().state.value.data.draft.revision == before.revision + 1 }
        val pasted = sheet().state.value.data.draft
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "first"), pasted.cells["A1"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "=2+2"), pasted.cells["B1"])
        assertEquals(OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.TEXT, "second"), pasted.cells["A2"])
        assertNull(pasted.cells["B2"])
        click("sheet-undo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == before.cells }
        click("sheet-redo")
        compose.waitUntil(15_000) { !sheet().state.value.busy && sheet().state.value.data.draft.cells == pasted.cells }
        val afterRedo = sheet().state.value.data.draft

        // A malformed rectangle must leave the whole draft unchanged.
        click("sheet-text-grid")
        compose.onNodeWithTag("sheet-text-grid-input").performScrollTo().performTextReplacement("x\ty\nshort")
        click("sheet-text-grid-preview")
        compose.waitUntil(15_000) { sheet().state.value.textGridError.isNotEmpty() && !sheet().state.value.busy }
        compose.onNodeWithTag("sheet-text-grid-error").performScrollTo().assertTextContains("refused", substring = true)
        assertSame(afterRedo, sheet().state.value.data.draft)
        compose.onNodeWithText("Cancel").performClick()

        // A real private AtomicFile checkpoint is inspected; this is not a process-death test.
        assertTrue(runBlocking { sheet().flush() })
        val recovered = AtomicFile(File(context.noBackupFilesDir, "sheet-draft-v1.bin")).openRead().use(OfficeSheetCodec::read)
        assertEquals(afterRedo, recovered)

        // Direct result delivery to a real MediaStore provider; this is NOT a system-picker test.
        destination { uri ->
            val coordinator = export()
            val request = AtomicReference<CopyPickerRequest>()
            compose.runOnUiThread { coordinator.sheet(afterRedo, emptyList()) { request.set(it) } }
            compose.waitUntil(15_000) { request.get() != null || !coordinator.state.value.busy }
            val issued = requireNotNull(request.get()) { coordinator.state.value.status }
            compose.runOnUiThread { coordinator.complete(CopyPickerResult(issued.requestId, CopyPickerOutcome.SELECTED, uri), emptyList()) }
            compose.waitUntil(30_000) { !coordinator.state.value.busy }
            assertTrue(coordinator.state.value.status, coordinator.state.value.status.contains("byte-for-byte"))
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            val workbook = FormulaXlsx.read(bytes)
            assertEquals(FormulaEval.CellInput.TextLiteral("=2+2"), workbook.inputs["B1"])
            assertEquals(FormulaEval.CellInput.TextLiteral("first"), workbook.inputs["A1"])
        }
    }
}
