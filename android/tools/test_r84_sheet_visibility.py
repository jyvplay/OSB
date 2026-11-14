#!/usr/bin/env python3
"""Negative controls for the R84 workbook sheet-visibility verifier.

Each control copies a real workbook produced by the Kotlin probe, breaks exactly one thing, and asserts that
tools/check_r84_sheet_visibility.py refuses the result. The controls skip when the artifacts are absent (they
are produced by tools/run_r84_sheet_visibility.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r84_sheet_visibility -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r84_sheet_visibility as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r84sheet/artifacts'
WORKBOOKS = ('default', 'hidden', 'very-hidden', 'mixed', 'first-hidden', 'styled')


def rewrite(source: Path, target: Path, transform) -> None:
    with zipfile.ZipFile(source) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, payload in entries:
            result = transform(name, payload)
            if result is None:
                continue
            output.writestr(name, result)


def part_only(transform, part='xl/workbook.xml'):
    def apply(name, payload):
        if name != part:
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


class VisibilityControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'visibility.tsv').is_file():
            self.skipTest('R84 sheet visibility artifacts are absent; run tools/run_r84_sheet_visibility.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r84-visible-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in WORKBOOKS:
            shutil.copy(ARTIFACTS / f'{name}.xlsx', self.directory / f'{name}.xlsx')
        shutil.copy(ARTIFACTS / 'visibility.tsv', self.directory / 'visibility.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def replace(self, workbook: str, transform):
        target = self.directory / f'{workbook}.xlsx'
        temporary = self.directory / f'{workbook}.xlsx.tmp'
        rewrite(target, temporary, part_only(transform))
        shutil.move(str(temporary), str(target))

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def edit_intent(self, transform):
        path = self.directory / 'visibility.tsv'
        path.write_text(transform(path.read_text(encoding='utf-8').splitlines()), encoding='utf-8')

    def test_undisturbed_workbooks_pass(self):
        report = self.verify()
        self.assertEqual(report['workbooks'], 6)
        self.assertEqual(report['sheets'], 13)

    def test_removed_state_is_rejected(self):
        self.replace('hidden', lambda text: text.replace(
            '<sheet name="Hidden" sheetId="2" state="hidden"', '<sheet name="Hidden" sheetId="2"', 1))
        self.expect_failure("!= declared 'hidden'")

    def test_explicit_visible_state_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<sheet name="One" sheetId="1"', '<sheet name="One" sheetId="1" state="visible"', 1))
        self.expect_failure('is visible but carries state')

    def test_changed_state_token_is_rejected(self):
        self.replace('very-hidden', lambda text: text.replace('state="veryHidden"', 'state="hidden"', 1))
        self.expect_failure("!= declared 'veryHidden'")

    def test_broken_sheet_numbering_is_rejected(self):
        self.replace('mixed', lambda text: text.replace('sheetId="3"', 'sheetId="4"', 1))
        self.expect_failure('sheetid')

    def test_broken_relationship_id_is_rejected(self):
        self.replace('mixed', lambda text: text.replace('r:id="rSheet2"', 'r:id="rSheet9"', 1))
        self.expect_failure('relationship')

    def test_undeclared_sheet_attribute_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<sheet name="Two" sheetId="2"', '<sheet name="Two" sheetId="2" tabColor="FFFF0000"', 1))
        self.expect_failure('undeclared attributes')

    def test_reordered_sheets_are_rejected(self):
        def transform(text):
            hidden = '<sheet name="Back" sheetId="2" state="hidden" r:id="rSheet2"/>'
            deep = '<sheet name="Archive" sheetId="3" state="veryHidden" r:id="rSheet3"/>'
            return text.replace(hidden + deep, deep + hidden, 1)
        self.replace('mixed', transform)
        self.expect_failure('name')

    def test_missing_book_views_is_rejected(self):
        def transform(text):
            start = text.index('<bookViews>')
            end = text.index('</bookViews>') + len('</bookViews>')
            return text[:start] + text[end:]
        self.replace('mixed', transform)
        self.expect_failure('must declare <bookviews>')

    def test_book_views_on_a_visible_workbook_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<sheets>', '<bookViews><workbookView activeTab="0"/></bookViews><sheets>', 1))
        self.expect_failure('must not emit <bookviews>')

    def test_wrong_active_tab_is_rejected(self):
        self.replace('mixed', lambda text: text.replace('activeTab="0"', 'activeTab="1"', 1))
        self.expect_failure('activetab')

    def test_hidden_first_sheet_with_its_own_active_tab_is_rejected(self):
        self.replace('first-hidden', lambda text: text.replace('activeTab="1"', 'activeTab="0"', 1))
        self.expect_failure('activetab')

    def test_tampered_intent_state_is_rejected(self):
        # The intent is narrowed from veryHidden to hidden: the workbook still declares <bookViews> (its
        # declared states are not all visible), so the state rule itself is what refuses.
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'very-hidden' and fields[3] == 'veryHidden':
                    fields[3] = 'hidden'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure("!= declared 'hidden'")

    def test_intent_without_a_visible_sheet_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'hidden' and fields[3] == 'visible':
                    fields[3] = 'hidden'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('no visible sheet')

    def test_unknown_state_token_is_rejected(self):
        def transform(lines):
            fields = lines[0].split('\t')
            fields[3] = 'gone'
            lines[0] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('unknown state')

    def test_tampered_cell_count_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'styled' and fields[2] == 'Live':
                    fields[4] = '1'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('non-empty cells')

    def test_truncated_intent_row_is_rejected(self):
        self.edit_intent(lambda lines: '\n'.join(lines[:1] + ['\t'.join(lines[1].split('\t')[:3])]) + '\n')
        self.expect_failure('fields, expected 5')

    def test_undeclared_workbook_is_rejected(self):
        shutil.copy(self.directory / 'default.xlsx', self.directory / 'stray.xlsx')
        self.expect_failure('unexpected workbooks')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'visibility.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
