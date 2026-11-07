#!/usr/bin/env python3
"""Static guardrails only; actual Android interaction and visual evidence are separate gates."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


class RepairGuardrails(unittest.TestCase):
    def test_writer_test_checks_both_disclosures_and_full_stale_refusal(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt')
        numbered = source.split('@Test fun numberedWriterOptInCancelResetAndRealPicker()', 1)[1].split('@Test fun ', 1)[0]
        self.assertIn('assertFullTextVisible(compose, "writer-structured-disclosure")', numbered)
        self.assertIn('assertFullTextVisible(compose, "writer-numbered-disclosure")', numbered)
        stale = source.split('@Test fun formattedWriterRejectsConsentAfterDraftRevisionChanges()', 1)[1].split('private fun created', 1)[0]
        self.assertIn('assertTextEquals("Draft or editor state changed. Review the export again.")', stale)
        self.assertIn('assertEquals(OfficeExportViewModel.State(), exportModel().state.value)', stale)
        self.assertIn('currentPackageName', stale)

    def test_picker_observes_failure_without_select_or_repeat(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/R22XlsxUiDeviceTest.kt')
        section = source.split('private fun app(', 1)[1].split('@Test fun ', 1)[0]
        self.assertIn('targetState=', section)
        self.assertEqual(section.count('file.click()'), 1)
        self.assertNotIn('action_menu_select', section)
        self.assertNotIn('device.pressBack()', section)

    def test_privacy_traces_do_not_clobber_the_other_actual_test(self):
        trace = read('privacy/src/androidTest/java/dev/fusion/privacy/PrivacySignalTrace.kt')
        wake = read('privacy/src/androidTest/java/dev/fusion/privacy/R24ScreenOffDeviceTest.kt')
        reveal = read('privacy/src/androidTest/java/dev/fusion/privacy/R23InputDefenseTest.kt')
        self.assertIn('private val reportName: String = "r24c-privacy-reveal-events.json"', trace)
        self.assertIn('File(folder, reportName)', trace)
        self.assertIn('PrivacySignalTrace(it, "r24c-privacy-screenoff-events.json")', wake)
        self.assertIn('PrivacySignalTrace(it)', reveal)
        self.assertIn('assertEquals(" (0/64)",output.text.toString())', wake)
        self.assertNotIn('findViewById<Button>(R.id.restart_workspace).performClick()', wake)

    def test_video_failure_records_bounded_actual_samples_without_replaying(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/R25LocalVideoDeviceTest.kt')
        section = source.split('@Test fun actualMp4DecoderProducesOrderedChangingSurfacePixelsAndReleasesOnHome()', 1)[1]
        self.assertEqual(section.count('play(s);'), 1)
        self.assertIn('deadline=started+7500', section)
        self.assertIn('firstSamples.size<4', section)
        self.assertIn('recentSamples.size==12', section)
        self.assertIn('listOf("red","green","blue"),seen)', section)

    def test_task_diagnostic_preserves_cases_and_installs_same_emulator_prerequisite(self):
        workflow = (ROOT.parent / '.github/workflows/android-task-diagnostic.yml').read_text()
        self.assertIn('apt-get install -y --no-install-recommends libpulse0', workflow)
        self.assertIn("grep -F 'libpulse.so.0'", workflow)
        self.assertEqual(workflow.count('bash tools/run_gradle_ci.sh device'), 1)
        for case in ('R17TaskUiDeviceTest', 'R18TaskUiDeviceTest', 'R19TaskUiDeviceTest',
                     'R20CountsUiDeviceTest', 'ShellSmokeTest#savedWikiBacklinksReadRealNoteFiles'):
            self.assertIn(case, workflow)
        self.assertIn('if: failure()\n        run: python3 tools/r61_emulator_observation.py', workflow)
        self.assertNotIn('continue-on-error', workflow)

    def test_long_editor_stages_rethrow_not_retry(self):
        source = read('app/src/androidTest/java/dev/fusion/shell/ShellSmokeTest.kt')
        section = source.split('@Test fun editorActionsRemainVisibleAfterLongInputAtTwoHundredPercentFont()', 1)[1].split('private fun withVisibleKeyboard', 1)[0]
        self.assertIn('throw IllegalArgumentException', section)
        for stage in ('scroll', 'click', 'text input'):
            self.assertEqual(section.count('stage("' + stage + '")'), 1)
        self.assertEqual(section.count('editor.performTextInput('), 1)


if __name__ == '__main__':
    unittest.main()
