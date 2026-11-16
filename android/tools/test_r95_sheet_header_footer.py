#!/usr/bin/env python3
"""Negative controls for the R95 header/footer verifier.

Each test tampers one thing — a worksheet part or one intent row — and asserts the verifier refuses it. The
undisturbed artifacts are checked first, so a verifier that rejects everything cannot pass.

Skips when the R95 artifacts are absent (CI regenerates them with `tools/run_r95_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r95_sheet_header_footer -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r95_sheet_header_footer as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r95/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None
GRID = 'xl/worksheets/sheet1.xml'
FIRST = 'xl/worksheets/sheet2.xml'
PLAIN = 'xl/worksheets/sheet3.xml'

HEADER_FOOTER = (
    '<headerFooter differentOddEven="1" scaleWithDoc="0" alignWithMargins="0">'
    '<oddHeader>&amp;LQuarterly report&amp;CConfidential</oddHeader>'
    '<oddFooter>&amp;R&amp;A page &amp;P/&amp;N</oddFooter>'
    '<evenHeader>&amp;CEven pages</evenHeader></headerFooter>'
)
ROW_BREAKS = '<rowBreaks count="3" manualBreakCount="2">'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class HeaderFooterControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'headerfooter.tsv').is_file():
            raise unittest.SkipTest('R95 artifacts are absent; run tools/run_r95_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r95-header-'))

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
        return (self.folder / 'headerfooter.tsv').read_text(encoding='utf-8').splitlines()

    def write_lines(self, lines: list[str]) -> None:
        (self.folder / 'headerfooter.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['header_sheets'], 2)
        self.assertEqual(totals['plain_sheets'], 1)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_sheets'], 3)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    def test_header_footer_removed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(HEADER_FOOTER, ''))
        self.fail()

    def test_undeclared_header_footer_injected(self):
        self.patch('r95.xlsx', PLAIN, lambda xml: xml.replace(
            '</sheetData>', '</sheetData>' + HEADER_FOOTER))
        self.fail()

    def test_slot_order_broken(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<oddHeader>&amp;LQuarterly report&amp;CConfidential</oddHeader>'
            '<oddFooter>&amp;R&amp;A page &amp;P/&amp;N</oddFooter>',
            '<oddFooter>&amp;R&amp;A page &amp;P/&amp;N</oddFooter>'
            '<oddHeader>&amp;LQuarterly report&amp;CConfidential</oddHeader>'))
        self.fail()

    def test_slot_repeated(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<evenHeader>&amp;CEven pages</evenHeader>',
            '<evenHeader>&amp;CEven pages</evenHeader><evenHeader>&amp;Cagain</evenHeader>'))
        self.fail()

    def test_slot_text_changed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '&amp;LQuarterly report', '&amp;LQuarterly Report'))
        self.fail()

    def test_ampersand_not_escaped(self):
        # An unescaped '&' makes the whole part unparseable; the verifier must say so, not traceback.
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '&amp;R&amp;A page', '&amp;R&A page'))
        self.fail()

    def test_raw_markup_in_text(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<oddHeader>&amp;LQuarterly report&amp;CConfidential</oddHeader>',
            '<oddHeader>&amp;LQuarterly report&amp;C<b>Confidential</b></oddHeader>'))
        self.fail()

    def test_declared_slot_removed(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<evenHeader>&amp;CEven pages</evenHeader>', ''))
        self.fail()

    def test_undeclared_slot_invented(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<evenHeader>&amp;CEven pages</evenHeader>',
            '<evenHeader>&amp;CEven pages</evenHeader><evenFooter>&amp;Cextra</evenFooter>'))
        self.fail()

    def test_false_flag_written(self):
        # differentOddEven="0" is a false flag the schema default already states; writing it is a lie about intent.
        self.patch('r95.xlsx', FIRST, lambda xml: xml.replace(
            '<headerFooter differentFirst="1" scaleWithDoc="1">',
            '<headerFooter differentFirst="1" differentOddEven="0" scaleWithDoc="1">'))
        self.fail()

    def test_flag_value_not_boolean(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            'scaleWithDoc="0"', 'scaleWithDoc="false"'))
        self.fail()

    def test_attribute_order_broken(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<headerFooter differentOddEven="1" scaleWithDoc="0"',
            '<headerFooter scaleWithDoc="0" differentOddEven="1"'))
        self.fail()

    def test_unmodelled_attribute(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '<headerFooter differentOddEven="1"', '<headerFooter differentOddEven="1" charset="x"'))
        self.fail()

    def test_header_footer_before_page_setup(self):
        def move(xml: str) -> str:
            margin = '<pageMargins left="0.5" right="0.5" top="1" bottom="1" header="0.5" footer="0.5"/>'
            setup = '<pageSetup paperSize="9" scale="85" orientation="landscape"/>'
            return (xml.replace(HEADER_FOOTER, '')
                       .replace(margin + setup, HEADER_FOOTER + margin + setup))
        self.patch('r95.xlsx', GRID, move)
        self.fail()

    def test_header_footer_after_breaks(self):
        def move(xml: str) -> str:
            return (xml.replace(HEADER_FOOTER, '')
                       .replace('</rowBreaks>', '</rowBreaks>' + HEADER_FOOTER))
        self.patch('r95.xlsx', GRID, move)
        self.fail()

    def test_text_without_section_marker(self):
        self.patch('r95.xlsx', GRID, lambda xml: xml.replace(
            '&amp;LQuarterly report&amp;CConfidential', 'Quarterly report'))
        self.fail()

    def test_intent_presence_lie(self):
        self.write_lines([line.replace('\toddHeader\t1\t', '\toddHeader\t0\t') for line in self.lines()])
        self.fail()

    def test_intent_text_lie(self):
        self.write_lines([line.replace('&LQuarterly report', '&LQuarterly reports') for line in self.lines()])
        self.fail()

    def test_intent_flag_lie(self):
        self.write_lines([line.replace('\tGrid\t1\toddHeader\t1\t&LQuarterly report&CConfidential\t1\t',
                                       '\tGrid\t1\toddHeader\t1\t&LQuarterly report&CConfidential\t0\t')
                          for line in self.lines()])
        self.fail()

    def test_intent_optional_flag_lie(self):
        self.write_lines([line.replace('\toddHeader\t1\t&LQuarterly report&CConfidential\t1\t0\t0\t0',
                                       '\toddHeader\t1\t&LQuarterly report&CConfidential\t1\t0\t-\t0')
                          for line in self.lines()])
        self.fail()

    def test_intent_row_dropped(self):
        self.write_lines([line for line in self.lines() if 'evenHeader' not in line])
        self.fail()

    def test_intent_field_count(self):
        self.write_lines([self.lines()[0] + '\textra'])
        self.fail()

    def test_intent_declares_missing_file(self):
        self.write_lines(self.lines() + ['r95-absent.xlsx\tGhost\t0\t-\t-\t-\t-\t-\t-\t-'])
        self.fail()


if __name__ == '__main__':
    unittest.main()
