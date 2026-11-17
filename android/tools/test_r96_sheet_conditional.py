#!/usr/bin/env python3
"""Negative controls for the R96 conditional-format verifier.

Each test tampers one thing — a worksheet part or one intent cell — and asserts the verifier refuses it. The
undisturbed artifacts are verified first, so a verifier that rejects everything cannot pass, and every tamper is
drawn from a failure mode a spreadsheet engine actually punishes (a dangling `dxfId`, a duplicate priority, a block
out of CT_Worksheet order, an attribute in the wrong position).

Skips when the R96 artifacts are absent (CI regenerates them with `tools/run_r96_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r96_sheet_conditional -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r96_sheet_conditional as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r96/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None
SHEET1 = 'xl/worksheets/sheet1.xml'
SHEET2 = 'xl/worksheets/sheet2.xml'
PLAIN = 'xl/worksheets/sheet3.xml'

GRID = ('<conditionalFormatting sqref="A1:A10"><cfRule type="cellIs" dxfId="0" priority="1" stopIfTrue="1" '
        'operator="greaterThan"><formula>5</formula></cfRule><cfRule type="expression" dxfId="1" priority="2" '
        'stopIfTrue="0"><formula>MOD(ROW(),2)=0</formula></cfRule></conditionalFormatting>')
SCALE = ('<conditionalFormatting sqref="B1:B5"><cfRule type="colorScale" priority="3"><colorScale><cfvo '
         'type="min"/><cfvo type="max"/><color rgb="FFF8696B"/><color rgb="FF63BE7B"/></colorScale></cfRule>'
         '</conditionalFormatting>')
BAR = ('<conditionalFormatting sqref="C1:C9"><cfRule type="dataBar" priority="4"><dataBar><cfvo type="num" '
       'val="0"/><cfvo type="num" val="100"/><color rgb="FF638EC6"/></dataBar></cfRule>'
       '</conditionalFormatting>')
TOP = ('<conditionalFormatting sqref="D1:D4"><cfRule type="top10" dxfId="2" priority="5" percent="0" '
       'bottom="1" rank="3"/></conditionalFormatting>')
TWO = ('<conditionalFormatting sqref="A1:C6"><cfRule type="cellIs" dxfId="0" priority="7" operator="between">'
       '<formula>10</formula><formula>20</formula></cfRule><cfRule type="expression" dxfId="0" priority="8" '
       'stopIfTrue="1"><formula>$A1&gt;0</formula></cfRule></conditionalFormatting>')
TWO_SCALE = ('<conditionalFormatting sqref="E1:E9 D1:D4"><cfRule type="colorScale" priority="9"><colorScale>'
             '<cfvo type="min"/><cfvo type="percent" val="50"/><cfvo type="max"/><color rgb="FFF8696B"/>'
             '<color rgb="FFFFEB84"/><color rgb="FF63BE7B"/></colorScale></cfRule></conditionalFormatting>')


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class ConditionalControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'conditional.tsv').is_file():
            raise unittest.SkipTest('R96 artifacts are absent; run tools/run_r96_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r96-cf-'))

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
        return (self.folder / 'conditional.tsv').read_text(encoding='utf-8').splitlines()

    def write_lines(self, lines: list[str]) -> None:
        (self.folder / 'conditional.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    # --- the untouched artifacts -------------------------------------------------
    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['rules'], 8)
        self.assertEqual(totals['blocks'], 6)
        self.assertEqual(totals['rule_sheets'], 2)
        self.assertEqual(totals['plain_sheets'], 2)
        self.assertEqual(totals['dxf_refs'], 5)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_rules'], 8)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    # --- structure ---------------------------------------------------------------
    def test_block_removed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace(GRID, ''))
        self.fail()

    def test_block_order_swapped(self):
        def swap(xml: str) -> str:
            return xml.replace(SCALE, '\x00').replace(BAR, SCALE).replace('\x00', BAR)
        self.patch('r96.xlsx', SHEET1, swap)
        self.fail()

    def test_sqref_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('sqref="A1:A10"', 'sqref="A1:A11"'))
        self.fail()

    def test_sqref_token_order_changed(self):
        self.patch('r96.xlsx', SHEET2, lambda xml: xml.replace('sqref="E1:E9 D1:D4"', 'sqref="D1:D4 E1:E9"'))
        self.fail()

    def test_block_attribute_invented(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('<conditionalFormatting sqref="A1:A10">',
                                                                '<conditionalFormatting sqref="A1:A10" pivot="1">'))
        self.fail()

    def test_block_before_sheet_data(self):
        def move(xml: str) -> str:
            return xml.replace(GRID, '').replace('<sheetData', GRID + '<sheetData', 1)
        self.patch('r96.xlsx', SHEET1, move)
        self.fail()

    def test_block_injected_into_rule_free_sheet(self):
        self.patch('r96.xlsx', PLAIN, lambda xml: xml.replace('</sheetData>', '</sheetData>' + GRID))
        self.fail()

    def test_block_injected_into_rule_free_workbook(self):
        self.patch('r96-plain.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace('</sheetData>', '</sheetData>' + GRID))
        self.fail()

    # --- rules -------------------------------------------------------------------
    def test_rule_removed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace(
            '<cfRule type="expression" dxfId="1" priority="2" stopIfTrue="0"><formula>MOD(ROW(),2)=0</formula>'
            '</cfRule>', ''))
        self.fail()

    def test_rule_order_swapped(self):
        def swap(xml: str) -> str:
            first = ('<cfRule type="cellIs" dxfId="0" priority="1" stopIfTrue="1" operator="greaterThan">'
                     '<formula>5</formula></cfRule>')
            second = ('<cfRule type="expression" dxfId="1" priority="2" stopIfTrue="0">'
                      '<formula>MOD(ROW(),2)=0</formula></cfRule>')
            return xml.replace(first + second, second + first)
        self.patch('r96.xlsx', SHEET1, swap)
        self.fail()

    def test_duplicate_priority(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('priority="2"', 'priority="1"'))
        self.fail()

    def test_priority_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('priority="5"', 'priority="6"'))
        self.fail()

    def test_attribute_order_broken(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace(
            '<cfRule type="top10" dxfId="2" priority="5"', '<cfRule dxfId="2" type="top10" priority="5"'))
        self.fail()

    def test_attribute_dropped(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace(
            '<cfRule type="expression" dxfId="1" priority="2" stopIfTrue="0">',
            '<cfRule type="expression" dxfId="1" priority="2">'))
        self.fail()

    def test_flag_value_flipped(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('stopIfTrue="0"', 'stopIfTrue="1"'))
        self.fail()

    def test_flag_invented(self):
        self.patch('r96.xlsx', SHEET2, lambda xml: xml.replace(
            '<cfRule type="expression" dxfId="0" priority="8"', '<cfRule type="expression" dxfId="0" bottom="0" priority="8"'))
        self.fail()

    def test_operator_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('operator="greaterThan"', 'operator="lessThan"'))
        self.fail()

    def test_operator_dropped(self):
        self.patch('r96.xlsx', SHEET2, lambda xml: xml.replace(' operator="between"', ''))
        self.fail()

    def test_rank_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('rank="3"', 'rank="4"'))
        self.fail()

    def test_rank_invented(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('priority="4"', 'priority="4" rank="2"'))
        self.fail()

    def test_type_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('type="cellIs"', 'type="containsText"'))
        self.fail()

    # --- formulas ----------------------------------------------------------------
    def test_formula_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('<formula>5</formula>', '<formula>6</formula>'))
        self.fail()

    def test_second_formula_dropped(self):
        self.patch('r96.xlsx', SHEET2, lambda xml: xml.replace('<formula>10</formula><formula>20</formula>',
                                                                '<formula>10</formula>'))
        self.fail()

    def test_formula_markup_broken(self):
        self.patch('r96.xlsx', SHEET2, lambda xml: xml.replace('<formula>$A1&gt;0</formula>',
                                                                '<formula>$A1<0</formula>'))
        self.fail()

    def test_comparison_is_escaped_on_disk(self):
        # A '>' inside formula text need not be escaped to be well-formed XML, but the writer escapes it and the
        # verifier reads the intent back through the parser, so the escape is a property worth pinning.
        with zipfile.ZipFile(self.folder / 'r96.xlsx') as archive:
            raw = archive.read(SHEET2).decode('utf-8')
        self.assertIn('<formula>$A1&gt;0</formula>', raw)
        self.assertNotIn('<formula>$A1>0</formula>', raw)

    # --- differential-format references -----------------------------------------
    def test_dxf_reference_dangling(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('dxfId="2"', 'dxfId="9"'))
        self.fail()

    def test_dxf_reference_dropped(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace(' type="cellIs" dxfId="0"', ' type="cellIs"'))
        self.fail()

    def test_dxf_reference_invented_on_scale(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('type="colorScale" priority="3"',
                                                               'type="colorScale" dxfId="0" priority="3"'))
        self.fail()

    # --- scale content -----------------------------------------------------------
    def test_cfvo_kind_changed(self):
        self.patch('r96.xlsx', SHEET2, lambda xml: xml.replace('<cfvo type="percent" val="50"/>',
                                                               '<cfvo type="num" val="50"/>'))
        self.fail()

    def test_cfvo_value_dropped(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('<cfvo type="num" val="0"/>', '<cfvo type="num"/>'))
        self.fail()

    def test_cfvo_value_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('val="100"', 'val="101"'))
        self.fail()

    def test_colour_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('rgb="FFF8696B"', 'rgb="FFF8696C"'))
        self.fail()

    def test_colour_alpha_changed(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('rgb="FFF8696B"', 'rgb="00F8696B"'))
        self.fail()

    def test_colour_dropped(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace('<color rgb="FF63BE7B"/>', ''))
        self.fail()

    def test_scale_injected_into_cell_is(self):
        self.patch('r96.xlsx', SHEET1, lambda xml: xml.replace(
            '<formula>5</formula></cfRule>', '<formula>5</formula><colorScale><cfvo type="min"/></colorScale></cfRule>'))
        self.fail()

    # --- intent table ------------------------------------------------------------
    def test_intent_field_count_wrong(self):
        lines = self.lines()
        lines[0] = lines[0] + '\textra'
        self.write_lines(lines)
        self.fail()

    def test_intent_block_missing(self):
        lines = [line for line in self.lines() if not line.startswith('r96.xlsx\tGrid\t1\t3\t')]
        self.write_lines(lines)
        self.fail()

    def test_intent_rule_undeclared(self):
        lines = [line for line in self.lines()
                 if not line.startswith('r96.xlsx\tTwo\t1\t1\t')]
        self.write_lines(lines)
        self.fail()

    def test_intent_dxf_reference_wrong(self):
        lines = [line.replace('\t2\t;i;;;9C0006;', '\t1\t;i;;;9C0006;') if line.startswith('r96.xlsx\tGrid\t1\t3\t')
                 else line for line in self.lines()]
        self.write_lines(lines)
        self.fail()

    def test_intent_has_rules_flag_wrong(self):
        lines = [line.replace('r96.xlsx\tPlain\t0', 'r96.xlsx\tPlain\t1', 1) if line.startswith('r96.xlsx\tPlain\t')
                 else line for line in self.lines()]
        self.write_lines(lines)
        self.fail()

    def test_intent_names_unknown_sheet(self):
        lines = self.lines() + ['r96.xlsx\tGhost\t0\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-\t-']
        self.write_lines(lines)
        self.fail()


if __name__ == '__main__':
    unittest.main()
