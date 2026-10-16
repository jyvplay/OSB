package dev.fusion.shell
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.automation.TaskRepository
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
        compose.waitUntil(10000) {compose.onAllNodesWithTag("tasks-status").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(0L,TaskRepository(File(compose.activity.noBackupFilesDir,"tasks-v1.bin")).load().revision)
        compose.onNodeWithTag("tasks-undo").assertDoesNotExist()
    }
}
