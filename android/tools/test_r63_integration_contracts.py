"""Static guards for wiring and safety defaults; these do not execute Android UI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


class R63IntegrationContracts(unittest.TestCase):
    def test_formula_fill_is_explicit_and_protection_resets_on_open(self):
        ui = read('feature/calc/src/main/java/dev/fusion/calc/SheetScreen.kt')
        self.assertIn('testTag("sheet-range-formula")', ui)
        self.assertIn('rangeOperation = OfficeSheetRangeEdit.Operation.FILL_FORMULA', ui)
        self.assertIn('rangeInput = selected; rangeProtection = true', ui)
        self.assertIn('testTag("sheet-range-formula-disclosure")', ui)
        self.assertIn('whole fill is refused', ui)
        self.assertIn('plan.formulasWritten', ui)
        self.assertIn('plan.formulasRemoved', ui)

    def test_formula_fill_preserves_snapshot_and_full_candidate_validation(self):
        source = read('format/ooxml/src/main/kotlin/dev/fusion/ooxml/OfficeSheetRangeEdit.kt')
        self.assertIn('draft === original && currentSelected == selected', source)
        self.assertIn('OfficeSheetDraft.restore(draft.revision + 1, cells)', source)
        self.assertIn('OfficeSheetDraft.MAX_COLUMNS', source)
        self.assertIn('OfficeSheetDraft.MAX_ROWS', source)
        self.assertIn('if (dc == 0 && dr == 0) return formula', source)
        self.assertNotIn('catch (', source)
        self.assertIn('Operation.FILL_LITERAL', source)
        self.assertIn('Operation.CLEAR_CONTENTS', source)

    def test_writer_restart_is_off_and_depends_on_numbering(self):
        ui = read('feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt')
        self.assertIn('var restartOrderedGroups by remember { mutableStateOf(false) }', ui)
        self.assertIn('numberedLines=false; strikeSpans=false; restartOrderedGroups=false', ui)
        self.assertIn('if (!it) restartOrderedGroups=false', ui)
        self.assertIn('value=restartOrderedGroups, enabled=numberedLines', ui)
        self.assertIn('DOCX readers may render numbering differently', ui)
        self.assertIn('fun EditorScreen(', ui)
        self.assertIn('fun EditorScreenWithStrike(', ui)
        self.assertIn('fun EditorScreenWithListRestart(', ui)

    def test_writer_old_overloads_and_explicit_override_are_present(self):
        writer = read('format/ooxml/src/main/kotlin/dev/fusion/ooxml/StructuredWriterDocx.kt')
        package = read('format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt')
        self.assertIn('restartOrderedGroups = false', writer)
        self.assertIn('require(!restartOrderedGroups || numberedLines)', writer)
        self.assertIn('restartOrderedGroups = false', package)
        self.assertIn('<w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/>', package)
        self.assertIn('level == 0', package)

    def test_current_status_retains_historical_traceability_labels(self):
        status = read('STATUS.md')
        self.assertTrue(status.startswith('# Current R64'))
        self.assertIn('legacy headings are historical', status)
        self.assertIn('# Latest R53', status)
        self.assertIn('# Latest R52', status)
        self.assertIn('0/50', status)

    def test_focus_failure_has_bounded_late_observation_without_rearm(self):
        source = read('privacy/src/androidTest/java/dev/fusion/privacy/PrivacyDeviceReady.kt')
        late = source.split('private fun lateMainThread()', 1)[1].split('/** Before launching', 1)[0]
        self.assertIn('thread.stackTrace.take(12)', late)
        self.assertIn('.take(160)', late)
        self.assertIn('SystemClock.elapsedRealtime()', late)
        self.assertIn('not a system ANR trace or cause', late)
        self.assertNotIn('.text', late)
        focus = source.split('fun <T:Activity> focus(', 1)[1]
        self.assertIn('ready.await(10,TimeUnit.SECONDS)', focus)
        self.assertIn('if (!gained) fail(', focus)
        self.assertIn('lateMain=${lateMainThread()}', focus)
        self.assertNotIn('restart_workspace', focus)
        self.assertNotIn('pressBack()', focus)
        self.assertNotIn('performClick()', focus)

    def test_large_font_new_actions_are_one_shot_real_taps(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/ShellSmokeTest.kt')
        section = source.split('@Test fun editorActionsRemainVisibleAfterLongInputAtTwoHundredPercentFont()', 1)[1].split('private fun withVisibleKeyboard', 1)[0]
        self.assertIn('By.text("New")', section)
        self.assertIn('Exactly one visible New action required', section)
        self.assertIn('node.isEnabled && !node.visibleBounds.isEmpty', section)
        self.assertEqual(section.count('node.click()'), 1)
        self.assertEqual(section.count('clickNewOnce()'), 3)  # one declaration + two distinct intended taps
        self.assertIn('compose.onNodeWithText("New").assertIsDisplayed()', section)
        self.assertIn('Long line 60', section)
        action = section.split('fun clickNewOnce()', 1)[1].split('clickNewOnce()', 1)[0]
        self.assertIn('Until.findObject(selector), 10_000', action)
        self.assertLess(action.index('Exactly one visible'), action.index('node.click()'))
        self.assertLess(action.index('New must be enabled and visible'), action.index('node.click()'))
        self.assertNotIn('catch (', action)
        self.assertNotIn('while (', action)

    def test_sheet_clear_assertions_have_one_failure_only_observation(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt')
        section = source.split('@Test fun sheetEditsRecalculatesKeepsUnappliedInputAndCapturesActualUi()', 1)[1].split('@Test fun writerExportDisclosureIsVisibleInRealEditor()', 1)[0]
        self.assertIn('SheetUiFailureEvidence.once(compose.activityRule.scenario, "clear visible and enabled")', section)
        self.assertEqual(section.count('onNodeWithTag("sheet-clear").performScrollTo().assertIsDisplayed().assertIsEnabled()'), 1)
        evidence = read('app/src/androidTest/java/dev/fusion/shell/SheetUiFailureEvidence.kt')
        self.assertEqual(evidence.count('action()'), 1)
        self.assertIn('catch (failure: Throwable)', evidence)
        self.assertIn('throw AssertionError(', evidence)
        self.assertNotIn('performClick()', evidence)

    def test_restart_picker_failure_observer_is_late_bounded_and_read_only(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt')
        section = source.split('@Test fun writerListRestartReviewIsOptInResettableAndReachesRealPicker()', 1)[1].split('@Test fun formattedWriterRejectsConsent', 1)[0]
        self.assertEqual(section.count('device.wait(Until.hasObject(documentsUi), 15_000)'), 1)
        failed = section.split('if (!device.wait(Until.hasObject(documentsUi), 15_000))', 1)[1].split('device.pressBack()', 1)[0]
        self.assertIn('thread.stackTrace.take(24)', failed)
        self.assertIn('not the system ANR trace', failed)
        self.assertIn('throw AssertionError', failed)
        self.assertNotIn('.click()', failed)
        self.assertNotIn('pressBack()', failed)
        self.assertNotIn('performClick()', failed)
        self.assertNotIn('device.wait(', failed)

    def test_touch_failure_observer_preserves_one_shot_assertions(self):
        source = read('privacy/src/androidTest/java/dev/fusion/privacy/R23InputDefenseTest.kt')
        trace = read('privacy/src/androidTest/java/dev/fusion/privacy/PrivacySignalTrace.kt')
        section = source.split('@Test fun realTouchEntryHasNoInputConnectionAndHardwareKeysCannotMutateCode()', 1)[1].split('@Test fun fullAndPartialObscuration', 1)[0]
        self.assertEqual(section.count('click("touch_key_7")'), 1)
        self.assertEqual(section.count('click("touch_reveal")'), 1)
        self.assertIn('assertEquals("72 (2/64)", output.text.toString())', section)
        self.assertIn('assertNull(output.onCreateInputConnection(EditorInfo()))', section)
        self.assertIn('performAccessibilityAction', section)
        self.assertIn('r63-privacy-touch-events.json', section)
        self.assertIn('r63-privacy-touch-events.json', trace)
        self.assertIn('events.size < 64', trace)
        self.assertIn('actualSignals=${trace.snapshot()}', section)
        self.assertIn('failure !is AssertionError', section)
        self.assertIn('original.addSuppressed(closeFailure)', section)
        self.assertIn('if (original == null) throw closeFailure', section)
        self.assertNotIn('restart_workspace).performClick()', section)
        self.assertNotIn('Thread.sleep', section)

    def test_webview_host_preparation_and_observation_do_not_retry_tests(self):
        workflow = (ROOT.parent / '.github/workflows/android-r25-web.yml').read_text(encoding='utf-8')
        prepared = workflow.index('python3 tools/r62_install_managed_sdk.py')
        device = workflow.index('bash tools/run_gradle_ci.sh device')
        observer = workflow.index('python3 tools/r61_emulator_observation.py')
        self.assertLess(prepared, device)
        self.assertLess(device, observer)
        self.assertEqual(workflow.count('bash tools/run_gradle_ci.sh device'), 1)
        self.assertIn('if: failure()', workflow[device:observer])
        self.assertNotIn('continue-on-error', workflow)

    def test_candidate_device_cases_frames_and_readers_are_wired(self):
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text(encoding='utf-8')
        for item in ('r63FormulaFillContracts', 'r63WriterRestartContracts',
                     'check_r63_formula_reference.py', 'check_r63_writer_reference.py',
                     'test_r63_formula_reference', 'test_r63_writer_reference',
                     'r63-sheet-formula.png', 'r63-writer-restart.png'):
            self.assertIn(item, workflow)
        for file, name in (
            ('app/src/androidTest/java/dev/fusion/shell/R59SheetDeviceTest.kt', 'formulaFillAdjustsReferencesProtectsConflictsAndSupportsUndoAndExport'),
            ('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt', 'writerListRestartReviewIsOptInResettableAndReachesRealPicker'),
            ('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt', 'writerListRestartProviderReadbackContainsIndependentOverrides'),
        ):
            self.assertIn('@Test fun ' + name, read(file))
        self.assertNotIn('continue-on-error', workflow)


if __name__ == '__main__':
    unittest.main()
