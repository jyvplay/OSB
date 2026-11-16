#!/usr/bin/env python3
"""Negative controls for the R94 print-layout verifier.

Each test tampers one thing — a worksheet part or one intent row — and asserts the verifier refuses it. The
undisturbed artifacts are checked first, so a verifier that rejects everything cannot pass.

Skips when the R94 artifacts are absent (CI regenerates them with `tools/run_r94_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r94_sheet_print -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r94_sheet_print as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r94/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None
GRID = 'xl/worksheets/sheet1.xml'
BARE = 'xl/worksheets/sheet2.xml'

OPTIONS = '<printOptions horizontalCentered="1" gridLines="1"/>'
MARGINS = '<pageMargins left="0.5" right="0.75" top="1" bottom="1" header="0.5" footer="0.5"/>'
SETUP = '<pageSetup paperSize="9" scale="85" orientation="landscape"/>'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class PrintControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'print.tsv').is_file():
            raise unittest.SkipTest('R94 artifacts are absent; run tools/run_r94_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r94-print-'))

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
        return (self.folder / 'print.tsv').read_text(encoding='utf-8').splitlines()

    def write_lines(self, lines: list[str]) -> None:
        (self.folder / 'print.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['print_sheets'], 2)
        self.assertEqual(totals['plain_sheets'], 3)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_sheets'], 5)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    def test_print_options_removed(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(OPTIONS, ''))
        self.fail()

    def test_page_margins_removed(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(MARGINS, ''))
        self.fail()

    def test_page_setup_removed(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(SETUP, ''))
        self.fail()

    def test_margin_attribute_dropped(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(' header="0.5" footer="0.5"', ' footer="0.5"'))
        self.fail()

    def test_margin_value_changed(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace('left="0.5"', 'left="0.6"'))
        self.fail()

    def test_margin_written_in_exponent_form(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace('left="0.5"', 'left="5E-1"'))
        self.fail()

    def test_margin_order_broken(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(
            'left="0.5" right="0.75"', 'right="0.75" left="0.5"'))
        self.fail()

    def test_false_switch_written(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(
            OPTIONS, '<printOptions horizontalCentered="1" verticalCentered="0" gridLines="1"/>'))
        self.fail()

    def test_switch_order_broken(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(
            OPTIONS, '<printOptions gridLines="1" horizontalCentered="1"/>'))
        self.fail()

    def test_setup_order_broken(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace(
            SETUP, '<pageSetup scale="85" paperSize="9" orientation="landscape"/>'))
        self.fail()

    def test_setup_value_changed(self):
        self.patch('r94.xlsx', GRID, lambda xml: xml.replace('scale="85"', 'scale="84"'))
        self.fail()

    def test_print_elements_out_of_sequence(self):
        def reorder(xml: str) -> str:
            return xml.replace(OPTIONS + MARGINS + SETUP, MARGINS + OPTIONS + SETUP)
        self.patch('r94.xlsx', GRID, reorder)
        self.fail()

    def test_undeclared_print_record(self):
        self.patch('r94.xlsx', BARE, lambda xml: xml.replace(
            '</sheetData>', '</sheetData>' + OPTIONS + MARGINS))
        self.fail()

    def test_intent_has_flag_lie(self):
        self.write_lines([line.replace('\t1\thorizontalCentered', '\t0\thorizontalCentered') for line in self.lines()])
        self.fail()

    def test_intent_flag_lie(self):
        self.write_lines([line.replace('horizontalCentered,gridLines', 'gridLines') for line in self.lines()])
        self.fail()

    def test_intent_margin_lie(self):
        self.write_lines([line.replace('\t0.5\t0.75\t', '\t0.5\t0.5\t') for line in self.lines()])
        self.fail()

    def test_intent_orientation_lie(self):
        self.write_lines([line.replace('\tlandscape\t', '\tportrait\t') for line in self.lines()])
        self.fail()

    def test_intent_row_dropped(self):
        self.write_lines([line for line in self.lines() if 'Bare' not in line])
        self.fail()

    def test_intent_field_count(self):
        self.write_lines([self.lines()[0] + '\textra'])
        self.fail()


if __name__ == '__main__':
    unittest.main()
