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
        compose.waitUntil(10000) {compose.onAllNodesWithText("Loaded/saved revision $revision. No reminder scheduled.").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun editCancelArchiveRecreateRestoreAndQueryThroughRealShell() {
        var phase="open/add"
        try {
        open()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextInput("Original task")
        compose.onNodeWithTag("tasks-add").performScrollTo().assertIsEnabled().performClick();saved(1)
        phase="edit/cancel or save"
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextReplacement("Cancelled change")
        compose.onNodeWithTag("tasks-cancel-edit").performScrollTo().performClick()
        compose.onNodeWithText("Original task").assertExists();saved(1)
        phase="edit/cancel or save"
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-title").performScrollTo().performTextReplacement("Renamed task")
        compose.onNodeWithTag("tasks-date").performScrollTo().performTextInput("2028-02-29")
        compose.onNodeWithTag("tasks-add").performScrollTo().performClick();saved(2)
        phase="archive"
        compose.onNodeWithText("Archive task").performScrollTo().performClick();saved(3)
        compose.onNodeWithText("Renamed task").assertDoesNotExist()
        phase="recreate"
        compose.activityRule.scenario.recreate();open();saved(3)
        phase="select archived"
        compose.onNodeWithTag("tasks-scope-ARCHIVED").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("tasks-scope-ARCHIVED").assertTextContains("Selected: ARCHIVED")
        compose.onNodeWithText("Renamed task").assertExists()
        phase="restore"
        compose.onNodeWithText("Restore task").performScrollTo().performClick();saved(4)
        compose.onNodeWithTag("tasks-scope-ACTIVE").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("tasks-scope-ACTIVE").assertTextContains("Selected: ACTIVE")
        phase="query"
        compose.onNodeWithTag("tasks-query").performScrollTo().performTextInput("RENAMED")
        compose.onNodeWithText("Renamed task").assertExists()
        compose.onNodeWithTag("tasks-query").performTextReplacement("missing")
        compose.onNodeWithText("Renamed task").assertDoesNotExist()
        compose.onNodeWithTag("tasks-query").performTextClearance()
        phase="order"
        compose.onNodeWithTag("tasks-order").performScrollTo().performClick()
        compose.onNodeWithText("Date only: 2028-02-29").assertExists()
        for(scope in listOf("OPEN","COMPLETED","ARCHIVED","ACTIVE")) {
            phase="view scope $scope"
            compose.onNodeWithTag("tasks-scope-$scope").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("tasks-scope-$scope").assertTextContains("Selected: $scope")
            if(scope in listOf("OPEN","ACTIVE")) compose.onNodeWithText("Renamed task").assertExists()
            else compose.onNodeWithText("Renamed task").assertDoesNotExist()
            saved(4) // View changes must not write another task revision.
        }
        } catch(failure:Throwable) {
            // Observe failure only; no wait, retry, altered tap or longer success timeout.
            val status=runCatching {compose.onNodeWithTag("tasks-status").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString {it.text}}.getOrElse {"unavailable"}
            throw TaskUiEvidence.failure(compose.activity,phase,failure,status=status)
        }
    }
}
