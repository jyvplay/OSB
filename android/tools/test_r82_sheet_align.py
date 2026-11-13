#!/usr/bin/env python3
"""Negative controls for the R82 spreadsheet cell-alignment verifier.

Each control copies a real workbook produced by the Kotlin probe, breaks exactly one thing, and
asserts that tools/check_r82_sheet_align.py refuses the result. The controls skip when the artifacts
are absent (they are produced by tools/run_r82_sheet_align.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r82_sheet_align -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r82_sheet_align as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r82sheet/artifacts'
WORKBOOKS = ('default', 'horizontal', 'vertical', 'combined', 'shared', 'styled')


def rewrite(source: Path, target: Path, transform) -> None:
    with zipfile.ZipFile(source) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, payload in entries:
            result = transform(name, payload)
            if result is None:
                continue
            output.writestr(name, result)


def part_only(transform, part='xl/styles.xml'):
    def apply(name, payload):
        if name != part:
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


class CellAlignControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'align.tsv').is_file():
            self.skipTest('R82 sheet align artifacts are absent; run tools/run_r82_sheet_align.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r82-align-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in WORKBOOKS:
            shutil.copy(ARTIFACTS / f'{name}.xlsx', self.directory / f'{name}.xlsx')
        shutil.copy(ARTIFACTS / 'align.tsv', self.directory / 'align.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def replace(self, workbook: str, transform, part: str = 'xl/styles.xml'):
        target = self.directory / f'{workbook}.xlsx'
        temporary = self.directory / f'{workbook}.xlsx.tmp'
        rewrite(target, temporary, part_only(transform, part))
        shutil.move(str(temporary), str(target))

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def test_undisturbed_workbooks_pass(self):
        report = self.verify()
        self.assertEqual(report['workbooks'], 6)
        self.assertEqual(report['cells'], 17)

    def test_removed_alignment_element_is_rejected(self):
        self.replace('combined', lambda text: text.replace(
            '<alignment horizontal="center" vertical="top"/>', '', 1))
        self.expect_failure('has no <alignment>')

    def test_changed_horizontal_token_is_rejected(self):
        self.replace('horizontal', lambda text: text.replace(
            '<alignment horizontal="right"/>', '<alignment horizontal="center"/>', 1))
        self.expect_failure('horizontal')

    def test_missing_apply_alignment_is_rejected(self):
        self.replace('combined', lambda text: text.replace('applyAlignment="1"', '', 1))
        self.expect_failure('lacks applyalignment')

    def test_alignment_on_the_default_style_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>',
            '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1">'
            '<alignment horizontal="center"/></xf>', 1))
        self.expect_failure('declares none')

    def test_undeclared_alignment_attribute_is_rejected(self):
        self.replace('combined', lambda text: text.replace(
            '<alignment horizontal="center" vertical="top"/>',
            '<alignment horizontal="center" vertical="top" wrapText="1"/>', 1))
        self.expect_failure('undeclared attributes')

    def test_two_identical_styles_on_different_entries_are_rejected(self):
        def transform(text):
            start = text.index('<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1">')
            end = text.index('</xf>', start) + len('</xf>')
            entry = text[start:end]
            return text[:end] + entry + text[end:]
        self.replace('shared', transform)
        self.expect_failure('the declared styles need')

    def test_missing_bold_flag_is_rejected(self):
        self.replace('styled', lambda text: text.replace('applyFont="1"', '', 1))
        self.expect_failure('bold without applyfont')

    def test_tampered_intent_is_rejected(self):
        path = self.directory / 'align.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'combined' and fields[2] == 'A2':
                fields[3] = 'left'
                lines[index] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('horizontal')

    def test_unknown_token_in_intent_is_rejected(self):
        path = self.directory / 'align.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        fields = lines[0].split('\t')
        fields[4] = 'middle'
        lines[0] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('unknown vertical token')

    def test_missing_cell_is_rejected(self):
        self.replace('horizontal', lambda text: text.replace('<c r="A2"', '<c r="Z9"', 1),
                     part='xl/worksheets/sheet1.xml')
        self.expect_failure('is absent from the sheet')

    def test_undeclared_workbook_is_rejected(self):
        shutil.copy(self.directory / 'default.xlsx', self.directory / 'stray.xlsx')
        self.expect_failure('unexpected workbooks')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'align.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
