#!/usr/bin/env python3
"""Negative controls for the R81 spreadsheet row-height verifier.

Each control copies a real workbook produced by the Kotlin probe, breaks exactly one thing, and
asserts that tools/check_r81_sheet_rows.py refuses the result. The controls skip when the artifacts
are absent (they are produced by tools/run_r81_sheet_rows.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r81_sheet_rows -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r81_sheet_rows as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r81sheet/artifacts'
WORKBOOKS = ('plain', 'heights', 'max-height', 'fractional', 'styled', 'multi-sheet')


def rewrite(source: Path, target: Path, transform) -> None:
    """Copies a workbook, letting ``transform(name, payload)`` rewrite or drop parts (None drops)."""
    with zipfile.ZipFile(source) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, payload in entries:
            result = transform(name, payload)
            if result is None:
                continue
            output.writestr(name, result)


def sheets_only(transform, part='xl/worksheets/sheet1.xml'):
    def apply(name, payload):
        if name != part:
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


class RowHeightControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'rows.tsv').is_file():
            self.skipTest('R81 sheet row artifacts are absent; run tools/run_r81_sheet_rows.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r81-rows-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in WORKBOOKS:
            shutil.copy(ARTIFACTS / f'{name}.xlsx', self.directory / f'{name}.xlsx')
        shutil.copy(ARTIFACTS / 'rows.tsv', self.directory / 'rows.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def replace(self, workbook: str, transform, part: str = 'xl/worksheets/sheet1.xml'):
        target = self.directory / f'{workbook}.xlsx'
        temporary = self.directory / f'{workbook}.xlsx.tmp'
        rewrite(target, temporary, sheets_only(transform, part))
        shutil.move(str(temporary), str(target))

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def test_undisturbed_workbooks_pass(self):
        report = self.verify()
        self.assertEqual(report['workbooks'], 6)
        self.assertEqual(report['sheets'], 7)

    def test_removed_height_is_rejected(self):
        self.replace('heights', lambda text: text.replace(' ht="24.0" customHeight="1"', '', 1))
        self.expect_failure('missing its declared height')

    def test_changed_height_is_rejected(self):
        self.replace('heights', lambda text: text.replace('ht="9.75"', 'ht="9.5"', 1))
        self.expect_failure('!= declared')

    def test_missing_custom_height_is_rejected(self):
        self.replace('heights', lambda text: text.replace('ht="9.75" customHeight="1"', 'ht="9.75"', 1))
        self.expect_failure('without customheight')

    def test_height_on_an_undeclared_row_is_rejected(self):
        self.replace('heights', lambda text: text.replace('<row r="2">', '<row r="2" ht="18.0" customHeight="1">', 1))
        self.expect_failure('no height was declared')

    def test_out_of_order_rows_are_rejected(self):
        def transform(text):
            tail = '<row r="3" ht="9.75" customHeight="1"></row>'
            self.assertIn(tail, text)
            text = text.replace(tail, '', 1)
            return text.replace('<row r="2">', tail + '<row r="2">', 1)
        self.replace('heights', transform)
        self.expect_failure('ascending order')

    def test_extra_row_is_rejected(self):
        self.replace('heights', lambda text: text.replace('</sheetData>',
                                                          '<row r="4"></row></sheetData>', 1))
        self.expect_failure('row set')

    def test_height_on_a_row_beyond_the_intent_is_rejected(self):
        self.replace('heights', lambda text: text.replace('</sheetData>',
                                                          '<row r="4" ht="12.0" customHeight="1"></row></sheetData>', 1))
        self.expect_failure('no height was declared')

    def test_duplicate_row_is_rejected(self):
        self.replace('heights', lambda text: text.replace('<row r="2">', '<row r="1"></row><row r="2">', 1))
        self.expect_failure('duplicate row numbers')

    def test_tampered_intent_is_rejected(self):
        path = self.directory / 'rows.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'heights':
                fields[3] = '1:25.0,3:9.75'
                lines[index] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('!= declared 25.0')

    def test_removed_column_widths_are_rejected(self):
        self.replace('styled', lambda text: text.replace('<cols>', '<cols-x>').replace('</cols>', '</cols-x>'))
        self.expect_failure('column widths')

    def test_undeclared_workbook_is_rejected(self):
        shutil.copy(self.directory / 'plain.xlsx', self.directory / 'stray.xlsx')
        self.expect_failure('unexpected workbooks')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'rows.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
