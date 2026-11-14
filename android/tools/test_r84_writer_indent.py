#!/usr/bin/env python3
"""Negative controls for the R84 Writer indentation and keep-flag verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and asserts that
tools/check_r84_writer_indent.py refuses the result. The controls skip when the artifacts are absent (they
are produced by tools/run_r84_writer_indent.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r84_writer_indent -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r84_writer_indent as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r84writer/artifacts'
DOCUMENTS = ('default', 'indent-left', 'indent-right', 'hanging', 'first-line', 'combined', 'heading-keep',
             'full', 'keep-off', 'zero', 'mixed', 'layout-indent')


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


class IndentControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'indent.tsv').is_file():
            self.skipTest('R84 writer indent artifacts are absent; run tools/run_r84_writer_indent.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r84-indent-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in DOCUMENTS:
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'indent.tsv', self.directory / 'indent.tsv')

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
        path = self.directory / 'indent.tsv'
        path.write_text(transform(path.read_text(encoding='utf-8').splitlines()), encoding='utf-8')

    def test_undisturbed_packages_pass(self):
        report = self.verify()
        self.assertEqual(report['documents'], 12)
        self.assertEqual(report['paragraphs'], 19)

    def test_removed_indent_is_rejected(self):
        self.replace('hanging', lambda text: text.replace('<w:ind w:hanging="240"/>', '', 1))
        self.expect_failure('is missing w:ind')

    def test_changed_indent_is_rejected(self):
        self.replace('indent-left', lambda text: text.replace('w:left="720"', 'w:left="721"', 1))
        self.expect_failure('!= declared 720')

    def test_invented_side_is_rejected(self):
        self.replace('indent-left', lambda text: text.replace(
            '<w:ind w:left="720"/>', '<w:ind w:left="720" w:right="0"/>', 1))
        self.expect_failure('declares w:right but the intent says none')

    def test_hanging_written_as_first_line_is_rejected(self):
        self.replace('hanging', lambda text: text.replace(
            '<w:ind w:hanging="240"/>', '<w:ind w:firstLine="240"/>', 1))
        self.expect_failure('!= declared 240')

    def test_both_first_line_and_hanging_are_rejected(self):
        self.replace('combined', lambda text: text.replace(
            'w:hanging="240"', 'w:hanging="240" w:firstLine="240"', 1))
        self.expect_failure('ct_ind forbids')

    def test_alias_attribute_is_rejected(self):
        self.replace('indent-left', lambda text: text.replace(
            '<w:ind w:left="720"/>', '<w:ind w:left="720" w:start="720"/>', 1))
        self.expect_failure('undeclared indent attributes')

    def test_removed_keep_flag_is_rejected(self):
        self.replace('combined', lambda text: text.replace('<w:keepNext/>', '', 1))
        self.expect_failure('w:keepnext presence')

    def test_invented_keep_flag_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<w:p><w:r><w:t xml:space="preserve">Plain body.</w:t>',
            '<w:p><w:pPr><w:keepNext/></w:pPr><w:r><w:t xml:space="preserve">Plain body.</w:t>', 1))
        self.expect_failure('w:keepnext presence')

    def test_explicit_off_written_as_a_bare_element_is_rejected(self):
        self.replace('keep-off', lambda text: text.replace('<w:keepNext w:val="0"/>', '<w:keepNext/>', 1))
        self.expect_failure('explicit off but w:val is')

    def test_explicit_on_written_as_zero_is_rejected(self):
        self.replace('combined', lambda text: text.replace('<w:keepNext/>', '<w:keepNext w:val="0"/>', 1))
        self.expect_failure('but the intent says on')

    def test_keep_flag_on_a_list_item_is_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:pStyle w:val="ListParagraph"/>', '<w:pStyle w:val="ListParagraph"/><w:keepNext/>', 1))
        self.expect_failure('only headings and paragraphs')

    def test_indent_after_the_alignment_is_rejected(self):
        self.replace('full', lambda text: text.replace(
            '<w:ind w:left="720" w:right="360" w:hanging="240"/><w:jc w:val="center"/>',
            '<w:jc w:val="center"/><w:ind w:left="720" w:right="360" w:hanging="240"/>', 1))
        self.expect_failure('out of ct_ppr order')

    def test_indent_before_the_spacing_is_rejected(self):
        # Swapping spacing and ind trips the CT_PPr order rule first; the verifier's "spacing precedes ind"
        # assertion is a defensive invariant for that same ordering, not a separately reachable failure.
        self.replace('full', lambda text: text.replace(
            '<w:spacing w:before="120" w:after="240"/><w:ind w:left="720" w:right="360" w:hanging="240"/>',
            '<w:ind w:left="720" w:right="360" w:hanging="240"/><w:spacing w:before="120" w:after="240"/>', 1))
        self.expect_failure('out of ct_ppr order')

    def test_indent_inside_a_table_cell_is_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:tc><w:p><w:r>', '<w:tc><w:p><w:pPr><w:ind w:left="720"/></w:pPr><w:r>', 1))
        self.expect_failure('table cell carries w:ind')

    def test_tampered_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'indent-left':
                    fields[3] = '900'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('!= declared 900')

    def test_out_of_range_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'hanging':
                    fields[5] = str(-(checker.MAX_INDENT_TWIPS + 1))
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('outside')

    def test_unknown_keep_token_is_rejected(self):
        def transform(lines):
            fields = lines[0].split('\t')
            fields[6] = 'yes'
            lines[0] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('bad keepnext flag')

    def test_truncated_intent_row_is_rejected(self):
        self.edit_intent(lambda lines: '\n'.join(lines[:1] + ['\t'.join(lines[1].split('\t')[:5])]) + '\n')
        self.expect_failure('fields, expected 8')

    def test_undeclared_package_is_rejected(self):
        shutil.copy(self.directory / 'default.docx', self.directory / 'stray.docx')
        self.expect_failure('unexpected packages')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'indent.tsv').unlink()
        self.expect_failure('missing intent table')


if __name__ == '__main__':
    unittest.main()
