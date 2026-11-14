#!/usr/bin/env python3
"""Negative controls for the R83 spreadsheet hidden-dimension verifier.

Each control copies a real workbook produced by the Kotlin probe, breaks exactly one thing, and asserts
that tools/check_r83_sheet_hidden.py refuses the result. The controls skip when the artifacts are
absent (they are produced by tools/run_r83_sheet_hidden.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r83_sheet_hidden -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r83_sheet_hidden as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r83sheet/artifacts'
WORKBOOKS = ('default', 'rows', 'columns', 'both', 'height-hidden', 'styled')


def rewrite(source: Path, target: Path, transform) -> None:
    with zipfile.ZipFile(source) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, payload in entries:
            result = transform(name, payload)
            if result is None:
                continue
            output.writestr(name, result)


def part_only(transform, part='xl/worksheets/sheet1.xml'):
    def apply(name, payload):
        if name != part:
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


def without(tag: str):
    def apply(text: str) -> str:
        start = text.index(f'<{tag}>')
        end = text.index(f'</{tag}>') + len(f'</{tag}>')
        return text[:start] + text[end:]
    return apply


class HiddenControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'hidden.tsv').is_file():
            self.skipTest('R83 sheet hidden artifacts are absent; run tools/run_r83_sheet_hidden.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r83-hidden-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in WORKBOOKS:
            shutil.copy(ARTIFACTS / f'{name}.xlsx', self.directory / f'{name}.xlsx')
        shutil.copy(ARTIFACTS / 'hidden.tsv', self.directory / 'hidden.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def replace(self, workbook: str, transform, part: str = 'xl/worksheets/sheet1.xml'):
        target = self.directory / f'{workbook}.xlsx'
        temporary = self.directory / f'{workbook}.xlsx.tmp'
        rewrite(target, temporary, part_only(transform, part))
        shutil.move(str(temporary), str(target))

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def edit_intent(self, transform):
        path = self.directory / 'hidden.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        path.write_text(transform(lines), encoding='utf-8')

    def test_undisturbed_workbooks_pass(self):
        report = self.verify()
        self.assertEqual(report['workbooks'], 6)
        self.assertEqual(report['sheets'], 6)

    def test_removed_row_hidden_flag_is_rejected(self):
        self.replace('rows', lambda text: text.replace('<row r="2" hidden="1">', '<row r="2">', 1))
        self.expect_failure('row 2 is declared hidden')

    def test_undeclared_hidden_row_is_rejected(self):
        self.replace('rows', lambda text: text.replace('<row r="1">', '<row r="1" hidden="1">', 1))
        self.expect_failure('row 1 carries hidden but is not declared')

    def test_dropped_hidden_only_row_is_rejected(self):
        self.replace('rows', lambda text: text.replace('<row r="5" hidden="1"></row>', '', 1))
        self.expect_failure('row set')

    def test_duplicate_column_is_rejected(self):
        self.replace('columns', lambda text: text.replace('<col min="3" max="3" hidden="1"/>',
                                                          '<col min="2" max="2" hidden="1"/>', 1))
        self.expect_failure('duplicate <col> entries')

    def test_removed_column_hidden_flag_is_rejected(self):
        self.replace('columns', lambda text: text.replace('<col min="2" max="2" hidden="1"/>',
                                                          '<col min="2" max="2"/>', 1))
        self.expect_failure('column 2 is declared hidden')

    def test_undeclared_column_width_is_rejected(self):
        self.replace('columns', lambda text: text.replace(
            '<col min="2" max="2" hidden="1"/>',
            '<col min="2" max="2" width="10.0" customWidth="1" hidden="1"/>', 1))
        self.expect_failure('declares a width that the intent does not')

    def test_changed_column_width_is_rejected(self):
        self.replace('both', lambda text: text.replace('width="18.0"', 'width="18.5"', 1))
        self.expect_failure('!= declared 18.0')

    def test_hidden_column_without_cols_is_rejected(self):
        self.replace('both', without('cols'))
        self.expect_failure('the sheet has no <cols>')

    def test_undeclared_column_attribute_is_rejected(self):
        self.replace('both', lambda text: text.replace(
            '<col min="1" max="1" width="18.0" customWidth="1"/>',
            '<col min="1" max="1" width="18.0" customWidth="1" outlineLevel="1"/>', 1))
        self.expect_failure('undeclared attributes')

    def test_wide_and_hidden_column_is_accepted(self):
        # Acceptance control, not a refusal: the `both` and `styled` fixtures hide a column that also
        # declares a width, and that legal combination must keep passing. Counted separately from the
        # refusal controls in R83-RESULTS.md.
        report = self.verify()
        self.assertEqual(report['workbooks'], 6)

    def test_tampered_intent_width_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'both':
                    fields[5] = '1:19.0,2:12.0'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('!= declared 19.0')

    def test_removed_hidden_row_from_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'rows':
                    fields[3] = '2'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        # Row 5 exists in the sheet, so narrowing the declared set breaks the row set before the
        # per-row hidden check is reached.
        self.expect_failure('row set')

    def test_truncated_intent_row_is_rejected(self):
        self.edit_intent(lambda lines: '\n'.join(lines[:1] + ['\t'.join(lines[1].split('\t')[:5])]) + '\n')
        self.expect_failure('fields, expected 8')

    def test_undeclared_workbook_is_rejected(self):
        shutil.copy(self.directory / 'default.xlsx', self.directory / 'stray.xlsx')
        self.expect_failure('unexpected workbooks')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'hidden.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
