#!/usr/bin/env python3
"""Negative controls for the R90 third-party lanes (`docx-preview` CSS, `exceljs` font model).

The fixtures are synthesised here instead of read from the probe artifacts, so these tests pin the lane
*logic* — including its refusal paths — without needing Node, a JDK or a previous probe run.

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r90_lanes -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

import check_r87_docx_render as render
import check_r87_sheet_exceljs as exceljs


def docx_row(name, paragraph, run, text, align, highlight):
    return [name, str(paragraph), str(run), text, align, highlight, '11', '-', 'false', 'false', '-', '-']


def xlsx_row(name, sheet, ref, underline, strike):
    return [name, sheet, ref, 'false', 'false', underline, strike, '-', '-', '-']


DOCX_TABLE = '\n'.join([
    '\t'.join(docx_row('deco', 0, 0, 'plain', '-', '-')),
    '\t'.join(docx_row('deco', 0, 1, 'marked', '-', 'yellow')),
    '\t'.join(docx_row('deco', 0, 2, 'raised', 'superscript', '-')),
]) + '\n'

XLSX_TABLE = '\n'.join([
    '\t'.join(xlsx_row('sheet', 'S', 'A1', 'true', 'false')),
    '\t'.join(xlsx_row('sheet', 'S', 'A2', 'false', 'true')),
    '\t'.join(xlsx_row('sheet', 'S', 'A3', 'true', 'true')),
]) + '\n'


def docx_payload(styles):
    return {'files': [{'name': 'deco.docx', 'paragraphs': [
        {'runs': [{'text': text, 'style': style} for (text, _), style in zip(
            [('plain', None), ('marked', None), ('raised', None)], styles)]}]}]}


def xlsx_payload(underlines, strikes):
    cells = [{'ref': f'A{index}', 'underline': u, 'strike': s, 'bold': False, 'italic': False, 'color': None}
             for index, (u, s) in enumerate(zip(underlines, strikes), start=1)]
    return {'files': [{'name': 'sheet.xlsx', 'sheets': [{'name': 'S', 'cells': cells}]}]}


class DocxLaneControls(unittest.TestCase):
    def setUp(self):
        self.folder = Path(tempfile.mkdtemp(prefix='r90-lane-docx-'))
        (self.folder / 'decorations.tsv').write_text(DOCX_TABLE, encoding='utf-8')

    def tearDown(self):
        shutil.rmtree(self.folder, ignore_errors=True)

    def check(self, styles):
        return render.check_r90(docx_payload(styles), self.folder)

    def fail(self, styles):
        with self.assertRaises(render.Fail):
            self.check(styles)

    def test_full_agreement_passes(self):
        totals = self.check([{}, {'background-color': 'yellow'}, {}])
        self.assertEqual(totals, (3, 1, 1))

    def test_missing_background_is_rejected(self):
        self.fail([{}, {}, {}])

    def test_wrong_background_is_rejected(self):
        self.fail([{}, {'background-color': 'green'}, {}])

    def test_undeclared_background_is_rejected(self):
        self.fail([{'background-color': 'yellow'}, {'background-color': 'yellow'}, {}])

    def test_case_sensitivity_is_enforced(self):
        self.fail([{}, {'background-color': 'Yellow'}, {}])

    def test_extra_run_is_rejected(self):
        payload = docx_payload([{}, {'background-color': 'yellow'}, {}])
        payload['files'][0]['paragraphs'][0]['runs'].append({'text': 'ghost', 'style': {}})
        with self.assertRaises(render.Fail):
            render.check_r90(payload, self.folder)

    def test_unknown_file_is_rejected(self):
        payload = docx_payload([{}, {'background-color': 'yellow'}, {}])
        payload['files'][0]['name'] = 'other.docx'
        with self.assertRaises(render.Fail):
            render.check_r90(payload, self.folder)

    def test_rendered_alignment_must_be_sup(self):
        self.fail([{}, {'background-color': 'yellow'}, {'verticalAlign': 'sub'}])

    def test_rendered_alignment_is_accepted_when_the_renderer_supports_it(self):
        totals = self.check([{}, {'background-color': 'yellow'}, {'verticalAlign': 'sup'}])
        self.assertEqual(totals, (3, 1, 0))


class SheetLaneControls(unittest.TestCase):
    def setUp(self):
        self.folder = Path(tempfile.mkdtemp(prefix='r90-lane-xlsx-'))
        (self.folder / 'decorations_xlsx.tsv').write_text(XLSX_TABLE, encoding='utf-8')

    def tearDown(self):
        shutil.rmtree(self.folder, ignore_errors=True)

    def check(self, underlines, strikes):
        return exceljs.check_r90(xlsx_payload(underlines, strikes), self.folder, 10)

    def fail(self, underlines, strikes):
        with self.assertRaises(exceljs.Fail):
            self.check(underlines, strikes)

    def test_full_agreement_passes(self):
        self.assertEqual(self.check([True, False, True], [False, True, True]),
                         {'workbooks': 1, 'cells': 3, 'decorated': 3})

    def test_missing_underline_is_rejected(self):
        self.fail([False, False, True], [False, True, True])

    def test_invented_strike_is_rejected(self):
        self.fail([True, False, True], [False, False, True])

    def test_extra_cell_is_tolerated_but_missing_cell_is_not(self):
        payload = xlsx_payload([True, False, True], [False, True, True])
        payload['files'][0]['sheets'][0]['cells'].pop()
        with self.assertRaises(exceljs.Fail):
            exceljs.check_r90(payload, self.folder, 10)

    def test_wrong_member_count_is_rejected(self):
        with (self.folder / 'decorations_xlsx.tsv').open('a', encoding='utf-8') as table:
            table.write('sheet\tS\tA4\n')        # 3 fields instead of 10
        self.fail([True, False, True], [False, True, True])

    def test_unread_workbook_is_rejected(self):
        payload = xlsx_payload([True, False, True], [False, True, True])
        payload['files'][0]['name'] = 'other.xlsx'
        with self.assertRaises(exceljs.Fail):
            exceljs.check_r90(payload, self.folder, 10)


if __name__ == '__main__':
    unittest.main()
