#!/usr/bin/env python3
"""Negative controls for `tools/check_r99_outline.py`.

Each test breaks one property the lane claims to protect — the presence of `outlinePr`, its position in
`CT_SheetPr`, its attribute set, a row/column level, a collapsed marker, the hidden/collapsed distinction, the
one-element-per-column rule, and completeness of the intent table — and asserts the lane notices. The
undisturbed artifacts are verified first, so a control can never pass because the lane was already failing.

    PYTHONPATH=tools python3 -m unittest tools.test_r99_outline

Skips when `testing/build/r99/artifacts` is absent (it is produced by `tools/run_r99_checks.py`).
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

import check_r99_outline as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r99/artifacts'
HAVE = (ARTIFACTS / 'r99.xlsx').is_file() and (ARTIFACTS / 'outline.tsv').is_file()


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


def set_field(folder: Path, name: str, key_column: int, key: str, index: int, value: str) -> None:
    def transform(text: str) -> str:
        out = []
        for line in text.splitlines(True):
            parts = line.rstrip('\n').split('\t')
            if parts[key_column] == key:
                parts[index] = value
            out.append('\t'.join(parts) + '\n')
        return ''.join(out)
    edit_table(folder, name, transform)


@unittest.skipUnless(HAVE, 'R99 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r99-outline-')
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
        self.assertEqual(totals['outline_sheets'], 3)
        self.assertEqual(totals['grouped_sheets'], 4)
        self.assertEqual(totals['levels'], 15)
        self.assertEqual(totals['reader_sheets'], 6)

    def test_dropped_outline_pr_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<outlinePr summaryBelow="0"/>', ''))
        self.assertFails('no <outlinePr>')

    def test_an_invented_outline_pr_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet5.xml',
                lambda text: text.replace('<sheetData>', '<sheetPr><outlinePr summaryBelow="0"/></sheetPr><sheetData>'))
        self.assertFails('yet an <outlinePr> is present')

    def test_sheet_pr_children_out_of_order_are_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet3.xml',
                lambda text: text.replace(
                    '<sheetPr><tabColor rgb="FFCC00FF"/><outlinePr summaryBelow="0"/>'
                    '<pageSetUpPr fitToPage="1"/></sheetPr>',
                    '<sheetPr><tabColor rgb="FFCC00FF"/><pageSetUpPr fitToPage="1"/>'
                    '<outlinePr summaryBelow="0"/></sheetPr>'))
        self.assertFails('out of CT_SheetPr order')

    def test_a_changed_summary_flag_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<outlinePr summaryBelow="0"/>', '<outlinePr summaryBelow="1"/>'))
        self.assertFails('outlinePr attributes')

    def test_an_extra_outline_attribute_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<outlinePr summaryBelow="0"/>',
                                          '<outlinePr summaryBelow="0" summaryRight="0"/>'))
        self.assertFails('outlinePr attributes')

    def test_a_missing_collapsed_column_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<col min="4" max="4" collapsed="1"/>', ''))
        self.assertFails('column 4')

    def test_a_collapsed_span_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<col min="2" max="2" outlineLevel="1"/>',
                                          '<col min="2" max="3" outlineLevel="1"/>'))
        self.assertFails('written as a span')

    def test_a_changed_row_level_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet2.xml',
                lambda text: text.replace('<row r="3" outlineLevel="3">', '<row r="3" outlineLevel="2">'))
        self.assertFails('outlineLevel')

    def test_a_dropped_row_marker_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<row r="5" collapsed="1">', '<row r="5">'))
        self.assertFails('row 5 collapsed')

    def test_an_invented_hidden_flag_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<row r="2" outlineLevel="1">', '<row r="2" hidden="1" outlineLevel="1">'))
        self.assertFails('row 2 hidden')

    def test_grouping_the_table_does_not_describe_is_caught(self) -> None:
        rewrite(self.folder, 'r99.xlsx', 'xl/worksheets/sheet4.xml',
                lambda text: text.replace('<row r="1">', '<row r="1" outlineLevel="1">'))
        self.assertFails('carries grouping the table does not describe')

    def test_a_dropped_level_row_is_caught(self) -> None:
        drop = lambda text: ''.join(  # noqa: E731
            line for line in text.splitlines(True)
            if not line.startswith('Grouped\trow\t4\t'))
        edit_table(self.folder, 'levels.tsv', drop)
        self.assertFails('carries grouping the table does not describe')

    def test_a_changed_level_in_the_table_is_caught(self) -> None:
        set_field(self.folder, 'levels.tsv', 2, '7', 3, '2')
        self.assertFails('outlineLevel')

    def test_a_changed_max_level_is_caught(self) -> None:
        set_field(self.folder, 'outline.tsv', 0, 'Nested', 8, '1')
        self.assertFails('max_level')

    def test_a_non_numeric_count_is_caught(self) -> None:
        set_field(self.folder, 'outline.tsv', 0, 'Nested', 4, 'three')
        self.assertFails('is not a count')

    def test_a_dropped_sheet_row_is_caught(self) -> None:
        drop = lambda text: ''.join(  # noqa: E731
            line for line in text.splitlines(True) if not line.startswith('FitOnly\t'))
        edit_table(self.folder, 'outline.tsv', drop)
        self.assertFails('outline.tsv describes')

    def test_a_short_row_is_caught(self) -> None:
        edit_table(self.folder, 'levels.tsv',
                   lambda text: text.replace('Grouped\trow\t2\t1\t0\t0\n', 'Grouped\trow\t2\t1\t0\n'))
        self.assertFails('fields, expected 6')

    def test_a_missing_table_is_caught(self) -> None:
        (self.folder / 'levels.tsv').unlink()
        self.assertFails('missing intent table')

    def test_the_plain_workbook_gaining_a_sheet_pr_is_caught(self) -> None:
        rewrite(self.folder, 'r99-plain.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<sheetData>', '<sheetPr><outlinePr summaryBelow="0"/></sheetPr><sheetData>'))
        self.assertFails('gained sheetPr')

    def test_a_level_past_the_ceiling_in_the_table_is_caught(self) -> None:
        set_field(self.folder, 'levels.tsv', 2, '7', 3, '9')
        self.assertFails('past the ceiling')


if __name__ == '__main__':
    unittest.main()
