#!/usr/bin/env python3
"""Controls for the R115 office-case inventory.

Twenty controls. The synthetic ones mutate one property at a time and require the *named* rule; two are aimed at the
defect that this round's own scanner had — a `"/*"` inside a string literal deleting the following test — because that
is the failure mode that lets a gate pass while the thing it guards is broken; and one copies the real workflow and
the real device tests into a temporary root and removes the workflow's single `Class#method` filter, which must be
reported as eighteen unexpected cases.
"""
from __future__ import annotations

import importlib
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
gate = importlib.import_module('check_r115_office_cases')
case_gate = importlib.import_module('r64_office_case_gate')
scanner = importlib.import_module('kotlin_code_only')

WORKFLOW = '.github/workflows/android-r54-office.yml'
TEST_DIR = 'app/src/androidTest/java/dev/fusion/shell'
EXPECTED = frozenset({'dev.fusion.shell.A.a1', 'dev.fusion.shell.A.a2', 'dev.fusion.shell.B.m'})

WORKFLOW_TEXT = (
    '      - name: Run real API34 UI and provider checks\n'
    '        run: >\n'
    '          bash tools/run_gradle_ci.sh device :app:pixel6Api34ProDebugAndroidTest\n'
    '          -Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.A,dev.fusion.shell.B#m\n'
)
A = 'class A {\n    @Test fun a1() {}\n    @Test fun a2() {}\n}\n'
B = 'class B {\n    @Test fun m() {}\n    @Test fun other() {}\n}\n'


def build(folder: Path, workflow: str = WORKFLOW_TEXT, a: str = A, b: str = B) -> Path:
    (folder / WORKFLOW).parent.mkdir(parents=True, exist_ok=True)
    (folder / WORKFLOW).write_text(workflow, encoding='utf-8')
    (folder / TEST_DIR).mkdir(parents=True, exist_ok=True)
    (folder / TEST_DIR / 'A.kt').write_text(a, encoding='utf-8')
    (folder / TEST_DIR / 'B.kt').write_text(b, encoding='utf-8')
    return folder


class SyntheticControls(unittest.TestCase):
    def judge(self, expected=EXPECTED, **kwargs) -> list[str]:
        with tempfile.TemporaryDirectory(prefix='r115-') as folder:
            found, _ = gate.violations(build(Path(folder), **kwargs), expected)
            return found

    def rules(self, found: list[str]) -> set[str]:
        return {item.split(':')[0] for item in found}

    def test_clean_tree_is_silent(self):
        self.assertEqual([], self.judge())

    def test_missing_expected_case_fires_rule_1(self):
        expected = EXPECTED | {'dev.fusion.shell.A.a3'}
        self.assertEqual({'R115-1'}, self.rules(self.judge(expected)))

    def test_extra_selected_case_fires_rule_3(self):
        a = A.replace('    @Test fun a2() {}\n', '    @Test fun a2() {}\n    @Test fun a4() {}\n')
        self.assertEqual({'R115-3'}, self.rules(self.judge(a=a)))

    def test_changed_workflow_filter_fires_rules_1_and_3(self):
        workflow = WORKFLOW_TEXT.replace('B#m', 'B#other')
        self.assertEqual({'R115-1', 'R115-3'}, self.rules(self.judge(workflow=workflow)))

    def test_duplicate_method_fires_rule_2(self):
        a = A.replace('    @Test fun a2() {}\n', '    @Test fun a2() {}\n    @Test fun a1() {}\n')
        self.assertEqual({'R115-2'}, self.rules(self.judge(a=a)))

    def test_missing_class_file_fires_rule_4(self):
        workflow = WORKFLOW_TEXT.replace('dev.fusion.shell.B#m', 'dev.fusion.shell.C#m')
        found = self.judge(workflow=workflow)
        self.assertIn('R115-4', self.rules(found))

    def test_reused_capture_name_fires_rule_5(self):
        a = A.replace('class A {', 'class A {\n    fun cap() { captureDialogDisplay(compose, act, "r58-x", "t1") }')
        a = a.replace('    @Test fun a2() {}', '    @Test fun a2() { captureDialogDisplay(compose, act, "r58-x", "t2") }')
        self.assertIn('R115-5', self.rules(self.judge(a=a)))

    def test_commented_out_selection_selects_nothing(self):
        workflow = WORKFLOW_TEXT + '          # -Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.A\n'
        found, summary = None, None
        with tempfile.TemporaryDirectory(prefix='r115-') as folder:
            found, summary = gate.violations(build(Path(folder), workflow=workflow), EXPECTED)
        self.assertEqual([], found)
        self.assertEqual(2, summary['classes'])

    def test_commented_out_capture_call_claims_no_name(self):
        a = ('class A {\n'
             '    // captureDialogDisplay(compose, act, "r58-ghost", "t1")\n'
             '    /* captureDialogDisplay(compose, act, "r58-ghost", "t2") */\n'
             '    @Test fun a1() { captureDialogDisplay(compose, act, "r58-live", "t3") }\n'
             '    @Test fun a2() {}\n'
             '}\n')
        with tempfile.TemporaryDirectory(prefix='r115-') as folder:
            root = build(Path(folder), a=a)
            self.assertEqual(['r58-live'], gate.captures(root))
            self.assertEqual([], gate.violations(root, EXPECTED)[0])

    def test_annotation_with_arguments_is_still_a_case(self):
        a = A.replace('@Test fun a1()', '@Test(timeout = 5000) fun a1()')
        self.assertEqual(['a1', 'a2'], gate.CASE.findall('@Test(timeout = 5000) fun a1() {}\n@Test fun a2() {}\n'))
        with tempfile.TemporaryDirectory(prefix='r115-') as folder:
            self.assertEqual([], self.judge(a=a))

    def test_named_capture_argument_and_parens_in_a_string(self):
        a = ('class A {\n'
             '    @Test fun a1() { captureDialogDisplay(compose, act, name = "r58-named", tag = "t(1)") }\n'
             '    @Test fun a2() { captureDialogDisplay(compose, act, "r58-named", "t2") }\n'
             '}\n')
        with tempfile.TemporaryDirectory(prefix='r115-') as folder:
            root = build(Path(folder), a=a)
            self.assertEqual(['r58-named', 'r58-named'], gate.captures(root))
            self.assertIn('R115-5', self.rules(gate.violations(root, EXPECTED)[0]))

    def test_string_containing_comment_markers_keeps_the_test(self):
        # The exact shape that broke this round's first scanner: `"content://provider/document/*"` must not start a
        # block comment, and `"a//b"` must not start a line comment.
        a = ('class A {\n'
             '    val uri = "content://provider/document/*"\n'
             '    val other = "a//b"\n'
             '    @Test fun a1() {}\n'
             '    @Test fun a2() {}\n'
             '}\n')
        self.assertEqual([], self.judge(a=a))


