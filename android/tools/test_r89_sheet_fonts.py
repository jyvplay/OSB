#!/usr/bin/env python3
"""Negative controls for the R89 spreadsheet font-size verifier.

Each test tampers one part or one intent row and asserts that `tools/check_r89_sheet_fonts.py` refuses it.
The undisturbed workbooks are checked first, so a verifier that rejects everything cannot pass.

Skips when the R89 artifacts are absent (CI regenerates them with `tools/run_r89_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r89_sheet_fonts -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r89_sheet_fonts as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r89/artifacts'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class FontControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'fonts.tsv').is_file():
            raise unittest.SkipTest('R89 artifacts are absent; run tools/run_r89_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r89-fonts-'))

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
        totals = checker.verify(self.folder, True)
        self.assertEqual(totals['workbooks'], 3)
        self.assertEqual(totals['cells'], 8)
        self.assertEqual(totals['sized'], 5)
        self.assertEqual(totals['reader_cells'], 8)

    def test_changed_size_is_rejected(self):
        self.patch('font-size', lambda xml: xml.replace('<sz val="24"/>', '<sz val="22"/>', 1))
        self.fail()

    def test_dropped_size_is_rejected(self):
        self.patch('font-size', lambda xml: xml.replace('<sz val="8"/>', '<sz val="11"/>', 1))
        self.fail()

    def test_added_size_is_rejected(self):
        # font-mixed A2 declares no size, so its font must stay at the default 11 pt.
        self.patch('font-mixed', lambda xml: xml.replace(
            '<font><sz val="11"/>', '<font><sz val="9"/>', 1))
        self.fail()

    def test_changed_colour_is_rejected(self):
        self.patch('font-mixed', lambda xml: xml.replace('FFC00000', 'FFC00001', 1))
        self.fail()

    def test_dropped_bold_is_rejected(self):
        self.patch('font-mixed', lambda xml: xml.replace('<font><b/>', '<font>', 1))
        self.fail()

    def test_dropped_workbook_is_rejected(self):
        (self.folder / 'font-mixed.xlsx').unlink()
        self.fail(require_openpyxl=False)

    def test_invented_intent_is_rejected(self):
        rows = (self.folder / 'fonts.tsv').read_text(encoding='utf-8').splitlines()
        fields = rows[0].split('\t')
        fields[6] = '12'
        rows[0] = '\t'.join(fields)
        (self.folder / 'fonts.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail(require_openpyxl=False)

    def test_openpyxl_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'fonts.tsv')
        table['font-default'][0][6] = '12'
        with self.assertRaises(checker.Fail):
            checker.verify_openpyxl(self.folder, table)


if __name__ == '__main__':
    unittest.main()
