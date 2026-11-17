#!/usr/bin/env python3
"""Negative controls for the R96 differential-format (`<dxfs>`) verifier.

Each test tampers the styles part or one intent cell and asserts the verifier refuses it: a table removed, a count
that disagrees with its children, an entry reordered, `<fill>` before `<font>`, a flag that appears or disappears, a
colour without its `FF` alpha, a solid fill without the `indexed="64"` background Excel writes, the table moved
ahead of `</cellXfs>`, and a table injected into a workbook that declares no formats.

Skips when the R96 artifacts are absent (CI regenerates them with `tools/run_r96_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r96_sheet_dxf -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r96_sheet_dxf as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r96/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None
STYLES = 'xl/styles.xml'

FILL_ENTRY = ('<dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/><bgColor indexed="64"/>'
              '</patternFill></fill></dxf>')
BOLD_ENTRY = '<dxf><font><b/></font></dxf>'
ITALIC_ENTRY = '<dxf><font><i/><color rgb="FF9C0006"/></font></dxf>'
TABLE = f'<dxfs count="3">{FILL_ENTRY}{BOLD_ENTRY}{ITALIC_ENTRY}</dxfs>'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class DxfControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'dxfs.tsv').is_file():
            raise unittest.SkipTest('R96 artifacts are absent; run tools/run_r96_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r96-dxf-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, transform) -> None:
        rewrite(ARTIFACTS / name, self.folder / name, STYLES, transform)

    def fail(self) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, HAVE_OPENPYXL)

    def lines(self) -> list[str]:
        return (self.folder / 'dxfs.tsv').read_text(encoding='utf-8').splitlines()

    def write_lines(self, lines: list[str]) -> None:
        (self.folder / 'dxfs.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    # --- the untouched artifacts -------------------------------------------------
    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['formats'], 3)
        self.assertEqual(totals['books_with_table'], 1)
        self.assertEqual(totals['plain_books'], 1)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_formats'], 3)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    # --- table-level -------------------------------------------------------------
    def test_table_removed(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(TABLE, ''))
        self.fail()

    def test_table_injected_into_plain_workbook(self):
        self.patch('r96-plain.xlsx', lambda xml: xml.replace('</cellXfs>', '</cellXfs>' + TABLE))
        self.fail()

    def test_table_before_cell_xfs(self):
        def move(xml: str) -> str:
            without = xml.replace(TABLE, '')
            start = without.index('?>') + 2 if without.startswith('<?xml') else 0
            head, _, tail = without[start:].partition('>')
            return without[:start] + head + '>' + TABLE + tail
        self.patch('r96.xlsx', move)
        self.fail()

    def test_count_attribute_changed(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<dxfs count="3">', '<dxfs count="4">'))
        self.fail()

    def test_count_attribute_dropped(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<dxfs count="3">', '<dxfs>'))
        self.fail()

    def test_entry_removed(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(ITALIC_ENTRY, '').replace('count="3"', 'count="2"'))
        self.fail()

    def test_entry_order_swapped(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(BOLD_ENTRY + ITALIC_ENTRY, ITALIC_ENTRY + BOLD_ENTRY))
        self.fail()

    def test_extra_entry_appended(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('</dxfs>', BOLD_ENTRY + '</dxfs>'))
        self.fail()

    # --- entry content -----------------------------------------------------------
    def test_font_before_fill_broken(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(
            '<dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/><bgColor indexed="64"/>'
            '</patternFill></fill></dxf>',
            '<dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/><bgColor indexed="64"/>'
            '</patternFill></fill><font><b/></font></dxf>'))
        self.fail()

    def test_bold_dropped(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(BOLD_ENTRY, '<dxf><font/></dxf>'))
        self.fail()

    def test_italic_invented(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(BOLD_ENTRY, '<dxf><font><b/><i/></font></dxf>'))
        self.fail()

    def test_strike_invented(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(BOLD_ENTRY, '<dxf><font><b/><strike/></font></dxf>'))
        self.fail()

    def test_underline_invented(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(BOLD_ENTRY, '<dxf><font><b/><u/></font></dxf>'))
        self.fail()

    def test_font_colour_changed(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('rgb="FF9C0006"', 'rgb="FF9C0007"'))
        self.fail()

    def test_font_colour_without_alpha(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('rgb="FF9C0006"', 'rgb="9C0006"'))
        self.fail()

    def test_font_colour_invented(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<font><b/></font>', '<font><b/><color rgb="FF000000"/></font>'))
        self.fail()

    def test_fill_colour_changed(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('rgb="FFFFC7CE"', 'rgb="FFFFC7CF"'))
        self.fail()

    def test_pattern_type_dropped(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<patternFill patternType="solid">', '<patternFill>'))
        self.fail()

    def test_foreground_dropped(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<fgColor rgb="FFFFC7CE"/>', ''))
        self.fail()

    def test_background_dropped(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<bgColor indexed="64"/>', ''))
        self.fail()

    def test_background_index_changed(self):
        self.patch('r96.xlsx', lambda xml: xml.replace('<bgColor indexed="64"/>', '<bgColor indexed="65"/>'))
        self.fail()

    def test_unmodelled_child(self):
        self.patch('r96.xlsx', lambda xml: xml.replace(BOLD_ENTRY, '<dxf><font><b/></font><numFmt numFmtId="0"/></dxf>'))
        self.fail()

    # --- intent table ------------------------------------------------------------
    def test_intent_count_column_wrong(self):
        self.write_lines([line.rsplit('\t', 1)[0] + '\t4' if line.startswith('r96.xlsx') else line
                          for line in self.lines()])
        self.fail()

    def test_intent_entry_undeclared(self):
        self.write_lines([line for line in self.lines() if not line.startswith('r96.xlsx\t2\t')])
        self.fail()

    def test_intent_flag_wrong(self):
        lines = list(self.lines())
        lines[1] = lines[1].replace('\t1\t1\t0\t0\t0\t', '\t1\t0\t1\t0\t0\t')
        self.write_lines(lines)
        self.fail()

    def test_intent_field_count_wrong(self):
        lines = list(self.lines())
        lines[0] = lines[0] + '\textra'
        self.write_lines(lines)
        self.fail()

    def test_intent_plain_workbook_absent(self):
        self.write_lines([line for line in self.lines() if not line.startswith('r96-plain')])
        self.fail()


if __name__ == '__main__':
    unittest.main()
