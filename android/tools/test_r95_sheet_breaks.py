#!/usr/bin/env python3
"""Negative controls for the R95 page-break verifier.

Each test tampers one thing — a worksheet part or one intent row — and asserts the verifier refuses it. The
undisturbed artifacts are checked first, so a verifier that rejects everything cannot pass.

Skips when the R95 artifacts are absent (CI regenerates them with `tools/run_r95_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r95_sheet_breaks -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r95_sheet_breaks as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r95/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None
GRID = 'xl/worksheets/sheet1.xml'
FIRST = 'xl/worksheets/sheet2.xml'
PLAIN = 'xl/worksheets/sheet3.xml'

ROW_BREAKS = ('<rowBreaks count="3" manualBreakCount="2"><brk id="10" man="1"/><brk id="25" man="0"/>'
              '<brk id="40" min="0" max="16383" man="1"/></rowBreaks>')
COL_BREAKS = '<colBreaks count="1" manualBreakCount="1"><brk id="3" man="1"/></colBreaks>'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class BreakControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'breaks.tsv').is_file():
            raise unittest.SkipTest('R95 artifacts are absent; run tools/run_r95_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r95-breaks-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, entry: str, transform) -> None:
        rewrite(ARTIFACTS / name, self.folder / name, entry, transform)

    def fail(self) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, HAVE_OPENPYXL)

    def lines(self) -> list[str]:
        return (self.folder / 'breaks.tsv').read_text(encoding='utf-8').splitlines()

    def write_lines(self, lines: list[str]) -> None:
        (self.folder / 'breaks.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['break_sheets'], 2)
        self.assertEqual(totals['plain_sheets'], 1)
        self.assertEqual(totals['breaks'], 5)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_sheets'], 3)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    def test_row_breaks_removed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(ROW_BREAKS, ''))
        self.fail()

    def test_col_breaks_removed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(COL_BREAKS, ''))
        self.fail()

    def test_break_order_broken(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<brk id="10" man="1"/><brk id="25" man="0"/>',
            '<brk id="25" man="0"/><brk id="10" man="1"/>'))
        self.fail()

    def test_count_attribute_changed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(ROW_BREAKS, ROW_BREAKS.replace('count="3"', 'count="4"')))
        self.fail()

    def test_manual_count_attribute_changed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            ROW_BREAKS, ROW_BREAKS.replace('manualBreakCount="2"', 'manualBreakCount="3"')))
        self.fail()

    def test_manual_flag_flipped(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace('<brk id="25" man="0"/>', '<brk id="25" man="1"/>'))
        self.fail()

    def test_manual_flag_dropped(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace('<brk id="10" man="1"/>', '<brk id="10"/>'))
        self.fail()

    def test_id_out_of_grid(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace('<brk id="10" man="1"/>', '<brk id="0" man="1"/>'))
        self.fail()

    def test_span_invented(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<brk id="10" man="1"/>', '<brk id="10" min="0" max="16383" man="1"/>'))
        self.fail()

    def test_span_dropped(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<brk id="40" min="0" max="16383" man="1"/>', '<brk id="40" man="1"/>'))
        self.fail()

    def test_span_value_changed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace('max="16383"', 'max="16382"'))
        self.fail()

    def test_attribute_order_broken(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<brk id="40" min="0" max="16383" man="1"/>',
            '<brk min="0" max="16383" id="40" man="1"/>'))
        self.fail()

    def test_unmodelled_attribute(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<brk id="10" man="1"/>', '<brk id="10" man="1" pt="0"/>'))
        self.fail()

    def test_undeclared_breaks_injected(self):
        self.patch('r95.xlsx', PLAIN, lambda xml: xml.replace('</sheetData>', '</sheetData>' + ROW_BREAKS))
        self.fail()

    def test_breaks_before_header_footer(self):
        def move(xml: str) -> str:
            header = xml[xml.index('<headerFooter'):xml.index('</headerFooter>') + len('</headerFooter>')]
            return xml.replace(header, '').replace(ROW_BREAKS, ROW_BREAKS + header)
        self.patch('r95.xlsx', GRID, move)
        self.fail()

    def test_empty_container_written(self):
        self.patch('r95.xlsx', FIRST, lambda xml: xml.replace(
            '<rowBreaks count="1" manualBreakCount="1"><brk id="5" min="2" max="9" man="1"/></rowBreaks>',
            '<rowBreaks count="1" manualBreakCount="1"><brk id="5" min="2" max="9" man="1"/></rowBreaks>'
            '<colBreaks count="0" manualBreakCount="0"></colBreaks>'))
        self.fail()

    def test_intent_id_lie(self):
        self.write_lines([line.replace('\trow\t10\t', '\trow\t11\t') for line in self.lines()])
        self.fail()

    def test_intent_manual_lie(self):
        self.write_lines([line.replace('\trow\t25\t0\t', '\trow\t25\t1\t') for line in self.lines()])
        self.fail()

    def test_intent_manual_total_lie(self):
        self.write_lines([line.replace('\t3', '\t2') for line in self.lines()])
        self.fail()

    def test_intent_axis_lie(self):
        self.write_lines([line.replace('\tcol\t3\t', '\tcol\t4\t') for line in self.lines()])
        self.fail()

    def test_intent_row_dropped(self):
        self.write_lines([line for line in self.lines() if '\t40\t' not in line])
        self.fail()

    def test_intent_mixed_zero_and_break_rows(self):
        self.write_lines(self.lines() + ['r95.xlsx\tPlain\trow\t7\t1\t-\t-\t1'])
        self.fail()

    def test_intent_field_count(self):
        self.write_lines([self.lines()[0] + '\textra'])
        self.fail()


if __name__ == '__main__':
    unittest.main()
