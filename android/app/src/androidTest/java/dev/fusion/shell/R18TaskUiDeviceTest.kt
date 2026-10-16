package dev.fusion.shell
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class R18TaskUiDeviceTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    private fun open() {
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("shelf-automation") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("shelf-automation").performScrollTo().performClick()
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-mode").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-add") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun saved(revision:Int) {
        try {compose.waitUntil(10000) {compose.onAllNodesWithText("Loaded/saved revision $revision. No reminder scheduled.").fetchSemanticsNodes().isNotEmpty()}}
        catch(e:Exception) {throw AssertionError("revision=$revision; "+compose.onNodeWithTag("tasks-status").printToString(),e)}
    }
    @Test fun editCancelArchiveRecreateRestoreAndQueryThroughRealShell() {
        open()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextInput("Original task")
        compose.onNodeWithTag("tasks-add").performScrollTo().assertIsEnabled().performClick();saved(1)
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextReplacement("Cancelled change")
        compose.onNodeWithTag("tasks-cancel-edit").performScrollTo().performClick()
        compose.onNodeWithText("Original task").assertExists();saved(1)
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextReplacement("Renamed task")
        compose.onNodeWithTag("tasks-date").performScrollTo().performTextInput("2028-02-29")
        compose.onNodeWithTag("tasks-add").performScrollTo().performClick();saved(2)
        compose.onNodeWithText("Archive task").performScrollTo().performClick();saved(3)
        compose.onNodeWithText("Renamed task").assertDoesNotExist()
        compose.activityRule.scenario.recreate();open();saved(3)
        compose.onNodeWithTag("tasks-scope-ARCHIVED").performScrollTo().performClick()
        compose.onNodeWithText("Renamed task").assertExists()
        compose.onNodeWithText("Restore task").performScrollTo().performClick();saved(4)
        compose.onNodeWithTag("tasks-scope-ACTIVE").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-query").performScrollTo().performTextInput("RENAMED")
        compose.onNodeWithText("Renamed task").assertExists()
        compose.onNodeWithTag("tasks-query").performTextReplacement("missing")
        compose.onNodeWithText("Renamed task").assertDoesNotExist()
        compose.onNodeWithTag("tasks-query").performTextClearance()
        compose.onNodeWithTag("tasks-order").performScrollTo().performClick()
        compose.onNodeWithText("Date only: 2028-02-29").assertExists()
    }
}
