#!/usr/bin/env python3
"""Negative controls for `tools/check_r98_print_records.py`.

Each test breaks one property the lane claims to protect — scope, quoting, absolute corners, rows-before-columns,
ordering, uniqueness, cross-table agreement — and asserts the lane notices. The undisturbed artifacts are
verified first, so a control cannot pass because the lane was already failing.

    PYTHONPATH=tools python3 -m unittest tools.test_r98_print_records

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

import check_r98_print_records as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r98/artifacts'
HAVE = (ARTIFACTS / 'r98.xlsx').is_file() and (ARTIFACTS / 'names.tsv').is_file()


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


def edit_both(folder: Path, old: str, new: str) -> None:
    """Change a string in both the workbook and the intent table, so only the structural rule can object."""
    rewrite(folder, 'r98.xlsx', 'xl/workbook.xml', lambda text: text.replace(old, new))
    edit_table(folder, 'names.tsv', lambda text: text.replace(old, new))


def rows_of(path: Path) -> list[list[str]]:
    return [line.split('\t') for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]


@unittest.skipUnless(HAVE, 'R98 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r98-records-')
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
        self.assertEqual(totals['records'], 6)
        self.assertEqual(totals['sheets'], 7)
        self.assertEqual(totals['reader_sheets'], 7)

    def test_a_dropped_record_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml',
                lambda text: text.replace(
                    '<definedName name="_xlnm.Print_Area" localSheetId="3">Records!$A$1:$A$3</definedName>', ''))
        self.assertFails('defined names')

    def test_swapped_entries_are_caught(self) -> None:
        def swap(text: str) -> str:
            first = '<definedName name="_xlnm.Print_Area" localSheetId="0">Fit!$A$1:$C$10</definedName>'
            second = '<definedName name="_xlnm.Print_Titles" localSheetId="0">Fit!$1:$3,Fit!$A:$B</definedName>'
            return text.replace(first + second, second + first)
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml', swap)
        self.assertFails('in that position')

    def test_a_changed_scope_is_caught(self) -> None:
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml',
                lambda text: text.replace('name="_xlnm.Print_Area" localSheetId="1"',
                                          'name="_xlnm.Print_Area" localSheetId="2"'))
        self.assertFails('localSheetId')

    def test_an_unquoted_spaced_name_is_caught(self) -> None:
        edit_both(self.folder, "'Fit Two'!", "Fit Two!")
        self.assertFails('does not start with')

    def test_a_missing_dollar_is_caught(self) -> None:
        edit_both(self.folder, 'Records!$A$1:$A$3', 'Records!A1:$A$3')
        self.assertFails('absolute A1 form')

    def test_columns_before_rows_are_caught(self) -> None:
        edit_both(self.folder, 'Fit!$1:$3,Fit!$A:$B', 'Fit!$A:$B,Fit!$1:$3')
        self.assertFails('rows must be spelled before columns')

    def test_defined_names_before_sheets_are_caught(self) -> None:
        def move(text: str) -> str:
            start = text.index('<definedNames>')
            end = text.index('</definedNames>') + len('</definedNames>')
            block = text[start:end]
            text = text[:start] + text[end:]
            return text.replace('<sheets>', block + '<sheets />').replace('<sheets />', '<sheets>', 1)
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml', move)
        self.assertFails('must follow sheets')

    def test_a_duplicated_record_is_caught(self) -> None:
        extra = '<definedName name="_xlnm.Print_Area" localSheetId="1">\'Fit Two\'!$B$2:$D$5</definedName>'
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml',
                lambda text: text.replace('</definedNames>', extra + '</definedNames>'))
        edit_table(self.folder, 'names.tsv',
                   lambda text: text + "_xlnm.Print_Area\t1\t'Fit Two'!$B$2:$D$5\t1\n")
        self.assertFails('twice in one scope')

    def test_an_area_disagreement_between_tables_is_caught(self) -> None:
        def drop_area(text: str) -> str:
            out = []
            for line in text.splitlines(True):
                parts = line.rstrip('\n').split('\t')
                if parts[0] == 'Records':
                    parts[10] = '-'
                out.append('\t'.join(parts) + '\n')
            return ''.join(out)
        edit_table(self.folder, 'print.tsv', drop_area)
        self.assertFails('area=')

    def test_a_title_disagreement_between_tables_is_caught(self) -> None:
        def drop_titles(text: str) -> str:
            out = []
            for line in text.splitlines(True):
                parts = line.rstrip('\n').split('\t')
                if parts[0] == 'Records':
                    parts[11] = '-'
                out.append('\t'.join(parts) + '\n')
            return ''.join(out)
        edit_table(self.folder, 'print.tsv', drop_titles)
        self.assertFails('titles disagree')

    def test_a_renamed_record_in_the_table_is_caught(self) -> None:
        edit_table(self.folder, 'names.tsv',
                   lambda text: text.replace('_xlnm.Print_Area\t0\t', '_xlnm.Print_Areas\t0\t'))
        self.assertFails('in that position')

    def test_a_record_declared_as_a_user_name_is_caught(self) -> None:
        edit_table(self.folder, 'names.tsv',
                   lambda text: text.replace('_xlnm.Print_Titles\t0\t', '_xlnm.Print_Titles\tglobal\t'))
        self.assertFails('localSheetId')

    def test_a_user_name_after_the_records_is_caught(self) -> None:
        total = '<definedName name="Total">SUM(Plain!$A$1:$A$9)</definedName>'
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml',
                lambda text: text.replace(total, '').replace('</definedNames>', total + '</definedNames>'))
        edit_table(self.folder, 'names.tsv',
                   lambda text: ''.join(line + '\n' for line in text.splitlines() if not line.startswith('Total'))
                   + 'Total\tglobal\tSUM(Plain!$A$1:$A$9)\t0\n')
        self.assertFails('user names must be written before')

    def test_a_missing_table_is_caught(self) -> None:
        (self.folder / 'names.tsv').unlink()
        self.assertFails('missing intent table')

    def test_a_short_row_is_caught(self) -> None:
        edit_table(self.folder, 'names.tsv',
                   lambda text: text.replace('SUM(Plain!$A$1:$A$9)\t0\n', 'SUM(Plain!$A$1:$A$9)\n'))
        self.assertFails('fields, expected 4')

    def test_a_global_name_the_reader_cannot_see_is_caught(self) -> None:
        # The intent table claims a workbook name the file does not carry; the raw lane is the first to notice.
        edit_table(self.folder, 'names.tsv', lambda text: text.replace('Total\tglobal', 'Totals\tglobal'))
        self.assertFails('in that position')

    def test_a_record_exposed_as_a_workbook_name_reaches_the_reader(self) -> None:
        # The reader lifts scoped records onto the sheet; a record with no scope would stay a workbook name.
        rewrite(self.folder, 'r98.xlsx', 'xl/workbook.xml',
                lambda text: text.replace('name="_xlnm.Print_Area" localSheetId="0"',
                                          'name="_xlnm.Print_Area"'))
        self.assertFails('localSheetId')

    def test_the_foreign_fixture_is_present_and_scoped(self) -> None:
        (self.folder / 'r98-foreign.xlsx').unlink()
        self.assertFails('r98-foreign.xlsx')


if __name__ == '__main__':
    unittest.main()
