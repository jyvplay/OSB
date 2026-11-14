#!/usr/bin/env python3
"""Negative controls for the R83 Writer paragraph-spacing verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and
asserts that tools/check_r83_writer_spacing.py refuses the result. The controls skip when the artifacts
are absent (they are produced by tools/run_r83_writer_spacing.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r83_writer_spacing -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r83_writer_spacing as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r83writer/artifacts'
DOCUMENTS = ('default', 'heading-spacing', 'paragraph-before', 'paragraph-after', 'zero',
             'spacing-align', 'mixed', 'layout-spacing')


def rewrite(source: Path, target: Path, transform) -> None:
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


class SpacingControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'spacing.tsv').is_file():
            self.skipTest('R83 writer spacing artifacts are absent; run tools/run_r83_writer_spacing.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r83-space-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in DOCUMENTS:
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'spacing.tsv', self.directory / 'spacing.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def replace(self, document: str, transform):
        target = self.directory / f'{document}.docx'
        temporary = self.directory / f'{document}.docx.tmp'
        rewrite(target, temporary, document_only(transform))
        shutil.move(str(temporary), str(target))

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def edit_intent(self, transform):
        path = self.directory / 'spacing.tsv'
        self.addCleanup(shutil.copy, ARTIFACTS / 'spacing.tsv', path)
        lines = path.read_text(encoding='utf-8').splitlines()
        path.write_text(transform(lines), encoding='utf-8')

    def test_undisturbed_packages_pass(self):
        report = self.verify()
        self.assertEqual(report['documents'], 8)
        self.assertEqual(report['paragraphs'], 14)

    def test_removed_spacing_is_rejected(self):
        self.replace('heading-spacing', lambda text: text.replace(
            '<w:spacing w:before="240" w:after="120"/>', '', 1))
        self.expect_failure('is missing w:spacing')

    def test_changed_spacing_is_rejected(self):
        self.replace('heading-spacing', lambda text: text.replace(
            'w:before="240"', 'w:before="241"', 1))
        self.expect_failure('!= declared 240')

    def test_invented_sibling_is_rejected(self):
        self.replace('paragraph-before', lambda text: text.replace(
            '<w:spacing w:before="120"/>', '<w:spacing w:before="120" w:after="0"/>', 1))
        self.expect_failure('declares w:after but the intent says none')

    def test_invented_line_spacing_is_rejected(self):
        self.replace('paragraph-before', lambda text: text.replace(
            '<w:spacing w:before="120"/>', '<w:spacing w:before="120" w:line="240"/>', 1))
        self.expect_failure('undeclared spacing attributes')

    def test_spacing_on_a_list_item_is_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:pStyle w:val="ListParagraph"/>',
            '<w:pStyle w:val="ListParagraph"/><w:spacing w:before="120"/>', 1))
        self.expect_failure('only headings and paragraphs')

    def test_spacing_after_the_alignment_is_rejected(self):
        self.replace('spacing-align', lambda text: text.replace(
            '<w:spacing w:before="120" w:after="240"/><w:jc w:val="center"/>',
            '<w:jc w:val="center"/><w:spacing w:before="120" w:after="240"/>', 1))
        self.expect_failure('out of ct_ppr order')

    def test_spacing_inside_a_table_cell_is_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:tc><w:p><w:r>', '<w:tc><w:p><w:pPr><w:spacing w:before="120"/></w:pPr><w:r>', 1))
        self.expect_failure('table cell carries w:spacing')

    def test_spacing_on_an_unspaced_paragraph_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<w:p><w:r><w:t xml:space="preserve">Default body.</w:t>',
            '<w:p><w:pPr><w:spacing w:before="120"/></w:pPr><w:r>'
            '<w:t xml:space="preserve">Default body.</w:t>', 1))
        self.expect_failure('carries w:spacing but declares none')

    def test_missing_paragraph_is_rejected(self):
        self.replace('paragraph-before', lambda text: text.replace(
            '<w:p><w:pPr><w:spacing w:before="120"/></w:pPr>'
            '<w:r><w:t xml:space="preserve">before only</w:t></w:r></w:p>', '', 1))
        self.expect_failure('paragraph count')

    def test_tampered_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'mixed' and fields[1] == '1':
                    fields[3] = '61'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('!= declared 61')

    def test_out_of_range_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'zero':
                    fields[3] = str(checker.MAX_SPACING_TWIPS + 1)
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('outside 0..')

    def test_truncated_intent_row_is_rejected(self):
        self.edit_intent(lambda lines: '\n'.join(lines[:1] + ['\t'.join(lines[1].split('\t')[:3])]) + '\n')
        self.expect_failure('fields, expected 5')

    def test_undeclared_package_is_rejected(self):
        shutil.copy(self.directory / 'default.docx', self.directory / 'stray.docx')
        self.expect_failure('unexpected packages')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'spacing.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
