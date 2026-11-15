#!/usr/bin/env python3
"""Negative controls for the R88 sheet-view gridline verifier.

Each test tampers one part or one intent row and asserts that `tools/check_r88_sheet_gridlines.py` refuses
it. The undisturbed workbooks are checked first, because a verifier that rejects everything would otherwise
pass every control.

The controls skip when the R88 artifacts are absent (CI regenerates them with `tools/run_r88_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r88_gridlines -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r88_sheet_gridlines as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r88/artifacts'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class GridlineControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'grid.tsv').is_file():
            raise unittest.SkipTest('R88 artifacts are absent; run tools/run_r88_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r88-grid-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, sheet: int, transform) -> None:
        rewrite(ARTIFACTS / f'{name}.xlsx', self.folder / f'{name}.xlsx',
                f'xl/worksheets/sheet{sheet}.xml', transform)

    def fail(self, require_openpyxl: bool = True) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_openpyxl)

    def test_undisturbed_workbooks_pass(self):
        totals = checker.verify(self.folder, True)
        self.assertEqual(totals['workbooks'], 3)
        self.assertEqual(totals['sheets'], 6)
        self.assertEqual(totals['grid_off'], 3)
        self.assertEqual(totals['reader_sheets'], 6)

    def test_flipped_flag_is_rejected(self):
        self.patch('grid-off', 1, lambda xml: xml.replace('showGridLines="0"', 'showGridLines="1"', 1))
        self.fail()

    def test_dropped_attribute_is_rejected(self):
        self.patch('grid-off', 3, lambda xml: xml.replace(' showGridLines="0"', '', 1))
        self.fail()

    def test_added_attribute_is_rejected(self):
        self.patch('grid-default', 1, lambda xml: xml.replace(
            '<worksheet xmlns', '<worksheet xmlns', 1).replace(
            '<sheetData>', '<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>'
            '<sheetData>', 1))
        self.fail()

    def test_dropped_sheet_views_is_rejected(self):
        self.patch('grid-off', 1, lambda xml: xml.replace(
            '<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>', '', 1))
        self.fail()

    def test_two_sheet_views_are_rejected(self):
        self.patch('grid-off', 1, lambda xml: xml.replace(
            '</sheetViews>', '<sheetView workbookViewId="1"/></sheetViews>', 1))
        self.fail()

    def test_changed_zoom_is_rejected(self):
        self.patch('grid-off', 3, lambda xml: xml.replace('zoomScale="150"', 'zoomScale="175"', 1))
        self.fail()

    def test_missing_pane_is_rejected(self):
        def drop_pane(xml: str) -> str:
            start = xml.index('<pane ')
            end = xml.index('/>', start) + 2
            return xml[:start] + xml[end:]

        self.patch('grid-off', 3, drop_pane)
        self.fail()

    def test_changed_state_is_rejected_by_openpyxl(self):
        rewrite(ARTIFACTS / 'grid-mixed.xlsx', self.folder / 'grid-mixed.xlsx', 'xl/workbook.xml',
                lambda xml: xml.replace('state="hidden"', 'state="visible"', 1))
        self.fail()

    def test_dropped_workbook_is_rejected(self):
        (self.folder / 'grid-mixed.xlsx').unlink()
        self.fail(require_openpyxl=False)

    def test_invented_intent_is_rejected(self):
        rows = (self.folder / 'grid.tsv').read_text(encoding='utf-8').splitlines()
        rows[0] = rows[0].replace('\t-\t', '\ttrue\t', 1)
        (self.folder / 'grid.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail(require_openpyxl=False)

    def test_openpyxl_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'grid.tsv')
        table['grid-mixed'][0]['grid'] = True
        with self.assertRaises(checker.Fail):
            checker.verify_openpyxl(self.folder, table)


if __name__ == '__main__':
    unittest.main()
