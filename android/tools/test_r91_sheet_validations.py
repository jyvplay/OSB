#!/usr/bin/env python3
"""Negative controls for the R91 data-validation verifier (`check_r91_sheet_validations.py`).

Each test tampers one thing — a workbook part or one intent row — and asserts the verifier refuses it. The
undisturbed artifacts are checked first, so a verifier that rejects everything cannot pass.

Skips when the R91 artifacts are absent (CI regenerates them with `tools/run_r91_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r91_sheet_validations -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r91_sheet_validations as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r91/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class ValidationControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'validations.tsv').is_file():
            raise unittest.SkipTest('R91 artifacts are absent; run tools/run_r91_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r91-dv-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, transform) -> None:
        rewrite(ARTIFACTS / name, self.folder / name, 'xl/worksheets/sheet1.xml', transform)

    def fail(self, require_openpyxl: bool = False) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_openpyxl)

    def rows(self) -> list[str]:
        return (self.folder / 'validations.tsv').read_text(encoding='utf-8').splitlines()

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['rules'], 7)
        self.assertEqual(totals['workbooks'], 2)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_rules'], 7)

    def test_dropped_rule_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace(
            '<dataValidation type="date"', '<dataValidationX type="date"', 1))
        self.fail()

    def test_wrong_count_attribute_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace('count="6"', 'count="5"', 1))
        self.fail()

    def test_changed_type_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace('type="whole"', 'type="decimal"', 1))
        self.fail()

    def test_reordered_attributes_are_rejected(self):
        # `sqref` first is legal XML but not the order Excel writes; the verifier pins the order.
        self.patch('dv.xlsx', lambda xml: xml.replace(
            '<dataValidation type="whole" errorStyle="stop" operator="between"',
            '<dataValidation sqref="B2:B10" type="whole" errorStyle="stop" operator="between"', 1)
            .replace(' sqref="B2:B10">', '>', 1))
        self.fail()

    def test_swapped_dropdown_polarity_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace(
            'allowBlank="1" showDropDown="1"', 'allowBlank="1" showDropDown="0"', 1))
        self.fail()

    def test_operator_on_a_list_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace(
            '<dataValidation type="list" errorStyle="information"',
            '<dataValidation type="list" errorStyle="information" operator="between"', 1))
        self.fail()

    def test_missing_second_operand_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace('<formula2>44196</formula2>', '', 1))
        self.fail()

    def test_changed_sqref_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace('sqref="F2:F10"', 'sqref="F2:F11"', 1))
        self.fail()

    def test_dropped_error_text_is_rejected(self):
        self.patch('dv.xlsx', lambda xml: xml.replace(' errorTitle="Date window"', '', 1))
        self.fail()

    def test_invented_rule_in_the_plain_workbook_is_rejected(self):
        def add(xml: str) -> str:
            return xml.replace('</sheetData>', '</sheetData><dataValidations count="1">'
                               '<dataValidation type="list" errorStyle="stop" allowBlank="0" '
                               'showDropDown="0" showInputMessage="0" showErrorMessage="0" sqref="A1">'
                               '<formula1>&quot;a&quot;</formula1></dataValidation></dataValidations>', 1)

        self.patch('dv-plain.xlsx', add)
        self.fail()

    def test_dropped_intent_row_is_rejected(self):
        rows = self.rows()
        (self.folder / 'validations.tsv').write_text('\n'.join(rows[:-1]) + '\n', encoding='utf-8')
        self.fail()

    def test_invented_intent_row_is_rejected(self):
        rows = self.rows()
        fields = rows[0].split('\t')
        fields[4] = 'notBetween'
        rows[0] = '\t'.join(fields)
        (self.folder / 'validations.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail()

    def test_wrong_member_count_is_rejected(self):
        with (self.folder / 'validations.tsv').open('a', encoding='utf-8') as table:
            table.write('dv.xlsx\tRules\t6\n')
        self.fail()

    @unittest.skipUnless(HAVE_OPENPYXL, 'openpyxl is not installed')
    def test_openpyxl_path_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'validations.tsv')
        table[('dv.xlsx', 'Rules')][0][7] = 'false'      # rule 1 declares allowBlank=true
        with self.assertRaises(checker.Fail):
            checker.verify_openpyxl(self.folder, table)

    @unittest.skipUnless(HAVE_OPENPYXL, 'openpyxl is not installed')
    def test_openpyxl_detects_a_polarity_flip(self):
        self.patch('dv.xlsx', lambda xml: xml.replace('showDropDown="1"', 'showDropDown="0"', 1))
        self.fail(require_openpyxl=True)


if __name__ == '__main__':
    unittest.main()
