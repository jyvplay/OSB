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
