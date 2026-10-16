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

    // Read-only diagnostic of the actual Activity-owned model. Does not dispatch or repair an edit.
    private fun editorDiagnostic(): String {
        var result="Activity observation unavailable"
        compose.activityRule.scenario.onActivity { activity ->
            val state=androidx.lifecycle.ViewModelProvider(activity)[dev.fusion.editor.EditorViewModel::class.java].state.value
            val id=state.session.selectedId
            result="loading=${state.loading} blocked=${state.recoveryBlocked} toolsBusy=${state.toolsBusy} planCount=${state.replacement?.count} revision=${state.buffers[id]?.revision} buffer=${state.buffers[id]?.content} field=${state.fields[id]?.text} status=${state.status}"
        }
        return result
    }
    @Test fun explicitLocalPlanOpensActualCalculatorAndPreservesModuleGates() {
        openModule("automation")
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("routine-script").performScrollTo().performTextInput("open calculator")
        compose.onNodeWithTag("routine-run").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("calculator-input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calculator-input").performScrollTo().assertIsDisplayed()
    }
    @Test fun densityReviewCancelAndApplyChangeActualDesktopState() {
        openModule("settings")
        // Assert the actual reset fixture before any action; the window engine defaults to COMPACT.
        compose.onNodeWithText("Window geometry density: COMPACT").assertExists()
        compose.onNodeWithText("TOUCH").performScrollTo().performClick()
        compose.onNodeWithText("Window geometry density: COMPACT").assertExists()
        compose.onNodeWithText("Cancel density change").performScrollTo().performClick()
        compose.onNodeWithText("Window geometry density: COMPACT").assertExists()
        compose.onNodeWithText("TOUCH").performScrollTo().performClick()
        compose.onNodeWithTag("settings-apply-density").performScrollTo().performClick()
        compose.onNodeWithText("Window geometry density: TOUCH").assertExists()
        compose.onNodeWithTag("settings-query").performScrollTo().performTextInput("x".repeat(257))
        compose.onNodeWithText("Search query exceeds256 UTF16 code units; query unchanged.").assertExists()
        compose.onNodeWithTag("settings-query").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
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
    @Test fun activatingWindowKeepsChildFocus() {
        openModule("editor")
        compose.onNodeWithText("New").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editor-content").performTextInput("retained")
        openModule("calculator")
        compose.onNodeWithTag("editor-content").assertIsNotFocused()
        compose.onNodeWithTag("editor-content").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("editor-content").assertIsFocused().assertTextContains("retained")
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
        compose.onNodeWithText("Close tab").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Discard unsaved changes?")).fetchSemanticsNodes().isNotEmpty() && runCatching { compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed() }.isSuccess }
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
        val beforeApply=editorDiagnostic()
        compose.onNodeWithTag("editor-confirm-replace").performClick()
        val afterApply=editorDiagnostic()
        compose.onNodeWithText("Close tools").performClick()
        try { compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("two two") }
        catch(failure:AssertionError) {
            throw AssertionError("Replacement failed. Before=$beforeApply; after apply=$afterApply; after close=${editorDiagnostic()}",failure)
        }
        compose.onNodeWithTag("editor-undo").performScrollTo().performClick()
        compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("one one")
        val beforeRedo=editorDiagnostic()
        compose.onNodeWithTag("editor-redo").performScrollTo().performClick()
        val afterRedo=editorDiagnostic()
        try { compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("two two") }
        catch(failure:AssertionError) { throw AssertionError("Redo failed before recreation. Before=$beforeRedo; after=$afterRedo",failure) }
        compose.activityRule.scenario.recreate()
        try { compose.onNodeWithTag("editor-content").performScrollTo().assertTextContains("two two") }
        catch(failure:AssertionError) { throw AssertionError("Redo lost after recreation. Before=$beforeRedo; after redo=$afterRedo; recreated=${editorDiagnostic()}",failure) }
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
        compose.onNodeWithTag("editor-content").assertTextContains("First document")
        // Patch attack: focus-driven scrolling must not hide the fixed action toolbar.
        compose.onNodeWithText("New").assertIsDisplayed()
        compose.onNodeWithText("New").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("editor-tab").fetchSemanticsNodes().size==2 }
        compose.onAllNodesWithTag("editor-tab").assertCountEquals(2)
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
        store.save(source,"[[$target]]",null)
        store.save(target,"Saved target",null)
        try {
            openModule("notes")
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Open note: $target") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open note: $target").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("notes-scan-links") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notes-scan-links").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Backlink: $source")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Backlink: $source").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("notes-text").performScrollTo().performTextReplacement("unsaved target draft")
            fun requestAndKeepDraft() {
                val link=compose.onNodeWithText("Backlink: $source")
                link.performScrollTo().assertIsDisplayed()
                val before=TaskUiEvidence.geometry(compose.activity)+"; link="+link.fetchSemanticsNode().boundsInRoot
                link.performClick() // Actual touch; no semantics onClick shortcut.
                try {
                    // A touch return is not proof a new platform dialog has completed presentation.
                    // Observe the actual displayed dialog/decision control, under the existing 10s UI budget.
                    compose.waitUntil(10000) {runCatching {
                        compose.onNodeWithText("Discard this memory draft?").assertIsDisplayed()
                        compose.onNodeWithText("Keep editing").assertIsDisplayed().assertIsEnabled()
                    }.isSuccess}
                    compose.onNodeWithText("Keep editing").performClick()
                } catch(failure:Throwable) {
                    var observed="unavailable"
                    compose.activityRule.scenario.onActivity {a ->
                        val state=androidx.lifecycle.ViewModelProvider(a)[dev.fusion.notes.NotesViewModel::class.java].state.value
                        observed="pending=${state.pending}; dirty=${state.dirty}; busy=${state.busy}; name=${state.name}; recovery=${state.recoveryStatus}"
                    }
                    throw AssertionError("Actual Notes backlink: $observed; before=$before; after=${TaskUiEvidence.geometry(compose.activity)}",failure)
                }
                compose.onNodeWithTag("notes-text").performScrollTo().assertTextContains("unsaved target draft")
            }
            requestAndKeepDraft()
            requestAndKeepDraft() // Patch attack: a second prompt/cancel must not reuse stale dialog state or lose the draft.
            org.junit.Assert.assertEquals("Saved target",store.load(target).text)
            org.junit.Assert.assertEquals("[[$target]]",store.load(source).text)
        } finally {
            java.io.File(context.filesDir,"notes/$source.md").delete()
            java.io.File(context.filesDir,"notes/$target.md").delete()
        }
    }

    @Test fun savedNoteTrashRestoresWithoutLosingItsRevision() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store=dev.fusion.notes.NotesStore(context)
        val name="Trash_界_"+java.util.UUID.randomUUID().toString().replace("-","")
        val original=store.save(name,"retained saved bytes α",null)
        try {
            openModule("notes")
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Open note: $name") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open note: $name").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Move note to trash…") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Move note to trash…").performScrollTo().assertIsDisplayed().performClick()
            try { compose.waitUntil(10000) { compose.onNodeWithText("Move saved note to trash?").isDisplayed() } }
            catch(error: Exception) {
                throw AssertionError("Trash confirmation missing. Action semantics: "+runCatching {
                    compose.onNodeWithText("Move note to trash…").printToString(maxDepth=1)
                }.getOrDefault("no action node"),error)
            }
            compose.onNodeWithText("Move saved note to trash?").assertIsDisplayed()
            compose.onNodeWithText("Move saved revision").performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Saved revision moved to trash.",substring=true)).fetchSemanticsNodes().isNotEmpty() }
            val receipt=store.trash().single { it.name==name }
            org.junit.Assert.assertFalse(store.list().contains(name))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
            compose.onNodeWithTag("notes-trash-scan").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("notes-restore-${receipt.token}") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notes-restore-${receipt.token}").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasText("Restored saved revision without overwriting another note.")).fetchSemanticsNodes().isNotEmpty() }
            org.junit.Assert.assertEquals(original,store.load(name))
            compose.onNodeWithTag("notes-text").performScrollTo().assertTextContains(original.text)
        } finally {
            java.io.File(context.filesDir,"notes/$name.md").delete()
            runCatching { store.trash().filter { it.name==name }.forEach {
                java.io.File(context.filesDir,"notes/.trash-v1/${it.name}--${it.token}_${it.revision.sha256}.md").delete()
            } }
        }
    }

    @Test fun savedNoteTrashAtTwoHundredPercentFontScale() = withTwoHundredPercentFont {
        savedNoteTrashRestoresWithoutLosingItsRevision()
    }
    @Test fun editorTabsAtTwoHundredPercentFontScale() = withTwoHundredPercentFont {
        editorTabsDoNotReplaceEachOthersText()
    }
    @Test fun editorActionsRemainVisibleAfterLongInputAtTwoHundredPercentFont() = withTwoHundredPercentFont { withVisibleKeyboard {
        openModule("editor")
        compose.waitUntil(10000) { compose.onAllNodes(hasText("New") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New").performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("editor-content") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editor-content").performScrollTo().performClick().performTextInput((1..60).joinToString("\n") { "Long line $it" })
        compose.waitUntil(10000) {
            var visible=false
            compose.activityRule.scenario.onActivity {
                visible=it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true
            }
            visible
        }
        compose.waitUntil(10000) { runCatching { compose.onNodeWithText("New").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("New").assertIsDisplayed().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("editor-tab").fetchSemanticsNodes().size==2 }
        compose.onNodeWithText("New").assertIsDisplayed()
        compose.onNodeWithText("Untitled.txt • Unsaved").performScrollTo().performClick()
        compose.onNodeWithTag("editor-content").assertTextContains("Long line 60", substring=true)
    } }
    private fun withVisibleKeyboard(action:()->Unit) {
        // Explicit disposable-emulator fixture: attached hardware keyboard must not suppress IME.
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val key="show_ime_with_hard_keyboard"
        val before=android.provider.Settings.Secure.getString(instrumentation.targetContext.contentResolver,key)
        check(before==null || before=="0" || before=="1")
        fun command(value:String) {
            instrumentation.uiAutomation.executeShellCommand(value).use { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
            }
        }
        try { command("settings put secure $key 1"); action() }
        finally { command(if(before==null) "settings delete secure $key" else "settings put secure $key $before") }
    }
    private fun withTwoHundredPercentFont(action:()->Unit) {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val before=android.provider.Settings.System.getString(instrumentation.targetContext.contentResolver,"font_scale")
        check(before==null || before.toFloatOrNull()?.let { it in 0.5f..5f }==true) { "Unexpected existing font setting; no mutation" }
        fun command(value:String) {
            instrumentation.uiAutomation.executeShellCommand(value).use { fd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
            }
        }
        try {
            command("settings put system font_scale 2.0")
            compose.waitUntil(10000) {
                runCatching {
                    var actual=0f
                    compose.activityRule.scenario.onActivity { actual=it.resources.configuration.fontScale }
                    kotlin.math.abs(actual-2f)<0.01f
                }.getOrDefault(false)
            }
            compose.waitForIdle()
            action()
        } finally {
            // DisposableEmulatorRule has already required explicit opt-in AND emulator hardware.
            val value=before?.toFloatOrNull()?.takeIf { it in 0.5f..5f }
            command(if(value==null) "settings delete system font_scale" else "settings put system font_scale $value")
            compose.waitUntil(10_000) {
                runCatching {
                    var actual=0f
                    compose.activityRule.scenario.onActivity { actual=it.resources.configuration.fontScale }
                    kotlin.math.abs(actual-(value ?: 1f))<0.01f
                }.getOrDefault(false)
            }
            compose.waitForIdle()
        }
    }

}