class ScannerControls(unittest.TestCase):
    def test_block_comment_is_removed(self):
        self.assertNotIn('secret', scanner.code_only('val x = 1 /* secret */ val y = 2'))

    def test_quoted_markers_survive_as_code(self):
        code = scanner.code_only('val a = "/*" \n @Test fun t() {}')
        self.assertIn('@Test', code)

    def test_char_literal_with_quote_is_skipped(self):
        code = scanner.code_only("val q = '\"' \n @Test fun t() {}")
        self.assertIn('@Test', code)

    def test_strip_comments_keeps_literals_and_drops_prose(self):
        text = 'val a = "content://provider/document/*"\n// captureDialogDisplay(x, y, "ghost")\n'
        kept = scanner.strip_comments(text)
        self.assertIn('"content://provider/document/*"', kept)
        self.assertNotIn('ghost', kept)
        self.assertEqual(text.count('\n'), kept.count('\n'))

    def test_line_count_is_preserved(self):
        text = 'a\nb // c\nd\n'
        self.assertEqual(text.count('\n'), scanner.code_only(text).count('\n'))


def _visual():
    return importlib.import_module('check_r112_visual_contracts')


class RepositoryControls(unittest.TestCase):
    def test_this_repository_selects_exactly_its_expected_cases(self):
        found, summary = gate.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual(37, summary['expected'])
        self.assertEqual(37, summary['selected'])
        self.assertEqual(7, summary['classes'])

    def test_two_parsers_agree_on_every_capture_name(self):
        # R112 reads names from the first literal in a call; R115 reads the third positional argument (or `name =`).
        # They are independent parsers of the same 14 call sites, so agreement is evidence that neither one drifted.
        independent = []
        for path in sorted((ROOT / TEST_DIR).glob('*.kt')):
            if path.name == 'NativeVisualEvidence.kt':
                continue
            independent.extend(name for name, _ in _visual().captures(path.read_text(encoding='utf-8')))
        self.assertEqual(14, len(independent))
        self.assertEqual(sorted(independent), sorted(gate.captures(ROOT)))

    def test_dropping_the_single_method_filter_is_reported(self):
        with tempfile.TemporaryDirectory(prefix='r115-real-') as folder:
            root = Path(folder)
            workflow = (ROOT.parent / WORKFLOW)
            (root / WORKFLOW).parent.mkdir(parents=True, exist_ok=True)
            text = workflow.read_text(encoding='utf-8')
            self.assertIn('ShellSmokeTest#', text)
            (root / WORKFLOW).write_text(text.replace('ShellSmokeTest#explicitLocalPlanOpensActualCalculatorAndPreservesModuleGates',
                                                     'ShellSmokeTest'), encoding='utf-8')
            shutil.copytree(ROOT / TEST_DIR, root / TEST_DIR)
            found, summary = gate.violations(root, frozenset(case_gate.EXPECTED))
            overrun = [item for item in found if item.startswith('R115-3')]
            self.assertEqual(18, len(overrun), '\n'.join(found))
            self.assertTrue(all('ShellSmokeTest' in item for item in overrun), overrun)
            self.assertEqual(55, summary['selected'])


if __name__ == '__main__':
    unittest.main()
