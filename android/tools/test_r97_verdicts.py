#!/usr/bin/env python3
"""Negative controls for the R97 verdict verifier.

Each test tampers exactly one thing — a field of `verdicts.tsv`, one cell of `rules.tsv`, one value — and asserts
the verifier refuses it. The undisturbed artifacts are verified first, so a verifier that rejects everything cannot
pass. Tampering the *intent* tables matters more than tampering the verdicts: it proves the Python recomputation
actually drives the comparison rather than echoing the file.

Skips when the R97 artifacts are absent (CI regenerates them with `tools/run_r97_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r97_verdicts -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path

import check_r97_verdicts as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r97/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None


class VerdictControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'verdicts.tsv').is_file():
            raise unittest.SkipTest('R97 artifacts are absent; run tools/run_r97_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r97-verdicts-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    # --- helpers -----------------------------------------------------------------
    def lines(self, name: str) -> list[str]:
        return (self.folder / name).read_text(encoding='utf-8').splitlines()

    def write(self, name: str, lines: list[str]) -> None:
        (self.folder / name).write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def edit(self, name: str, prefix: str, column: int, value: str) -> None:
        """Replace one tab-separated column of the first line starting with `prefix`."""
        lines = self.lines(name)
        found = False
        for index, line in enumerate(lines):
            if line.startswith(prefix):
                fields = line.split('\t')
                fields[column] = value
                lines[index] = '\t'.join(fields)
                found = True
                break
        self.assertTrue(found, f'no row starts with {prefix!r}')
        self.write(name, lines)

    def fail(self) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, HAVE_OPENPYXL)

    # --- the untouched artifacts -------------------------------------------------
    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['cells'], 33)
        self.assertEqual(totals['sheets'], 6)
        self.assertEqual(totals['fired'], 46)
        self.assertEqual(totals['unresolved'], 7)
        self.assertEqual(totals['undecided'], 2)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_rules'], 15)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    # --- the reported verdicts ---------------------------------------------------
    def test_fired_priorities_removed(self):
        self.edit('verdicts.tsv', 'Numbers\tA1\t', 3, '-')
        self.fail()

    def test_fired_priorities_added(self):
        self.edit('verdicts.tsv', 'Numbers\tA6\t', 3, '1\x1f2')
        self.fail()

    def test_kind_changed(self):
        self.edit('verdicts.tsv', 'Numbers\tA6\t', 4, 'expression')
        self.fail()

    def test_stopped_flag_flipped(self):
        self.edit('verdicts.tsv', 'Numbers\tA6\t', 5, '0')
        self.fail()

    def test_unresolved_removed(self):
        self.edit('verdicts.tsv', 'Text\tB1\t', 6, '-')
        self.fail()

    def test_visual_changed(self):
        self.edit('verdicts.tsv', 'Numbers\tA6\t', 7, 'fill:C6EFCE')
        self.fail()

    def test_decoration_bold_flipped(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 8, '0')
        self.fail()

    def test_decoration_font_colour_changed(self):
        self.edit('verdicts.tsv', 'Text\tB1\t', 12, 'FF0000')
        self.fail()

    def test_decoration_scale_changed(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 14, 'FFFFFF')
        self.fail()

    def test_decoration_bar_changed(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 15, '0.9')
        self.fail()

    def test_applicable_count_changed(self):
        self.edit('verdicts.tsv', 'Numbers\tA1\t', 16, '1')
        self.fail()

    def test_outcome_flipped(self):
        self.edit('verdicts.tsv', 'Numbers\tA1\t', 2, 'undecided')
        self.fail()

    def test_reason_invented_on_a_decided_row(self):
        self.edit('verdicts.tsv', 'Numbers\tA1\t', 17, 'a reason that did not happen')
        self.fail()

    def test_reason_removed_from_an_undecided_row(self):
        self.edit('verdicts.tsv', 'Faults\tE1\t', 17, '-')
        self.fail()

    # --- the rule model the verdicts are about ----------------------------------
    def test_rule_priority_swapped(self):
        # 1 <-> 2 on the Numbers sheet: the stopping rule is no longer first, so every row changes.
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Numbers' and fields[6] in ('1', '2'):
                fields[6] = '2' if fields[6] == '1' else '1'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_rule_formula_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Numbers' and fields[6] == '1':
                fields[12] = '50'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_rule_operator_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Numbers' and fields[6] == '1':
                fields[8] = 'lessThan'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_stop_flag_dropped(self):
        self.edit('rules.tsv', 'Numbers\t1\t0\tA1:A10\t0', 7, '-')
        self.fail()

    def test_threshold_percent_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Numbers' and fields[6] == '4':
                fields[13] = 'min:-,percent:60,max:-'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_colour_list_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Percentile':
                fields[14] = '0000FF,FFFFFE,000000' if ',' in fields[14] else fields[14]
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_block_sqref_narrowed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Numbers' and fields[3] == 'A1:A10':
                fields[3] = 'A1:A2'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_rule_dropped(self):
        self.write('rules.tsv', [line for line in self.lines('rules.tsv') if not line.startswith('Faults\t')])
        self.fail()

    def test_rule_field_count_wrong(self):
        lines = self.lines('rules.tsv')
        lines[0] = lines[0] + '\textra'
        self.write('rules.tsv', lines)
        self.fail()

    # --- the values the verdicts are computed from ------------------------------
    def test_value_changed(self):
        self.edit('values.tsv', 'Numbers\tA1\t', 3, '100')
        self.fail()

    def test_value_kind_changed(self):
        self.edit('values.tsv', 'Rank\tD4\t', 2, 'text')
        self.fail()

    def test_value_row_removed(self):
        self.write('values.tsv', [line for line in self.lines('values.tsv') if not line.startswith('Rank\tD3\t')])
        self.fail()

    def test_value_field_count_wrong(self):
        lines = self.lines('values.tsv')
        lines[0] = lines[0] + '\textra'
        self.write('values.tsv', lines)
        self.fail()


if __name__ == '__main__':
    unittest.main()
