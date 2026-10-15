package dev.fusion.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Real MainActivity entry, consent, local persistence and recreation. No reminder claimed. */
class R17TaskUiDeviceTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private fun open() {
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("shelf-automation") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("shelf-automation").performScrollTo().performClick()
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-mode").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun addCompleteAndReopenPersistedTaskThroughActualShell() {
        open()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextInput("R17 local task")
        compose.onNodeWithTag("tasks-date").performScrollTo().performTextInput("2028-02-29")
        compose.onNodeWithTag("tasks-title").assertTextContains("R17 local task")
        compose.onNodeWithTag("tasks-date").assertTextContains("2028-02-29")
        compose.onNodeWithTag("tasks-add").performScrollTo().assertIsEnabled().performClick()
        try { compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Completed: R17 local task").fetchSemanticsNodes().isNotEmpty()} }
        catch(e:Exception) {
            val context=compose.activity
            val disk=runCatching {dev.fusion.automation.TaskRepository(java.io.File(context.noBackupFilesDir,"tasks-v1.bin")).load()}
                .fold({"revision=${it.revision}, tasks=${it.tasks}"},{"${it.javaClass.simpleName}: ${it.message}"})
            throw AssertionError("Task UI observation failed; actual disk=$disk; status="+
                compose.onNodeWithTag("tasks-status").printToString()+"; semantics="+compose.onRoot().printToString().takeLast(1600),e)
        }
        compose.onNodeWithContentDescription("Completed: R17 local task").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Loaded/saved revision 2. No reminder scheduled.").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.recreate();open()
        compose.onNodeWithContentDescription("Completed: R17 local task").performScrollTo().assertIsOn()
        compose.onNodeWithText("Date only: 2028-02-29").assertExists()
    }
}
