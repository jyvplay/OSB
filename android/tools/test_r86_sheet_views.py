#!/usr/bin/env python3
"""Negative controls for the R86 spreadsheet tab-colour and workbook-view verifier.

Each control copies a real workbook produced by the Kotlin probe, breaks exactly one thing, and asserts that
tools/check_r86_sheet_views.py refuses the result. The controls skip when the artifacts are absent (they are
produced by tools/run_r86_sheet_views.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r86_sheet_views -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r86_sheet_views as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r86sheet/artifacts'
WORKBOOKS = ('default', 'tab-colour', 'hide-tabs', 'first-sheet', 'zoom', 'hidden-only', 'view-mix')


def rewrite(source: Path, target: Path, transform) -> None:
    with zipfile.ZipFile(source) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, payload in entries:
            output.writestr(name, transform(name, payload))


def part_only(entry: str, transform):
    def apply(name, payload):
        if name != entry:
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


class SheetViewControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'views.tsv').is_file():
            self.skipTest('R86 sheet view artifacts are absent; run tools/run_r86_sheet_views.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r86-views-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in WORKBOOKS:
            shutil.copy(ARTIFACTS / f'{name}.xlsx', self.directory / f'{name}.xlsx')
        shutil.copy(ARTIFACTS / 'views.tsv', self.directory / 'views.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def replace(self, workbook: str, entry: str, transform):
        target = self.directory / f'{workbook}.xlsx'
        temporary = self.directory / f'{workbook}.xlsx.tmp'
        rewrite(target, temporary, part_only(entry, transform))
        shutil.move(str(temporary), str(target))

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def edit_intent(self, transform):
        path = self.directory / 'views.tsv'
        path.write_text(transform(path.read_text(encoding='utf-8').splitlines()), encoding='utf-8')

    def test_undisturbed_workbooks_pass(self):
        report = self.verify()
        self.assertEqual(report['workbooks'], 7)
        self.assertEqual(report['sheets'], 15)

    # --- tab colour ----------------------------------------------------------------------------------
    def test_removed_sheet_pr_is_rejected(self):
        self.replace('tab-colour', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('<sheetPr><tabColor rgb="FF00B050"/></sheetPr>', '', 1))
        self.expect_failure('missing <sheetpr>')

    def test_changed_colour_is_rejected(self):
        self.replace('tab-colour', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('rgb="FF00B050"', 'rgb="FF00B051"', 1))
        self.expect_failure('tabcolor rgb')

    def test_colour_without_the_alpha_prefix_is_rejected(self):
        self.replace('tab-colour', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('rgb="FF00B050"', 'rgb="00B050"', 1))
        self.expect_failure('tabcolor rgb')

    def test_sheet_pr_out_of_place_is_rejected(self):
        self.replace('tab-colour', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('<sheetPr><tabColor rgb="FF00B050"/></sheetPr>', '', 1)
                     .replace('</worksheet>', '<sheetPr><tabColor rgb="FF00B050"/></sheetPr></worksheet>', 1))
        self.expect_failure('first child')

    def test_invented_sheet_pr_is_rejected(self):
        self.replace('default', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('<worksheet ', '<worksheet ', 1).replace(
                         '><sheetData>', '><sheetPr><tabColor rgb="FF112233"/></sheetPr><sheetData>', 1))
        self.expect_failure('without a tab colour')

    # --- workbook view -------------------------------------------------------------------------------
    def test_removed_book_views_are_rejected(self):
        self.replace('hide-tabs', 'xl/workbook.xml',
                     lambda text: text.replace('<bookViews><workbookView showSheetTabs="0"/></bookViews>', '', 1))
        self.expect_failure('bookviews> presence')

    def test_invented_book_views_are_rejected(self):
        self.replace('default', 'xl/workbook.xml',
                     lambda text: text.replace('<sheets>', '<bookViews><workbookView/></bookViews><sheets>', 1))
        self.expect_failure('bookviews> presence')

    def test_inverted_show_sheet_tabs_is_rejected(self):
        self.replace('hide-tabs', 'xl/workbook.xml',
                     lambda text: text.replace('showSheetTabs="0"', 'showSheetTabs="1"', 1))
        self.expect_failure('showsheettabs')

    def test_invented_active_tab_is_rejected(self):
        self.replace('hide-tabs', 'xl/workbook.xml',
                     lambda text: text.replace('<workbookView showSheetTabs="0"/>',
                                               '<workbookView showSheetTabs="0" activeTab="0"/>', 1))
        self.expect_failure('activeTab belongs to the hidden-sheet case only')

    def test_removed_active_tab_is_rejected(self):
        self.replace('hidden-only', 'xl/workbook.xml',
                     lambda text: text.replace(' activeTab="0"', '', 1))
        self.expect_failure('first visible sheet index')

    def test_wrong_active_tab_is_rejected(self):
        self.replace('hidden-only', 'xl/workbook.xml',
                     lambda text: text.replace('activeTab="0"', 'activeTab="1"', 1))
        self.expect_failure('first visible sheet index')

    def test_invented_show_sheet_tabs_is_rejected(self):
        self.replace('first-sheet', 'xl/workbook.xml',
                     lambda text: text.replace('<workbookView firstSheet="2"/>',
                                               '<workbookView showSheetTabs="1" firstSheet="2"/>', 1))
        self.expect_failure('an undeclared showsheettabs must stay unwritten')

    def test_invented_first_sheet_is_rejected(self):
        self.replace('hide-tabs', 'xl/workbook.xml',
                     lambda text: text.replace('<workbookView showSheetTabs="0"/>',
                                               '<workbookView showSheetTabs="0" firstSheet="1"/>', 1))
        self.expect_failure('an undeclared firstsheet must stay unwritten')

    def test_wrong_first_sheet_is_rejected(self):
        self.replace('first-sheet', 'xl/workbook.xml',
                     lambda text: text.replace('firstSheet="2"', 'firstSheet="1"', 1))
        self.expect_failure('firstsheet')

    def test_removed_state_is_rejected(self):
        self.replace('view-mix', 'xl/workbook.xml',
                     lambda text: text.replace(' state="hidden"', '', 1))
        self.expect_failure('state')

    # --- sheet view ----------------------------------------------------------------------------------
    def test_removed_zoom_attribute_is_rejected(self):
        self.replace('zoom', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace(' zoomScale="150"', '', 1))
        self.expect_failure('zoomscale')

    def test_removed_sheet_views_are_rejected(self):
        self.replace('zoom', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace(
                         '<sheetViews><sheetView workbookViewId="0" zoomScale="150"/></sheetViews>', '', 1))
        self.expect_failure('sheetviews> presence')

    def test_changed_zoom_is_rejected(self):
        self.replace('zoom', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('zoomScale="150"', 'zoomScale="160"', 1))
        self.expect_failure('zoomscale')

    def test_removed_pane_is_rejected(self):
        self.replace('zoom', 'xl/worksheets/sheet2.xml',
                     lambda text: text.replace(
                         '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>', '', 1))
        self.expect_failure('missing frozen <pane>')

    def test_invented_pane_is_rejected(self):
        self.replace('zoom', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('zoomScale="150"/></sheetViews>',
                                               'zoomScale="150"><pane ySplit="1"/></sheetView></sheetViews>', 1))
        self.expect_failure('no pane was declared')

    def test_undeclared_sheet_view_attribute_is_rejected(self):
        self.replace('zoom', 'xl/worksheets/sheet1.xml',
                     lambda text: text.replace('zoomScale="150"', 'zoomScale="150" tabSelected="1"', 1))
        self.expect_failure('carries undeclared attributes')

    # --- a hidden sheet keeps its data ----------------------------------------------------------------
    def test_dropped_cell_in_a_hidden_sheet_is_rejected(self):
        self.replace('view-mix', 'xl/worksheets/sheet2.xml',
                     lambda text: text.replace(
                         '<c r="B2" t="inlineStr"><is><t xml:space="preserve">kept</t></is></c>', '', 1))
        self.expect_failure('non-empty cells')

    # --- intent table and package bookkeeping ----------------------------------------------------------
    def test_tampered_intent_zoom_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'zoom' and fields[2] == 'Wide':
                    fields[5] = '200'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('zoomscale')

    def test_tampered_intent_show_tabs_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'hide-tabs':
                    fields[7] = 'true'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('showsheettabs')

    def test_inconsistent_workbook_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'default' and fields[1] == '1':
                    fields[8] = '1'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('not constant')

    def test_truncated_intent_row_is_rejected(self):
        self.edit_intent(lambda lines: '\n'.join(lines[:1] + ['\t'.join(lines[1].split('\t')[:5])]) + '\n')
        self.expect_failure('fields, expected 10')

    def test_undeclared_workbook_is_rejected(self):
        shutil.copy(self.directory / 'default.xlsx', self.directory / 'stray.xlsx')
        self.expect_failure('!= declared')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'views.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
