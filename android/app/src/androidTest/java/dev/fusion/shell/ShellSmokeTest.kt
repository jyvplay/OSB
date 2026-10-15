package dev.fusion.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** SH-08 / PR-01 / FF-15. Emulator checks only; never a physical-device spike pass. */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    // Reset is deliberately refused without explicit disposable-emulator consent.
    private val reset = DisposableEmulatorRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(reset).around(compose)

    private fun openModule(module: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("shelf-$module") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shelf-$module").performScrollTo().performClick()
    }

    @Test fun shellRendersWithShelf() {
        compose.onNodeWithText("Fusion Shell").assertIsDisplayed()
        compose.onNodeWithTag("shelf-files").assertIsDisplayed()
    }
    @Test fun openingCalculatorRendersItsContent() {
        openModule("calculator")
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("calculator-input") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calculator-input").performScrollTo().performTextInput("0.1+0.2")
        compose.onNodeWithTag("calculator-evaluate").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("calculator-result")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("calculator-result") and hasText("0.3")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("calculator-result").performScrollTo().assertTextEquals("0.3")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("calculator-input").performScrollTo().assertTextContains("0.1+0.2")
        compose.onNodeWithTag("calculator-result").performScrollTo().assertTextEquals("0.3")
    }
    @Test fun distinctWindowPanesDoNotMergeTheirControls() {
        openModule("editor")
        compose.onNodeWithText("Open text").performScrollTo().assertIsDisplayed()
        openModule("calculator")
        compose.onNodeWithTag("calculator-input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open text").assertExists()
        compose.onAllNodesWithContentDescription("Minimize").assertCountEquals(2)
    }
    @Test fun filesDeclaresMissingAccess() {
        openModule("files")
        compose.onNodeWithText("No folder access. Only folders you choose are visible.").assertIsDisplayed()
    }
    @Test fun editorRejectsAccidentalDiscardAndSurvivesActivityRecreation() {
        openModule("editor")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("editor-content").performTextInput("Draft survives recreation")
        compose.onNodeWithText("Close tab").performScrollTo().performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("editor-content").assertTextContains("Draft survives recreation")
    }
    /** PR-01 native integration source. Not a claim of execution or IME/AX acceptance. */
    @Test fun editorLiteralReplaceIsOneUndoableDraftEdit() {
        openModule("editor")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("editor-content").performTextInput("one one")
        compose.onNodeWithText("Find / Replace / Go to").performScrollTo().performClick()
        compose.onNodeWithTag("editor-find-query").performScrollTo().performTextInput("one")
        compose.onNodeWithTag("editor-replacement").performScrollTo().performTextInput("two")
        compose.onNodeWithTag("editor-replace-all-review").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("editor-confirm-replace")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editor-confirm-replace").performClick()
        compose.onNodeWithText("Close tools").performClick()
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("two two")
        compose.onNodeWithTag("editor-undo").performScrollTo().performClick()
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("one one")
        compose.onNodeWithTag("editor-redo").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("two two")
    }

    @Test fun blockedBeamCannotBeEnabledByPilotConsent() {
        openModule("beam")
        compose.onNodeWithText("Unavailable in this pilot").assertIsDisplayed()
        compose.onNodeWithText("Enable for this session with test data").assertDoesNotExist()
    }
    @Test fun editorCanBeRestoredFromMinimizedShelf() {
        openModule("editor")
        compose.onNodeWithContentDescription("Minimize").performClick()
        compose.onNodeWithText("Restore: Editor").performScrollTo().performClick()
        compose.onNodeWithText("Open text").performScrollTo().assertIsDisplayed()
    }
    @Test fun editorTabsDoNotReplaceEachOthersText() {
        openModule("editor")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("editor-content").performTextInput("First document")
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("editor-content").performTextInput("Second document")
        compose.onNodeWithText("Untitled.txt • Unsaved").performScrollTo().performClick()
        compose.onNodeWithTag("editor-content").assertTextContains("First document")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun paletteExecutesARealModuleAction() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("shelf-editor") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Find").performScrollTo().performClick()
        compose.onNodeWithTag("palette-query").performTextInput("editor")
        compose.onNodeWithTag("palette-query").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Open text").performScrollTo().assertIsDisplayed()
    }
    @Test fun notesSaveAndMemoryDraftSurviveActivityRecreation() {
        val name="test_"+java.util.UUID.randomUUID().toString().replace("-","")
        try {
            openModule("notes")
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("notes-name") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notes-name").performScrollTo().performTextInput(name)
            compose.onNodeWithTag("notes-text").performScrollTo().performTextInput("Saved native note")
            compose.onNodeWithTag("notes-save").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Saved locally with exact readback.", substring=true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notes-text").performScrollTo().performTextReplacement("Memory-only draft")
            compose.onNodeWithText("New note").performScrollTo().performClick()
            compose.onNodeWithText("Discard this memory draft?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.activityRule.scenario.recreate()
            // Consent is deliberately Activity-scoped, while the draft's ViewModel is retained.
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.onNodeWithTag("notes-text").performScrollTo().assertTextContains("Memory-only draft")
        } finally {
            val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            java.io.File(context.filesDir,"notes/$name.md").delete() // Only this test's fresh UUID name.
        }
    }
    @Test fun savedWikiBacklinksReadRealNoteFiles() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store=dev.fusion.notes.NotesStore(context)
        val suffix=java.util.UUID.randomUUID().toString().replace("-","")
        val source="LinkSource_$suffix";val target="LinkTarget_$suffix"
        val a=store.save(source,"[[$target]]",null)
        val b=store.save(target,"Saved target",null)
        try {
            openModule("notes")
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Open note: $target") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open note: $target").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("notes-scan-links") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notes-scan-links").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Backlink: $source")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Backlink: $source").performScrollTo().assertIsDisplayed()
            org.junit.Assert.assertEquals("[[$target]]",store.load(source).text)
        } finally {
            runCatching { store.delete(source,a.revision) };runCatching { store.delete(target,b.revision) }
        }
    }

}
