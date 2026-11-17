#!/usr/bin/env python3
"""Negative controls for `tools/check_r98_sheet_print.py`.

Each test breaks one thing the checker claims to protect and asserts the checker notices. The undisturbed
artifacts are verified first, so a control can never pass because the lane was already failing.

    PYTHONPATH=tools python3 -m unittest tools.test_r98_sheet_print

Skips when `testing/build/r98/artifacts` is absent (it is produced by `tools/run_r98_checks.py`).
"""
from __future__ import annotations

import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

import check_r98_sheet_print as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r98/artifacts'
HAVE = (ARTIFACTS / 'r98.xlsx').is_file() and (ARTIFACTS / 'print.tsv').is_file()


def rewrite(folder: Path, book: str, part: str, transform) -> None:
    path = folder / book
    with zipfile.ZipFile(path) as archive:
        entries = {item.filename: archive.read(item.filename) for item in archive.infolist()}
    entries[part] = transform(entries[part].decode('utf-8')).encode('utf-8')
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, body in entries.items():
            archive.writestr(name, body)


def edit_table(folder: Path, name: str, transform) -> None:
    path = folder / name
    path.write_text(transform(path.read_text(encoding='utf-8')), encoding='utf-8')


def set_field(folder: Path, name: str, sheet: str, index: int, value: str) -> None:
    """Rewrite one field of one row, keeping the column count intact."""
    def transform(text: str) -> str:
        out = []
        for line in text.splitlines(True):
            parts = line.rstrip('\n').split('\t')
            if parts[0] == sheet:
                parts[index] = value
            out.append('\t'.join(parts) + '\n')
        return ''.join(out)
    edit_table(folder, name, transform)


@unittest.skipUnless(HAVE, 'R98 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r98-print-')
        self.folder = Path(self._temporary.name)
        shutil.copytree(ARTIFACTS, self.folder, dirs_exist_ok=True)

    def tearDown(self) -> None:
        self._temporary.cleanup()

    def assertFails(self, fragment: str, *, reader: bool = True) -> None:
        with self.assertRaises(lane.Fail) as caught:
            lane.verify(self.folder, reader)
        self.assertIn(fragment, str(caught.exception))

    def test_undisturbed_artifacts_pass(self) -> None:
        totals = lane.verify(ARTIFACTS, True)
        self.assertEqual(totals['fit_sheets'], 3)
        self.assertEqual(totals['plain_sheets'], 4)
        self.assertEqual(totals['reader_sheets'], 7)

    def test_fit_counts_without_the_flag_are_refused(self) -> None:
        set_field(self.folder, 'print.tsv', 'Fit', 2, '0')   # fitToPage off while fitToWidth stays 1
        self.assertFails('fit counts without')

    def test_missing_page_setup_pr_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<pageSetUpPr fitToPage="1"/>', ''))
        self.assertFails('sheetPr children')

    def test_sheet_pr_children_out_of_order_are_caught(self) -> None:
        rewrite(
            self.folder, 'r98.xlsx', 'xl/worksheets/sheet6.xml',
            lambda text: text.replace(
                '<sheetPr><tabColor rgb="FF00B050"/><pageSetUpPr fitToPage="1"/></sheetPr>',
                '<sheetPr><pageSetUpPr fitToPage="1"/><tabColor rgb="FF00B050"/></sheetPr>',
            ),
        )
        self.assertFails('sheetPr children')

    def test_wrong_fit_width_in_the_file_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('fitToWidth="1"', 'fitToWidth="9"'))
        self.assertFails('fitToWidth')

    def test_invented_scale_is_caught(self) -> None:
        # Inserted in schema order so the attribute-order rule is not what fires.
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('paperSize="9"', 'paperSize="9" scale="90"'))
        self.assertFails('scale was written without being declared')

    def test_missing_use_first_page_number_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet3.xml',
                lambda text: text.replace(' useFirstPageNumber="1"', ''))
        self.assertFails('useFirstPageNumber')

    def test_wrong_orientation_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet3.xml',
                lambda text: text.replace('orientation="portrait"', 'orientation="landscape"'))
        self.assertFails('orientation')

    def test_page_setup_before_margins_is_caught(self) -> None:
        def move(text: str) -> str:
            start = text.index('<pageSetup')
            end = text.index('/>', start) + 2
            setup = text[start:end]
            text = text[:start] + text[end:]
            margin = text.index('<pageMargins')
            return text[:margin] + setup + text[margin:]
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet1.xml', move)
        self.assertFails('CT_Worksheet order')

    def test_page_setup_on_a_sheet_that_declares_none_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet7.xml',
                lambda text: text.replace('</sheetData>', '</sheetData><pageSetup paperSize="1"/>'))
        self.assertFails('pageSetup')

    def test_dropped_print_options_are_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet3.xml',
                lambda text: text.replace('<printOptions gridLines="1"/>', ''))
        self.assertFails('printOptions')

    def test_plain_workbook_gaining_a_sheet_pr_is_caught(self) -> None:
        rewrite(self.folder, 'r98-plain.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<sheetData>', '<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr><sheetData>'))
        self.assertFails('gained sheetPr')

    def test_plain_workbook_gaining_a_defined_name_is_caught(self) -> None:
        rewrite(self.folder, 'r98-plain.xlsx', 'xl/workbook.xml',
                lambda text: text.replace('</workbook>',
                                          '<definedNames><definedName name="X">A1</definedName></definedNames></workbook>'))
        self.assertFails('gained definedName')

    def test_first_page_number_mismatch_reaches_the_reader(self) -> None:
        set_field(self.folder, 'print.tsv', 'Numbered', 5, '6')
        self.assertFails('firstPageNumber')

    def test_a_dropped_sheet_row_is_caught(self) -> None:
        edit_table(self.folder, 'print.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True) if not line.startswith('Records\t')))
        self.assertFails('print.tsv describes')

    def test_an_extra_sheet_row_is_caught(self) -> None:
        edit_table(self.folder, 'print.tsv',
                   lambda text: text + 'Ghost\t0\t0\t-\t-\t-\t0\t-\t-\t-\t-\t-\t-\t0\t0\t0\t0\n')
        self.assertFails('print.tsv describes')

    def test_a_non_binary_flag_is_caught(self) -> None:
        set_field(self.folder, 'print.tsv', 'Fit', 2, '2')
        self.assertFails('is not 0/1')

    def test_a_missing_field_is_caught(self) -> None:
        edit_table(self.folder, 'print.tsv',
                   lambda text: text.replace('Fit\t1\t1\t1\t0\t', 'Fit\t1\t1\t1\t', 1))
        self.assertFails('fields, expected 17')

    def test_a_missing_table_is_caught(self) -> None:
        (self.folder / 'print.tsv').unlink()
        self.assertFails('missing intent table')

    def test_a_bad_paper_size_reaches_the_reader(self) -> None:
        set_field(self.folder, 'print.tsv', 'Fit', 8, '8')
        self.assertFails('paperSize')

    def test_fit_flag_dropped_from_the_reader_view_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet6.xml',
                lambda text: text.replace('<pageSetUpPr fitToPage="1"/>', '<pageSetUpPr fitToPage="0"/>'))
        self.assertFails('fitToPage')

    def test_page_setup_pr_without_the_flag_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('fitToPage="1"', 'fitToPage="0"'))
        self.assertFails('does not ask to fit to page')


if __name__ == '__main__':
    unittest.main()
