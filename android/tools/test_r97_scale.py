#!/usr/bin/env python3
"""Negative controls for the R97 scale/bar arithmetic verifier.

Each test tampers one number or one stop and asserts the lane refuses it: an altered interpolated colour, a bar
fraction nudged by one percent, a colour dragged outside the box of its neighbouring stops, a swapped palette, a
threshold kind changed, a value changed under the arithmetic. The undisturbed artifacts are verified first.

Skips when the R97 artifacts are absent (CI regenerates them with `tools/run_r97_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r97_scale -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

import check_r97_scale as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r97/artifacts'


class ScaleControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'verdicts.tsv').is_file():
            raise unittest.SkipTest('R97 artifacts are absent; run tools/run_r97_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r97-scale-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def lines(self, name: str) -> list[str]:
        return (self.folder / name).read_text(encoding='utf-8').splitlines()

    def write(self, name: str, lines: list[str]) -> None:
        (self.folder / name).write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def edit(self, name: str, prefix: str, column: int, value: str) -> None:
        lines = self.lines(name)
        for index, line in enumerate(lines):
            if line.startswith(prefix):
                fields = line.split('\t')
                fields[column] = value
                lines[index] = '\t'.join(fields)
                self.write(name, lines)
                return
        self.fail(f'no row starts with {prefix!r}')

    def fail(self) -> None:
        with self.assertRaises((checker.Fail, checker.base.Fail)):
            checker.verify(self.folder)

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder)
        self.assertEqual(totals['scales'], 14)
        self.assertEqual(totals['bars'], 4)
        self.assertEqual(totals['rules'], 3)

    def test_artifacts_absent_fail(self):
        with self.assertRaises((checker.Fail, checker.base.Fail)):
            checker.verify(self.workdir / 'absent')

    def test_scale_colour_altered(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 7, 'font:1000:-\x1ffont:0001:-\x1fscale:FF3901\x1fbar:0.2')
        self.fail()

    def test_scale_colour_outside_the_stop_box(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 7, 'font:1000:-\x1ffont:0001:-\x1fscale:00FF00\x1fbar:0.2')
        self.fail()

    def test_scale_colour_replaced_by_a_stop(self):
        self.edit('verdicts.tsv', 'Numbers\tA3\t', 7, 'font:1000:-\x1fscale:FFFF00\x1fbar:0.3')
        self.fail()

    def test_bar_fraction_nudged(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 7, 'font:1000:-\x1ffont:0001:-\x1fscale:FF3900\x1fbar:0.21')
        self.fail()

    def test_bar_fraction_above_one(self):
        self.edit('verdicts.tsv', 'Numbers\tA10\t', 7, 'fill:FFC7CE')
        self.edit('verdicts.tsv', 'Numbers\tA1\t', 7, 'scale:FF0000\x1fbar:1.5')
        self.fail()

    def test_visual_dropped(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 7, 'font:1000:-\x1ffont:0001:-\x1fscale:FF3900')
        self.fail()

    def test_visual_added(self):
        self.edit('verdicts.tsv', 'Numbers\tA6\t', 7, 'fill:FFC7CE\x1fscale:FF0000')
        self.fail()

    def test_fired_priority_trimmed(self):
        self.edit('verdicts.tsv', 'Numbers\tA2\t', 3, '2\x1f3\x1f4')
        self.fail()

    def test_palette_order_swapped(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Percentile':
                fields[14] = '000000,FFFFFF,0000FF'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_threshold_kind_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Percentile':
                fields[13] = 'min:-,percent:50,max:-'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_stop_value_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Numbers' and fields[6] == '5':
                fields[13] = 'num:0,num:20'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()

    def test_value_changed_under_the_arithmetic(self):
        self.edit('values.tsv', 'Numbers\tA2\t', 3, '7')
        self.fail()

    def test_value_made_non_numeric(self):
        self.edit('values.tsv', 'Numbers\tA3\t', 2, 'text')
        self.edit('values.tsv', 'Numbers\tA3\t', 3, 'three')
        self.fail()

    def test_rule_kind_changed(self):
        lines = self.lines('rules.tsv')
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'Percentile':
                fields[5] = 'dataBar'
                lines[index] = '\t'.join(fields)
        self.write('rules.tsv', lines)
        self.fail()


if __name__ == '__main__':
    unittest.main()
