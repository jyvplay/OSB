// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.documents.TextBytes
import dev.fusion.documents.TextDocument
import dev.fusion.editor.CopyDestinationContract
import dev.fusion.editor.CopyPickerOutcome
import dev.fusion.editor.CopyPickerRequest
import dev.fusion.editor.CopyPickerResult
import dev.fusion.ooxml.OfficeSheetDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/** Authored for a disposable AVD: the UI case uses DocumentsUI; direct provider cases inject callbacks. */
@RunWith(AndroidJUnit4::class)
class R54OfficeDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun openModule(module: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-$module") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shelf-$module").performScrollTo().performClick()
    }
    private fun maximize(module: String) {
        compose.onNodeWithTag("window-actions-$module").performClick()
        compose.onNodeWithText("Maximize").performClick()
    }
    private fun hideIme() {
        compose.activityRule.scenario.onActivity { activity ->
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
    }
    private fun chooseSheetKind(kind: String) {
        compose.onNodeWithTag("sheet-kind").performScrollTo().assertIsEnabled().assertIsDisplayed().performClick()
        // Observe the actual popup instead of assuming the tap instantly created its nodes.
        compose.waitUntil(5_000) { compose.onAllNodesWithText(kind).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(kind).performClick()
    }
    private fun awaitSelectedCell(address: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("sheet-selected-value") and hasText("Selected $address", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun sheetEditsRecalculatesKeepsUnappliedInputAndCapturesActualUi() {
        openModule("spreadsheet")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        maximize("spreadsheet")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        chooseSheetKind("Number")
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextInput("4")
        compose.onNodeWithTag("sheet-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-selected-value") and hasText("displayed value: 4", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // Wrapped Clear is reachable without horizontally scrolling the action row, even with IME state retained.
        compose.onNodeWithTag("sheet-clear").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("B1")
        compose.onNodeWithTag("sheet-select").performScrollTo().performClick()
        awaitSelectedCell("B1")
        chooseSheetKind("Formula")
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextInput("=A1*2")
        compose.onNodeWithTag("sheet-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-selected-value") and hasText("displayed value: 8", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-apply").performScrollTo().assertIsDisplayed()
        hideIme()
        compose.onNodeWithTag("sheet-export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("sheet-apply").performScrollTo().assertIsDisplayed()
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-sheet", listOf("sheet-export", "sheet-apply"))
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextReplacement("=A1*3")
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("C1")
        compose.onNodeWithTag("sheet-select").performScrollTo().performClick()
        compose.onNodeWithText("Discard unapplied cell input?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("sheet-input").performScrollTo().assertTextContains("=A1*3")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-input").performScrollTo().assertTextContains("=A1*3")
        compose.onNodeWithTag("sheet-selected-value").assertTextContains("displayed value: 8", substring = true)
        // A bare reference to a truly empty cell is a numeric zero when stored as a formula.
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextReplacement("=A3")
        compose.onNodeWithTag("sheet-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-selected-value") and hasText("displayed value: 0", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sheet-address").performScrollTo().performTextReplacement("C1")
        compose.onNodeWithTag("sheet-select").performScrollTo().performClick()
        awaitSelectedCell("C1")
        chooseSheetKind("Formula")
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextInput("=COUNT(B1)")
        compose.onNodeWithTag("sheet-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-selected-value") and hasText("displayed value: 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        // ROUND was formerly refused in this editor; assert the actual typed result after applying.
        compose.onNodeWithTag("sheet-input").performScrollTo().performTextReplacement("=ROUND(1.005,2)")
        compose.onNodeWithTag("sheet-apply").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("sheet-selected-value") and hasText("displayed value: 1.01", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        hideIme()
        compose.onNodeWithTag("sheet-apply").performScrollTo().assertIsDisplayed()
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-round", listOf("sheet-apply"))
    }

    @Test fun writerExportDisclosureIsVisibleInRealEditor() {
        openModule("editor")
        maximize("editor")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editor-content").performScrollTo().performTextInput("Writer test\nSecond line")
        hideIme()
        compose.onNodeWithTag("writer-export-docx").performScrollTo().performClick()
        compose.onNodeWithText("Export plain text as DOCX?").assertIsDisplayed()
        compose.onNodeWithText("No rich formatting", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("Writer test", substring = true)
        hideIme()
        compose.onNodeWithTag("writer-export-docx").performScrollTo().assertIsDisplayed()
        NativeVisualEvidence.capture(compose, compose.activity, "r24c-writer", listOf("writer-export-docx"))

        // Unlike the provider tests below, exercise the real system Create Document picker and its result grants.
        val vm = exportModel()
        compose.onNodeWithTag("writer-export-docx").performClick()
        compose.onNodeWithText("Choose destination").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val documentsUi = By.pkg(Pattern.compile(".*documentsui"))
        if (!device.wait(Until.hasObject(documentsUi), 15_000)) {
            val actions = device.findObjects(By.clickable(true)).take(15).map {
                "${it.className} id=${it.resourceName} text=${it.text.orEmpty().take(60)} desc=${it.contentDescription.orEmpty().take(80)}"
            }
            throw AssertionError("Writer picker not observed; foreground=${device.currentPackageName}; export=${vm.state.value}; actions=$actions")
        }
        val filename = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        filename.text = "fusion-r54-${UUID.randomUUID()}.docx"
        requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))), 10_000)).click()
        assertTrue(device.wait(Until.gone(documentsUi), 15_000))
        compose.waitUntil(30_000) { !vm.state.value.busy && vm.state.value.status.isNotEmpty() }
        assertTrue(vm.state.value.status, vm.state.value.status.contains("byte-for-byte"))
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("Writer test", substring = true)
    }

    @Test fun structuredWriterOptionUsesRealEditorAndSystemPicker() {
        openModule("editor")
        maximize("editor")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val text = "# Heading\n- **bold** and *italic*\n> `code`"
        compose.onNodeWithTag("editor-content").performScrollTo().performTextInput(text)
        hideIme()
        compose.onNodeWithTag("writer-export-docx").performScrollTo().performClick()
        compose.onNodeWithTag("writer-structured-option").performScrollTo().performClick()
        compose.onNodeWithText("Export formatted DOCX copy?").assertIsDisplayed()
        compose.onNodeWithText("Not a rich editor or full Markdown", substring = true).assertExists()
        compose.onNodeWithTag("writer-structured-confirm").assertIsDisplayed()
        // Preserve an actual diagnostic image even if the independent text-fit assertion fails.
        NativeVisualEvidence.captureDialogDisplay(compose, compose.activity, "r24c-structured", "writer-structured-confirm")
        NativeVisualEvidence.assertFullTextVisible(compose, "writer-structured-disclosure")
        val vm = exportModel()
        compose.onNodeWithTag("writer-structured-confirm").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val documentsUi = By.pkg(Pattern.compile(".*documentsui"))
        assertTrue("Actual Create Document picker required", device.wait(Until.hasObject(documentsUi), 15_000))
        val filename = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
        filename.text = "fusion-r57-${UUID.randomUUID()}.docx"
        requireNotNull(device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))), 10_000)).click()
        assertTrue(device.wait(Until.gone(documentsUi), 15_000))
        compose.waitUntil(30_000) { !vm.state.value.busy && vm.state.value.status.isNotEmpty() }
        assertTrue(vm.state.value.status, vm.state.value.status.contains("byte-for-byte"))
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains(text, substring = true)
    }

    private fun created(extension: String, mime: String, materialize: Boolean = true, body: (Uri) -> Unit) {
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "fusion-r54-${UUID.randomUUID()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FusionR54")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        try {
            // MediaStore can insert a pending row without a readable file. Materialize an
            // actual empty file for the normal cases; the separate negative case omits this.
            if (materialize) requireNotNull(resolver.openOutputStream(uri, "wt")).use { }
            body(uri)
        } finally { resolver.delete(uri, null, null) }
    }
    private fun read(uri: Uri): ByteArray = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
    private fun zipPart(uri: Uri, name: String): String {
        ZipInputStream(requireNotNull(resolver.openInputStream(uri))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("Missing $name")
    }
    private fun exportModel(): OfficeExportViewModel {
        lateinit var vm: OfficeExportViewModel
        compose.activityRule.scenario.onActivity { vm = ViewModelProvider(it)[OfficeExportViewModel::class.java] }
        return vm
    }
    private fun beginWriter(vm: OfficeExportViewModel, doc: TextDocument, forbidden: List<String>): CopyPickerRequest {
        val request = AtomicReference<CopyPickerRequest>()
        compose.runOnUiThread { vm.writer(doc, forbidden) { request.set(it) } }
        compose.waitUntil(15_000) { request.get() != null || !vm.state.value.busy }
        return requireNotNull(request.get()) { vm.state.value.status }
    }
    private fun deliver(vm: OfficeExportViewModel, request: CopyPickerRequest, uri: Uri?, sources: List<String> = emptyList()) {
        val result = CopyPickerResult(request.requestId, if (uri == null) CopyPickerOutcome.CANCELED else CopyPickerOutcome.SELECTED, uri)
        compose.runOnUiThread { vm.complete(result, sources) }
    }

    @Test fun realProviderRefusesAliasAndStaleResultCannotAuthorizeNewExport() {
        created("txt", "text/plain") { original ->
            resolver.openOutputStream(original, "wt")!!.use { it.write("original bytes".toByteArray()) }
            created("docx", CopyDestinationContract.DOCX_MIME) { staleDestination ->
                created("docx", CopyDestinationContract.DOCX_MIME) { destination ->
                    val vm = exportModel()
                    val doc = TextDocument(UUID.randomUUID().toString(), "writer.txt", original.toString(), "first\tline\nsecond", TextBytes.digest("first\tline\nsecond"))
                    val alias = beginWriter(vm, doc, listOf(original.toString()))
                    assertEquals(CopyDestinationContract.DOCX_MIME, alias.mimeType)
                    deliver(vm, alias, original)
                    compose.waitUntil(15_000) { !vm.state.value.busy }
                    assertEquals("original bytes", read(original).toString(Charsets.UTF_8))
                    assertTrue(vm.state.value.status.contains("unverified", ignoreCase = true))
                    val old = beginWriter(vm, doc, listOf(original.toString()))
                    deliver(vm, old, null)
                    val current = beginWriter(vm, doc, listOf(original.toString()))
                    deliver(vm, old, staleDestination)
                    assertTrue(vm.state.value.busy)
                    assertEquals(0, read(staleDestination).size)
                    deliver(vm, current, destination, listOf(original.toString()))
                    compose.waitUntil(15_000) { !vm.state.value.busy }
                    assertTrue(vm.state.value.status, vm.state.value.status.contains("byte-for-byte"))
                    val xml = zipPart(destination, "word/document.xml")
                    assertTrue(xml.contains("first") && xml.contains("<w:tab/>") && xml.contains("second"))
                    assertEquals("original bytes", read(original).toString(Charsets.UTF_8))
                }
            }
        }
    }

    @Test fun structuredWriterRealProviderReadbackPreservesSource() {
        created("txt", "text/plain") { original ->
            val source = "# Heading\n- **bold** and *italic*\n> `code`"
            resolver.openOutputStream(original, "wt")!!.use { it.write(source.toByteArray(Charsets.UTF_8)) }
            created("docx", CopyDestinationContract.DOCX_MIME) { destination ->
                val vm = exportModel()
                val doc = TextDocument(UUID.randomUUID().toString(), "writer.txt", original.toString(), source, TextBytes.digest(source))
                val request = AtomicReference<CopyPickerRequest>()
                compose.runOnUiThread { vm.structuredWriter(doc, listOf(original.toString())) { request.set(it) } }
                compose.waitUntil(15_000) { request.get() != null || !vm.state.value.busy }
                val ready = requireNotNull(request.get()) { vm.state.value.status }
                assertEquals(CopyDestinationContract.DOCX_MIME, ready.mimeType)
                deliver(vm, ready, destination, listOf(original.toString()))
                compose.waitUntil(15_000) { !vm.state.value.busy }
                assertTrue(vm.state.value.status, vm.state.value.status.contains("byte-for-byte"))
                val xml = zipPart(destination, "word/document.xml")
                assertTrue(xml.contains("<w:pStyle w:val=\"Heading1\"/>") && xml.contains("<w:numPr>") && xml.contains("<w:b/>") && xml.contains("<w:i/>") && xml.contains("monospace"))
                val styles = zipPart(destination, "word/styles.xml")
                assertTrue(styles.contains("<w:sz w:val=\"32\"/>") && styles.contains("<w:outlineLvl w:val=\"0\"/>"))
                assertArrayEquals(source.toByteArray(Charsets.UTF_8), read(original))
            }
        }
    }

    @Test fun realProviderRefusesAnUnrelatedNonemptyDestination() {
        created("docx", CopyDestinationContract.DOCX_MIME) { destination ->
            resolver.openOutputStream(destination, "wt")!!.use { it.write("keep these bytes".toByteArray()) }
            val vm = exportModel()
            val doc = TextDocument(UUID.randomUUID().toString(), "writer.txt", null, "new draft", TextBytes.digest("new draft"))
            val request = beginWriter(vm, doc, emptyList())
            deliver(vm, request, destination)
            compose.waitUntil(15_000) { !vm.state.value.busy }
            assertTrue(vm.state.value.status.contains("nonempty", ignoreCase = true))
            assertEquals("keep these bytes", read(destination).toString(Charsets.UTF_8))
        }
    }

    @Test fun unreadableProviderDestinationFailsClosed() {
        created("docx", CopyDestinationContract.DOCX_MIME, materialize = false) { destination ->
            // On this provider a pending row is not a readable file until it is opened for writing.
            assertTrue(runCatching { read(destination) }.isFailure)
            val vm = exportModel()
            val doc = TextDocument(UUID.randomUUID().toString(), "writer.txt", null, "new draft", TextBytes.digest("new draft"))
            val request = beginWriter(vm, doc, emptyList())
            deliver(vm, request, destination)
            compose.waitUntil(15_000) { !vm.state.value.busy }
            assertTrue(vm.state.value.status, vm.state.value.status.contains("unverified", ignoreCase = true))
            assertTrue(runCatching { read(destination) }.isFailure)
        }
    }

    @Test fun unavailableSourceIdentitiesRefuseBeforeOpeningDestination() {
        created("docx", CopyDestinationContract.DOCX_MIME) { destination ->
            val vm = exportModel()
            val doc = TextDocument(UUID.randomUUID().toString(), "writer.txt", null, "new draft", TextBytes.digest("new draft"))
            val request = beginWriter(vm, doc, emptyList())
            compose.runOnUiThread {
                vm.complete(CopyPickerResult(request.requestId, CopyPickerOutcome.SELECTED, destination), emptyList(), sourcesKnown = false)
            }
            assertFalse(vm.state.value.busy)
            assertTrue(vm.state.value.status.contains("identities are unavailable"))
            assertEquals(0, read(destination).size)
            // The consumed result cannot later be retried against the same frozen attempt.
            deliver(vm, request, destination)
            assertEquals(0, read(destination).size)
        }
    }

    @Test fun realProviderWritesAndReadsFormulaWorkbook() {
        created("xlsx", CopyDestinationContract.XLSX_MIME) { destination ->
            val vm = exportModel()
            var draft = OfficeSheetDraft.empty()
            draft = OfficeSheetDraft.replace(draft, "A1", OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.NUMBER, "7")).draft
            draft = OfficeSheetDraft.replace(draft, "B1", OfficeSheetDraft.Cell(OfficeSheetDraft.Kind.FORMULA, "=A1*2")).draft
            val frozen = draft
            val request = AtomicReference<CopyPickerRequest>()
            compose.runOnUiThread { vm.sheet(frozen, emptyList()) { request.set(it) } }
            compose.waitUntil(15_000) { request.get() != null || !vm.state.value.busy }
            val ready = requireNotNull(request.get()) { vm.state.value.status }
            assertEquals(CopyDestinationContract.XLSX_MIME, ready.mimeType)
            deliver(vm, ready, destination)
            compose.waitUntil(15_000) { !vm.state.value.busy }
            assertTrue(vm.state.value.status, vm.state.value.status.contains("byte-for-byte"))
            val xml = zipPart(destination, "xl/worksheets/sheet1.xml")
            assertTrue(xml.contains("<f>A1*2</f>") && xml.contains("<v>14</v>"))
        }
    }
}
