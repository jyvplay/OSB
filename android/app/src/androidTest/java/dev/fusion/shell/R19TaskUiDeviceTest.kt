package dev.fusion.shell
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.automation.*
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.util.regex.Pattern

class R19TaskUiDeviceTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private fun open() {
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("shelf-automation") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("shelf-automation").performScrollTo().performClick()
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-mode").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun saved(r:Int) {compose.waitUntil(10000) {compose.onAllNodesWithText("Loaded/saved revision $r. No reminder scheduled.").fetchSemanticsNodes().isNotEmpty()}}
    @Test fun editUndoUsesRealTouchAndRecreationDoesNotInventAnUndoToken() {
        try {
            open()
            compose.onNodeWithTag("tasks-title").performScrollTo().performTextInput("R19 original")
            compose.onNodeWithTag("tasks-date").performScrollTo().performTextInput("2028-02-29")
            compose.onNodeWithTag("tasks-add").performScrollTo().assertIsDisplayed().performClick();saved(1)
            compose.onNodeWithText("Edit task").performScrollTo().performClick()
            compose.onNodeWithTag("tasks-title").performScrollTo().performTextReplacement("R19 changed")
            compose.onNodeWithTag("tasks-date").performScrollTo().performTextClearance()
            compose.onNodeWithTag("tasks-add").performScrollTo().assertIsDisplayed().performClick();saved(2)
            // Patch attack: a no-op edit must not erase the previous meaningful edit's undo.
            compose.onNodeWithText("Edit task").performScrollTo().performClick()
            compose.onNodeWithTag("tasks-add").performScrollTo().assertIsDisplayed().performClick();saved(2)
            // Same revision is not completion evidence for a no-op; observe the actual enabled undo control.
            compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-undo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("tasks-cancel-edit").assertDoesNotExist()
            compose.onNodeWithTag("tasks-undo").performScrollTo().assertIsDisplayed().performClick();saved(3)
            compose.onNodeWithText("R19 original").assertExists();compose.onNodeWithText("Date only: 2028-02-29").assertExists()
            compose.onNodeWithTag("tasks-undo").assertDoesNotExist()
            compose.activityRule.scenario.recreate();open();saved(3)
            compose.onNodeWithText("R19 original").assertExists();compose.onNodeWithTag("tasks-undo").assertDoesNotExist()
        } catch(failure:Throwable) {throw TaskUiEvidence.failure(compose.activity,"R19 edit/undo/recreate",failure)}
    }
    @Test fun actualSystemCreateDocumentPickerCanBeCancelledWithoutTaskMutation() {
        open()
        compose.onNodeWithTag("tasks-export").performScrollTo().assertIsDisplayed().performClick()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Actual DocumentsUI did not appear",device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
        device.pressBack()
        assertTrue("Picker did not leave the foreground",device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
        assertEquals(compose.activity.packageName,device.currentPackageName)
        compose.waitUntil(10000) {compose.onAllNodesWithTag("tasks-status").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(0L,TaskRepository(File(compose.activity.noBackupFilesDir,"tasks-v1.bin")).load().revision)
        compose.onNodeWithTag("tasks-undo").assertDoesNotExist()
    }
    @Test fun actualPickerImportConfirmExportAndUndoPreserveSource() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val resolver=context.contentResolver
        val seedName="r19-seed-${UUID.randomUUID()}.fts"
        val reportName="r19-copy-${UUID.randomUUID()}.fts"
        val source=TaskBook(123,listOf(LocalTask(UUID.randomUUID().toString(),"Picker imported task",java.time.LocalDate.of(2028,2,29))))
        val bytes=TaskFrame.encode(source)
        val uri=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,seedName);put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }))
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());var phase="seed real provider"
        fun picker() {assertTrue("DocumentsUI not visible",device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))}
        fun app() {
            assertTrue("DocumentsUI did not finish",device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
            assertEquals(context.packageName,device.currentPackageName)
        }
        fun selectFile(name:String) {
            val file=device.wait(Until.findObject(By.text(name)),10000)
            assertNotNull("File not visible in actual picker: $name; package=${device.currentPackageName}",file)
            file.click();app()
        }
        try {
            resolver.openOutputStream(uri,"wt")!!.use {it.write(bytes)}
            resolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            open();phase="pick import"
            compose.onNodeWithTag("tasks-import").performScrollTo().assertIsDisplayed().performClick();picker();selectFile(seedName)
            compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-confirm-merge") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            assertEquals(0L,TaskRepository(File(context.noBackupFilesDir,"tasks-v1.bin")).load().revision)
            phase="confirm merge"
            compose.onNodeWithTag("tasks-confirm-merge").performScrollTo().assertIsDisplayed().performClick();saved(1)
            compose.onNodeWithText("Picker imported task").assertExists()
            phase="save through actual picker"
            compose.onNodeWithTag("tasks-export").performScrollTo().assertIsDisplayed().performClick();picker()
            val name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000)
            assertNotNull("Filename field missing",name);name.text=reportName
            val save=device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))),10000)
            assertNotNull("System Save button missing",save);save.click();app()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Export readback verified: revision 1",substring=true).fetchSemanticsNodes().isNotEmpty()}
            phase="reopen actual exported copy"
            compose.onNodeWithTag("tasks-import").performScrollTo().assertIsDisplayed().performClick();picker();selectFile(reportName)
            compose.waitUntil(10000) {compose.onAllNodesWithText("Preview: 0 new; 1 identical.",substring=true).fetchSemanticsNodes().isNotEmpty()}
            // R20 patch attack: actual no-op confirmation must preserve the older meaningful import token.
            compose.onNodeWithTag("tasks-confirm-merge").performScrollTo().performClick()
            compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-undo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("tasks-confirm-merge").assertDoesNotExist()
            phase="undo merge after read-only export/preview"
            compose.onNodeWithTag("tasks-undo").performScrollTo().assertIsDisplayed().performClick();saved(2)
            assertTrue(TaskRepository(File(context.noBackupFilesDir,"tasks-v1.bin")).load().tasks.isEmpty())
            assertArrayEquals(bytes,resolver.openInputStream(uri)!!.use {it.readBytes()})
            // The uniquely named UI-created copy remains on this explicitly disposable AVD as an artifact.
            // No guessed URI or name-based deletion of a potentially unrelated document is attempted.
        } catch(failure:Throwable) {throw TaskUiEvidence.failure(compose.activity,"R19 picker: $phase; foreground=${device.currentPackageName}",failure)}
        finally {resolver.delete(uri,null,null)}
    }

}
