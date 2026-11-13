#!/usr/bin/env python3
"""Negative controls for the R81 Writer table-geometry verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and
asserts that tools/check_r81_writer_tables.py refuses the result. The controls skip when the
artifacts are absent (they are produced by tools/run_r81_writer_tables.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r81_writer_tables -v
"""
from __future__ import annotations

import re
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r81_writer_tables as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r81writer/artifacts'
DOCUMENTS = ('auto-table', 'sized-table', 'repeat-header', 'sized-repeat', 'wide-table',
             'boundary-width', 'two-tables', 'layout-table', 'single-cell')


def rewrite(source: Path, target: Path, transform) -> None:
    """Copies a package, letting ``transform(name, payload)`` rewrite or drop parts (None drops)."""
    with zipfile.ZipFile(source) as original:
        entries = [(name, original.read(name)) for name in original.namelist()]
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
        for name, payload in entries:
            result = transform(name, payload)
            if result is None:
                continue
            output.writestr(name, result)


def document_only(transform):
    def apply(name, payload):
        if name != 'word/document.xml':
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


class TableControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'tables.tsv').is_file():
            self.skipTest('R81 writer table artifacts are absent; run tools/run_r81_writer_tables.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r81-tables-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in DOCUMENTS:
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'tables.tsv', self.directory / 'tables.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def test_undisturbed_packages_pass(self):
        report = self.verify()
        self.assertEqual(report['documents'], 9)
        self.assertEqual(report['tables'], 10)

    def test_removed_header_marker_is_rejected(self):
        rewrite(self.directory / 'sized-repeat.docx', self.directory / 'sized-repeat.docx.tmp',
                document_only(lambda text: text.replace('<w:trPr><w:tblHeader/></w:trPr>', '')))
        shutil.move(str(self.directory / 'sized-repeat.docx.tmp'), str(self.directory / 'sized-repeat.docx'))
        self.expect_failure('header row must carry exactly one')

    def test_header_marker_on_a_later_row_is_rejected(self):
        def transform(text):
            marker = '<w:tr><w:trPr><w:tblHeader/></w:trPr>'
            third = '<w:tr><w:tc><w:p><w:r><w:t xml:space="preserve">b</w:t>'
            self.assertIn(marker, text)
            self.assertIn(third, text)
            return text.replace(third, '<w:tr><w:trPr><w:tblHeader/></w:trPr><w:tc>'
                                       '<w:p><w:r><w:t xml:space="preserve">b</w:t>', 1)
        rewrite(self.directory / 'repeat-header.docx', self.directory / 'repeat-header.docx.tmp',
                document_only(transform))
        shutil.move(str(self.directory / 'repeat-header.docx.tmp'), str(self.directory / 'repeat-header.docx'))
        self.expect_failure('only row 0 may repeat')

    def test_changed_grid_width_is_rejected(self):
        rewrite(self.directory / 'sized-table.docx', self.directory / 'sized-table.docx.tmp',
                document_only(lambda text: text.replace('<w:gridCol w:w="3000"/>', '<w:gridCol w:w="3001"/>')))
        shutil.move(str(self.directory / 'sized-table.docx.tmp'), str(self.directory / 'sized-table.docx'))
        self.expect_failure('grid column 0 width')

    def test_missing_table_width_is_rejected(self):
        rewrite(self.directory / 'sized-table.docx', self.directory / 'sized-table.docx.tmp',
                document_only(lambda text: text.replace(
                    '<w:tblW w:w="6750" w:type="dxa"/>', '', 1)))
        shutil.move(str(self.directory / 'sized-table.docx.tmp'), str(self.directory / 'sized-table.docx'))
        self.expect_failure('tblpr children')

    def test_auto_layout_on_a_sized_table_is_rejected(self):
        rewrite(self.directory / 'sized-table.docx', self.directory / 'sized-table.docx.tmp',
                document_only(lambda text: text.replace(
                    '<w:tblLayout w:type="fixed"/>', '<w:tblLayout w:type="autofit"/>', 1)))
        shutil.move(str(self.directory / 'sized-table.docx.tmp'), str(self.directory / 'sized-table.docx'))
        self.expect_failure('w:tblLayout')

    def test_missing_grid_column_is_rejected(self):
        rewrite(self.directory / 'wide-table.docx', self.directory / 'wide-table.docx.tmp',
                document_only(lambda text: text.replace('<w:gridCol w:w="5000"/>', '', 1)))
        shutil.move(str(self.directory / 'wide-table.docx.tmp'), str(self.directory / 'wide-table.docx'))
        self.expect_failure('grid column count')

    def test_added_style_on_an_auto_table_is_rejected(self):
        rewrite(self.directory / 'auto-table.docx', self.directory / 'auto-table.docx.tmp',
                document_only(lambda text: text.replace(
                    '<w:tbl><w:tblPr/>', '<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/></w:tblPr>', 1)))
        shutil.move(str(self.directory / 'auto-table.docx.tmp'), str(self.directory / 'auto-table.docx'))
        self.expect_failure('empty w:tblpr')

    def test_width_on_an_auto_table_column_is_rejected(self):
        rewrite(self.directory / 'auto-table.docx', self.directory / 'auto-table.docx.tmp',
                document_only(lambda text: text.replace(
                    '<w:tblGrid><w:gridCol/><w:gridCol/></w:tblGrid>',
                    '<w:tblGrid><w:gridCol w:w="2000"/><w:gridCol/></w:tblGrid>', 1)))
        shutil.move(str(self.directory / 'auto-table.docx.tmp'), str(self.directory / 'auto-table.docx'))
        self.expect_failure('declares a width but the table does not')

    def test_table_wider_than_the_text_column_is_rejected(self):
        # Widen the margins instead of the table: the declared geometry stays self-consistent, so the
        # only failing rule is containment inside the section's text column.
        def transform(text):
            self.assertIn('w:left="1440"', text)
            self.assertIn('w:right="1440"', text)
            return text.replace('w:left="1440"', 'w:left="5000"', 1).replace(
                'w:right="1440"', 'w:right="5000"', 1)
        rewrite(self.directory / 'layout-table.docx', self.directory / 'layout-table.docx.tmp',
                document_only(transform))
        shutil.move(str(self.directory / 'layout-table.docx.tmp'), str(self.directory / 'layout-table.docx'))
        self.expect_failure('wider than the')

    def test_tampered_intent_is_rejected(self):
        path = self.directory / 'tables.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'sized-table':
                fields[3] = '3000,1500,2251'
                lines[index] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('sum of declared widths')

    def test_undeclared_package_is_rejected(self):
        shutil.copy(self.directory / 'single-cell.docx', self.directory / 'stray.docx')
        self.expect_failure('unexpected packages')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'tables.tsv').unlink()
        self.expect_failure('missing intent table')

    def test_truncated_intent_row_is_rejected(self):
        path = self.directory / 'tables.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        lines[0] = '\t'.join(lines[0].split('\t')[:5])
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('fields, expected 7')

    def test_row_count_change_is_rejected(self):
        rewrite(self.directory / 'repeat-header.docx', self.directory / 'repeat-header.docx.tmp',
                document_only(lambda text: re.sub(r'<w:tr><w:tc><w:p><w:r><w:t xml:space="preserve">b</w:t>.*?</w:tr>',
                                                  '', text, count=1, flags=re.S)))
        shutil.move(str(self.directory / 'repeat-header.docx.tmp'), str(self.directory / 'repeat-header.docx'))
        self.expect_failure('row count')


if __name__ == '__main__':
    unittest.main()
