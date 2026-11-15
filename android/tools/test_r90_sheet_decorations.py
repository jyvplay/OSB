#!/usr/bin/env python3
"""Negative controls for the R90 spreadsheet decoration verifier (`<u/>`, `<strike/>`).

Each test tampers one part or one intent row and asserts that `tools/check_r90_sheet_decorations.py`
refuses it. The undisturbed workbooks are checked first.

Skips when the R90 artifacts are absent (CI regenerates them with `tools/run_r90_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r90_sheet_decorations -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r90_sheet_decorations as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r90/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class SheetDecorationControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'decorations_xlsx.tsv').is_file():
            raise unittest.SkipTest('R90 artifacts are absent; run tools/run_r90_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r90-xlsx-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, transform) -> None:
        rewrite(ARTIFACTS / f'{name}.xlsx', self.folder / f'{name}.xlsx', 'xl/styles.xml', transform)

    def fail(self, require_openpyxl: bool = True) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_openpyxl)

    def test_undisturbed_workbooks_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['workbooks'], 3)
        self.assertEqual(totals['cells'], 8)
        self.assertEqual(totals['decorated'], 5)
        if HAVE_OPENPYXL:                  # installed in CI, not necessarily on a host
            self.assertEqual(totals['reader_cells'], 8)

    def test_dropped_underline_is_rejected(self):
        self.patch('deco', lambda xml: xml.replace('<font><u/>', '<font>', 1))
        self.fail()

    def test_dropped_strike_is_rejected(self):
        self.patch('deco', lambda xml: xml.replace('<font><strike/>', '<font>', 1))
        self.fail()

    def test_invented_underline_is_rejected(self):
        # A2 declares strike only; adding <u/> to the default font must be refused.
        self.patch('deco', lambda xml: xml.replace(
            '<font><sz val="11"/>', '<font><u/><sz val="11"/>', 1))
        self.fail()

    def test_reordered_font_children_are_rejected(self):
        def swap(xml: str) -> str:
            both = '<strike/><u/><sz val="11"/>'
            return xml.replace(both, '<u/><strike/><sz val="11"/>', 1)

        self.patch('deco', swap)
        self.fail()

    def test_dropped_workbook_is_rejected(self):
        (self.folder / 'deco-mixed.xlsx').unlink()
        self.fail(require_openpyxl=False)

    def test_invented_intent_is_rejected(self):
        rows = (self.folder / 'decorations_xlsx.tsv').read_text(encoding='utf-8').splitlines()
        index = next(i for i, row in enumerate(rows) if row.split('\t')[:3] == ['deco', 'Deco', 'A4'])
        fields = rows[index].split('\t')      # A4 is decorated with neither <u/> nor <strike/>
        fields[5] = 'true'
        rows[index] = '\t'.join(fields)
        (self.folder / 'decorations_xlsx.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail(require_openpyxl=False)

    @unittest.skipUnless(HAVE_OPENPYXL, 'openpyxl is not installed')
    def test_openpyxl_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'decorations_xlsx.tsv')
        table['deco'][0][5] = 'false'         # A1 is underlined on disk
        with self.assertRaises(checker.Fail):
            checker.verify_openpyxl(self.folder, table)

    def test_document_styles_part_is_required(self):
        target = self.folder / 'deco.xlsx'
        with zipfile.ZipFile(target) as reader:
            parts = {item.filename: reader.read(item.filename) for item in reader.infolist()}
        parts.pop('xl/styles.xml')
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
            for name, payload in parts.items():
                writer.writestr(name, payload)
        self.fail(require_openpyxl=False)


if __name__ == '__main__':
    unittest.main()
