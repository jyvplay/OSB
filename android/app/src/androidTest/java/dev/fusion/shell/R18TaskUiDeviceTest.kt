package dev.fusion.shell
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
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
    private fun nativeText(tag:String, expectedBefore:String, value:String) {
        // TextActions.performTextInput hit SnapshotStateObserver from the instrumentation thread while IME layout changed.
        // Retain the real UI scroll, but enter text through Android's actual accessibility action on its UI thread.
        compose.onNodeWithTag(tag).performScrollTo()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var observation="No native root"
        fun find():AccessibilityNodeInfo? {
            val root=instrumentation.uiAutomation.rootInActiveWindow ?: return null
            val queue=java.util.ArrayDeque<AccessibilityNodeInfo>();queue.add(root);var count=0
            val editables=mutableListOf<String>()
            while(queue.isNotEmpty()) {
                val node=queue.removeFirst();check(++count<=4000)
                if(node.isEditable && editables.size<12) editables.add("${node.viewIdResourceName}:${node.isVisibleToUser}:${node.isEnabled}")
                if((node.viewIdResourceName==tag || node.viewIdResourceName?.endsWith(":id/$tag")==true) && node.refresh() && node.isVisibleToUser && node.isEditable && node.isEnabled) return node
                for(i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
            observation="root=${root.packageName}/${root.className}; nodes=$count; editable=$editables"
            return null
        }
        // Accessibility publication is asynchronous on its first connection. Wait for an actual node, not a guessed sleep or repeated action.
        val deadline=SystemClock.uptimeMillis()+10000
        fun await(value:String?=null):AccessibilityNodeInfo {
            do {
                val node=find()
                if(node!=null && (value==null || node.text?.toString().orEmpty()==value)) return node
                SystemClock.sleep(50)
            } while(SystemClock.uptimeMillis()<deadline)
            throw AssertionError("Actual native editable $tag not published/updated: $observation")
        }
        val node=await()
        assertEquals("Unexpected pre-edit content of $tag",expectedBefore,node.text?.toString().orEmpty())
        assertTrue("Actual native text action refused for $tag",node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
        }))
        instrumentation.waitForIdleSync();compose.waitForIdle()
        assertEquals("Actual native text action not observed for $tag",value,await(value).text?.toString().orEmpty())
    }
    private fun saved(revision:Int) {
        compose.waitUntil(10000) {compose.onAllNodesWithText("Loaded/saved revision $revision. No reminder scheduled.").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun editCancelArchiveRecreateRestoreAndQueryThroughRealShell() {
        var phase="open/add"
        try {
        open()
        nativeText("tasks-title", "", "Original task")
        compose.onNodeWithTag("tasks-add").performScrollTo().assertIsEnabled().performClick();saved(1)
        phase="edit/cancel or save"
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        nativeText("tasks-title", "Original task", "Cancelled change")
        compose.onNodeWithTag("tasks-cancel-edit").performScrollTo().performClick()
        compose.onNodeWithText("Original task").assertExists();saved(1)
        phase="edit/cancel or save"
        compose.onNodeWithText("Edit task").performScrollTo().performClick()
        nativeText("tasks-title", "Original task", "Renamed task")
        nativeText("tasks-date", "", "2028-02-29")
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
        nativeText("tasks-query", "", "RENAMED")
        compose.onNodeWithText("Renamed task").assertExists()
        nativeText("tasks-query", "RENAMED", "missing")
        compose.onNodeWithText("Renamed task").assertDoesNotExist()
        nativeText("tasks-query", "missing", "")
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
