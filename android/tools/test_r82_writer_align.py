#!/usr/bin/env python3
"""Negative controls for the R82 Writer paragraph-alignment verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and
asserts that tools/check_r82_writer_align.py refuses the result. The controls skip when the artifacts
are absent (they are produced by tools/run_r82_writer_align.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r82_writer_align -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r82_writer_align as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r82writer/artifacts'
DOCUMENTS = ('default', 'heading-center', 'paragraph-right', 'paragraph-justify', 'paragraph-left',
             'all-four', 'mixed', 'layout-align')


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


class AlignControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'align.tsv').is_file():
            self.skipTest('R82 writer align artifacts are absent; run tools/run_r82_writer_align.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r82-align-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in DOCUMENTS:
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'align.tsv', self.directory / 'align.tsv')

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

    def test_undisturbed_packages_pass(self):
        report = self.verify()
        self.assertEqual(report['documents'], 8)
        self.assertEqual(report['paragraphs'], 20)

    def test_removed_alignment_is_rejected(self):
        self.replace('heading-center', lambda text: text.replace('<w:jc w:val="center"/>', '', 1))
        self.expect_failure('missing its w:jc')

    def test_changed_token_is_rejected(self):
        self.replace('paragraph-right', lambda text: text.replace('<w:jc w:val="right"/>',
                                                                  '<w:jc w:val="center"/>', 1))
        self.expect_failure('!= declared')

    def test_default_paragraph_with_alignment_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<w:p><w:r><w:t xml:space="preserve">Default body one.</w:t>',
            '<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r>'
            '<w:t xml:space="preserve">Default body one.</w:t>', 1))
        self.expect_failure('declares no alignment')

    def test_alignment_on_a_list_item_is_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:p><w:pPr><w:pStyle w:val="ListParagraph"/>',
            '<w:p><w:pPr><w:pStyle w:val="ListParagraph"/><w:jc w:val="right"/>', 1))
        self.expect_failure('only headings and paragraphs')

    def test_alignment_before_the_paragraph_style_is_rejected(self):
        self.replace('heading-center', lambda text: text.replace(
            '<w:pStyle w:val="Heading1"/><w:jc w:val="center"/>',
            '<w:jc w:val="center"/><w:pStyle w:val="Heading1"/>', 1))
        self.expect_failure('out of ct_ppr order')

    def test_unknown_paragraph_property_is_rejected(self):
        self.replace('heading-center', lambda text: text.replace(
            '<w:pStyle w:val="Heading1"/><w:jc w:val="center"/>',
            '<w:pStyle w:val="Heading1"/><w:sz w:val="10"/><w:jc w:val="center"/>', 1))
        self.expect_failure('unknown w:ppr children')

    def test_alignment_inside_a_table_cell_is_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:tc><w:p><w:r>', '<w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r>', 1))
        self.expect_failure('table cell carries w:jc')

    def test_tampered_intent_is_rejected(self):
        path = self.directory / 'align.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        for index, line in enumerate(lines):
            fields = line.split('\t')
            if fields[0] == 'all-four' and fields[3] == 'center':
                fields[3] = 'right'
                lines[index] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('!= declared')

    def test_unknown_token_in_intent_is_rejected(self):
        path = self.directory / 'align.tsv'
        lines = path.read_text(encoding='utf-8').splitlines()
        fields = lines[0].split('\t')
        fields[3] = 'middle'
        lines[0] = '\t'.join(fields)
        path.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        self.expect_failure('unknown alignment')

    def test_missing_paragraph_is_rejected(self):
        self.replace('paragraph-left', lambda text: text.replace('<w:p><w:pPr><w:jc w:val="left"/></w:pPr>'
                                                                 '<w:r><w:t xml:space="preserve">Left aligned.'
                                                                 '</w:t></w:r></w:p>', '', 1))
        self.expect_failure('paragraph count')

    def test_undeclared_package_is_rejected(self):
        shutil.copy(self.directory / 'default.docx', self.directory / 'stray.docx')
        self.expect_failure('unexpected packages')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'align.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
