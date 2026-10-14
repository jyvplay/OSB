package dev.fusion.shell

import android.graphics.Bitmap
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Real native UI captures, not reference images or a visual/accessibility pass. UNRUN. */
@RunWith(AndroidJUnit4::class)
class VisualCaptureTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(DisposableEmulatorRule()).around(compose)

    @Test fun captureNativeSurfaces() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val run = requireNotNull(args.getString("fusionCaptureRun"))
        require(run.matches(Regex("[a-f0-9]{32}")))
        val requestedScale = requireNotNull(args.getString("fusionFontScale")).toFloat()
        require(requestedScale == 1f || requestedScale == 2f)
        val context = instrumentation.targetContext
        val actualScale = context.resources.configuration.fontScale
        check(abs(actualScale - requestedScale) < 0.01f) { "Actual font scale $actualScale != $requestedScale" }
        val directory = File(context.filesDir, "visual-evidence/$run")
        check(!directory.exists() && directory.mkdirs()) { "Capture run must be fresh" }
        File(directory, "configuration.txt").writeText(
            "requestedFontScale=$requestedScale\nactualFontScale=$actualScale\n" +
                "configuration=${context.resources.configuration}\n" +
                "displayMetrics=${context.resources.displayMetrics}\n")

        fun capture(name: String) {
            compose.activityRule.scenario.onActivity { activity ->
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Native screenshot unavailable" }
            try {
                check(bitmap.width > 0 && bitmap.height > 0)
                File(directory, "$name.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
        }
        fun openModule(module: String) {
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasTestTag("shelf-$module") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("shelf-$module").performScrollTo().performClick()
        }
        fun maximize(module: String) {
            compose.onNodeWithTag("window-title-$module", useUnmergedTree=true).assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("window-actions-$module").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Maximize").performClick()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("shelf-editor") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        capture("01-desktop")
        openModule("calculator")
        maximize("calculator")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("calculator-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("calculator-input").performScrollTo().performTextInput("0.1+0.2")
        compose.onNodeWithTag("calculator-evaluate").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("calculator-result") and hasText("0.3")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("calculator-result").performScrollTo().assertTextEquals("0.3")
        capture("02-calculator")
        compose.onNodeWithContentDescription("Close window").performClick()
        openModule("editor")
        maximize("editor")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("editor-content").performScrollTo().performTextInput("Native editor\nUnsaved test draft — not an external save.")
        capture("03-editor")
        compose.onNodeWithText("Find / Replace / Go to").performScrollTo().performClick()
        capture("04-editor-tools")
        compose.onNodeWithText("Close tools").performScrollTo().performClick()
        compose.onNodeWithTag("window-actions-editor").performClick()
        compose.onNodeWithText("Tile left", substring=true).assertExists()
        compose.onNodeWithText("Tile right", substring=true).assertExists()
        compose.onNodeWithText("Maximize").assertExists()
        capture("05-window-actions")
        compose.onNodeWithText("Restore previous size").performClick()
        compose.onNodeWithContentDescription("Close window").performClick()
        compose.onNodeWithText("Keep drafts and close").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("window-actions-editor")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Find").performScrollTo().performClick()
        compose.onNodeWithTag("palette-query").performTextInput("notes")
        capture("06-find")
        compose.onNodeWithTag("palette-hit-module:notes").performScrollTo().performClick()
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        maximize("notes")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("notes-name") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-name").performScrollTo().performTextInput("visual_"+run.take(8))
        compose.onNodeWithTag("notes-text").performScrollTo().performTextInput("Native Markdown note. This is a memory-only draft until saved.")
        capture("07-notes")
    }
}
