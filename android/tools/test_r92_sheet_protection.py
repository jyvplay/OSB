#!/usr/bin/env python3
"""Negative controls for the R92 sheet-protection verifier.

Each test tampers one thing — a workbook part or one intent row — and asserts the verifier refuses it. The
undisturbed artifacts are checked first, so a verifier that rejects everything cannot pass.

Skips when the R92 artifacts are absent (CI regenerates them with `tools/run_r92_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r92_sheet_protection -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r92_sheet_protection as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r92/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class ProtectionControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'protections.tsv').is_file():
            raise unittest.SkipTest('R92 artifacts are absent; run tools/run_r92_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r92-prot-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, entry: str, transform) -> None:
        rewrite(ARTIFACTS / name, self.folder / name, entry, transform)

    def fail(self, require_openpyxl: bool = False) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_openpyxl)

    def rows(self, name: str) -> list[str]:
        return (self.folder / name).read_text(encoding='utf-8').splitlines()

    def write(self, name: str, rows: list[str]) -> None:
        (self.folder / name).write_text('\n'.join(rows) + '\n', encoding='utf-8')

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['protected_sheets'], 2)
        self.assertEqual(totals['cells'], 5)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_sheets'], 3)
            self.assertEqual(totals['reader_cells'], 5)

    def test_dropped_action_attribute_is_rejected(self):
        self.patch('prot.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace(' sort="1"', '', 1))
        self.fail()

    def test_flipped_action_flag_is_rejected(self):
        self.patch('prot.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace('selectLockedCells="1"', 'selectLockedCells="0"', 1))
        self.fail()

    def test_inactive_sheet_flag_is_rejected(self):
        self.patch('prot.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace(' sheet="1"', ' sheet="0"', 1))
        self.fail()

    def test_misplaced_protection_is_rejected(self):
        def move(xml: str) -> str:
            start = xml.index('<sheetProtection')
            end = xml.index('/>', start) + 2
            element = xml[start:end]
            stripped = xml[:start] + xml[end:]
            return stripped.replace('<sheetData>', element + '<sheetData>', 1)

        self.patch('prot.xlsx', 'xl/worksheets/sheet1.xml', move)
        self.fail()

    def test_changed_password_is_rejected(self):
        self.patch('prot.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace('password="DAA7"', 'password="CE4B"', 1))
        self.fail()

    def test_undeclared_password_is_rejected(self):
        self.patch('prot-nopw.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace('<sheetProtection', '<sheetProtection password="CE4B"', 1))
        self.fail()

    def test_missing_protection_element_is_rejected(self):
        self.patch('prot-plain.xlsx', 'xl/worksheets/sheet1.xml',
                   lambda xml: xml.replace('<sheetData>', '<sheetProtection sheet="1" objects="0" scenarios="0" '
                                           'formatCells="0" formatColumns="0" formatRows="0" insertColumns="0" '
                                           'insertRows="0" insertHyperlinks="0" deleteColumns="0" deleteRows="0" '
                                           'selectLockedCells="0" sort="0" autoFilter="0" pivotTables="0" '
                                           'selectUnlockedCells="0"/><sheetData>', 1))
        self.fail()

    def test_dropped_cell_protection_is_rejected(self):
        self.patch('prot.xlsx', 'xl/styles.xml',
                   lambda xml: xml.replace('<protection locked="0"/>', '', 1))
        self.fail()

    def test_missing_apply_protection_is_rejected(self):
        self.patch('prot.xlsx', 'xl/styles.xml',
                   lambda xml: xml.replace(' applyProtection="1"', '', 4))
        self.fail()

    def test_flipped_cell_lock_is_rejected(self):
        self.patch('prot.xlsx', 'xl/styles.xml',
                   lambda xml: xml.replace('<protection locked="0"/>', '<protection locked="1"/>', 1))
        self.fail()

    def test_invented_cell_protection_is_rejected(self):
        self.patch('prot.xlsx', 'xl/styles.xml',
                   lambda xml: xml.replace('<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>',
                                           '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" '
                                           'applyProtection="1"><protection locked="0"/></xf>', 1))
        self.fail()

    def test_dropped_intent_row_is_rejected(self):
        rows = self.rows('protections.tsv')
        self.write('protections.tsv', rows[:-1])
        self.fail()

    def test_invented_intent_flag_is_rejected(self):
        rows = self.rows('protections.tsv')
        fields = rows[0].split('\t')
        fields[8] = '1' if fields[8] == '0' else '0'
        rows[0] = '\t'.join(fields)
        self.write('protections.tsv', rows)
        self.fail()

    def test_wrong_member_count_is_rejected(self):
        rows = self.rows('cell_protection.tsv')
        rows.append('prot.xlsx\tLocked\tA6')
        self.write('cell_protection.tsv', rows)
        self.fail()

    def test_malformed_worksheet_is_rejected(self):
        self.patch('prot.xlsx', 'xl/worksheets/sheet1.xml', lambda xml: xml[:-40])
        self.fail()

    @unittest.skipUnless(HAVE_OPENPYXL, 'openpyxl is not installed')
    def test_openpyxl_disagrees_with_a_tampered_table(self):
        rows = self.rows('protections.tsv')
        fields = rows[0].split('\t')
        fields[6] = '1' if fields[6] == '0' else '0'          # formatColumns
        rows[0] = '\t'.join(fields)
        self.write('protections.tsv', rows)
        with self.assertRaises(checker.Fail):
            checker.verify_openpyxl(self.folder, checker.intent_table(self.folder / 'protections.tsv', 19),
                                   checker.intent_table(self.folder / 'cell_protection.tsv', 5))


if __name__ == '__main__':
    unittest.main()
