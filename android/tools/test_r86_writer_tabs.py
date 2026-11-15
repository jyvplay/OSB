#!/usr/bin/env python3
"""Negative controls for the R86 Writer tab-stop, page-break and widow-control verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and asserts that
tools/check_r86_writer_tabs.py refuses the result. The controls skip when the artifacts are absent (they are
produced by tools/run_r86_writer_tabs.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r86_writer_tabs -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r86_writer_tabs as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r86writer/artifacts'
DOCUMENTS = ('default', 'tabs-basic', 'tabs-aligns', 'tabs-leaders', 'break-before', 'widow', 'ordered',
             'boundary', 'heading-tabs', 'mixed', 'layout-tabs')


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
    def apply(_name, payload):
        if _name != 'word/document.xml':
            return payload
        return transform(payload.decode('utf-8')).encode('utf-8')
    return apply


class TabStopControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'tabs.tsv').is_file():
            self.skipTest('R86 writer tab artifacts are absent; run tools/run_r86_writer_tabs.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r86-tabs-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in DOCUMENTS:
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'tabs.tsv', self.directory / 'tabs.tsv')

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
        path = self.directory / 'tabs.tsv'
        path.write_text(transform(path.read_text(encoding='utf-8').splitlines()), encoding='utf-8')

    def test_undisturbed_packages_pass(self):
        report = self.verify()
        self.assertEqual(report['packages'], 11)
        self.assertEqual(report['paragraphs'], 21)

    # --- the tab list itself --------------------------------------------------------------------------
    def test_removed_tabs_element_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '<w:tabs><w:tab w:val="left" w:pos="720"/></w:tabs>', '', 1))
        self.expect_failure('w:tabs presence')

    def test_changed_position_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace('w:pos="720"', 'w:pos="721"', 1))
        self.expect_failure('w:pos')

    def test_missing_val_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '<w:tab w:val="left" w:pos="720"/>', '<w:tab w:pos="720"/>', 1))
        self.expect_failure('has no w:val')

    def test_wrong_alignment_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace('w:val="left"', 'w:val="center"', 1))
        self.expect_failure('w:val')

    def test_wrong_leader_is_rejected(self):
        self.replace('tabs-leaders', lambda text: text.replace('w:leader="dot"', 'w:leader="heavy"', 1))
        self.expect_failure('w:leader')

    def test_invented_leader_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '<w:tab w:val="left" w:pos="720"/>', '<w:tab w:val="left" w:pos="720" w:leader="dot"/>', 1))
        self.expect_failure('w:leader')

    def test_extra_stop_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '<w:tab w:val="left" w:pos="720"/>',
            '<w:tab w:val="left" w:pos="720"/><w:tab w:val="left" w:pos="1440"/>', 1))
        self.expect_failure('declared 1')

    def test_undeclared_tab_attribute_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '<w:tab w:val="left" w:pos="720"/>', '<w:tab w:val="left" w:pos="720" w:foo="1"/>', 1))
        self.expect_failure('w:tab carries undeclared attributes')

    def test_non_tab_child_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '</w:tabs>', '<w:bogus/></w:tabs>', 1))
        self.expect_failure('non-w:tab child')

    # --- the tri-state flags --------------------------------------------------------------------------
    def test_removed_page_break_is_rejected(self):
        self.replace('break-before', lambda text: text.replace('<w:pageBreakBefore/>', '', 1))
        self.expect_failure('presence')

    def test_explicit_off_written_bare_is_rejected(self):
        self.replace('break-before', lambda text: text.replace(
            '<w:pageBreakBefore w:val="0"/>', '<w:pageBreakBefore/>', 1))
        self.expect_failure('declared off but carries')

    def test_explicit_on_written_as_zero_is_rejected(self):
        self.replace('break-before', lambda text: text.replace(
            '<w:pageBreakBefore/>', '<w:pageBreakBefore w:val="0"/>', 1))
        self.expect_failure('declared on but carries')

    def test_widow_off_written_bare_is_rejected(self):
        self.replace('widow', lambda text: text.replace('<w:widowControl w:val="0"/>', '<w:widowControl/>', 1))
        self.expect_failure('declared off but carries')

    def test_invented_page_break_is_rejected(self):
        self.replace('default', lambda text: text.replace(
            '<w:p><w:r><w:t xml:space="preserve">Plain body.</w:t>',
            '<w:p><w:pPr><w:pageBreakBefore/></w:pPr><w:r><w:t xml:space="preserve">Plain body.</w:t>', 1))
        self.expect_failure('presence')

    # --- sequence order and scope ---------------------------------------------------------------------
    def test_tabs_after_the_spacing_are_rejected(self):
        self.replace('ordered', lambda text: text.replace(
            '<w:tabs><w:tab w:val="right" w:pos="1440" w:leader="dot"/></w:tabs>'
            '<w:spacing w:before="120" w:after="240"/>',
            '<w:spacing w:before="120" w:after="240"/>'
            '<w:tabs><w:tab w:val="right" w:pos="1440" w:leader="dot"/></w:tabs>', 1))
        self.expect_failure('ct_ppr order')

    def test_page_break_after_widow_is_rejected(self):
        self.replace('ordered', lambda text: text.replace(
            '<w:pageBreakBefore/><w:widowControl w:val="0"/>',
            '<w:widowControl w:val="0"/><w:pageBreakBefore/>', 1))
        self.expect_failure('ct_ppr order')

    def test_unknown_property_is_rejected(self):
        self.replace('tabs-basic', lambda text: text.replace(
            '</w:pPr>', '<w:bogus/></w:pPr>', 1))
        self.expect_failure('unknown w:ppr children')

    def test_tabs_on_a_list_item_are_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '</w:numPr></w:pPr>',
            '</w:numPr><w:tabs><w:tab w:val="left" w:pos="720"/></w:tabs></w:pPr>', 1))
        self.expect_failure('carries tab stops')

    def test_tabs_inside_a_table_cell_are_rejected(self):
        self.replace('mixed', lambda text: text.replace(
            '<w:tc><w:p><w:r>',
            '<w:tc><w:p><w:pPr><w:tabs><w:tab w:val="left" w:pos="720"/></w:tabs></w:pPr><w:r>', 1))
        self.expect_failure('table cell carries w:tabs')

    # --- intent table and package bookkeeping ----------------------------------------------------------
    def test_tampered_intent_position_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'tabs-basic':
                    fields[5] = '900:left:none'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('!= declared 900')

    def test_out_of_range_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'tabs-basic':
                    fields[5] = f'{checker.MAX_POSITION + 1}:left:none'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('outside')

    def test_descending_intent_is_rejected(self):
        def transform(lines):
            for index, line in enumerate(lines):
                fields = line.split('\t')
                if fields[0] == 'tabs-basic':
                    fields[5] = '1440:left:none;720:left:none'
                    lines[index] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('not strictly ascending')

    def test_unknown_alignment_intent_is_rejected(self):
        def transform(lines):
            fields = lines[1].split('\t')
            fields[5] = '720:middle:none'
            lines[1] = '\t'.join(fields)
            return '\n'.join(lines) + '\n'
        self.edit_intent(transform)
        self.expect_failure('unknown alignment')

    def test_truncated_intent_row_is_rejected(self):
        self.edit_intent(lambda lines: '\n'.join(lines[:1] + ['\t'.join(lines[1].split('\t')[:4])]) + '\n')
        self.expect_failure('fields, expected 6')

    def test_undeclared_package_is_rejected(self):
        shutil.copy(self.directory / 'default.docx', self.directory / 'stray.docx')
        self.expect_failure('!= declared')

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'tabs.tsv').unlink()
        self.expect_failure('missing intent table')

    # --- the reader's own verdict, independent of the raw rules ----------------------------------------
    def test_python_docx_refuses_a_tab_without_val(self):
        target = self.directory / 'tabs-basic.docx'
        temporary = self.directory / 'tabs-basic.docx.tmp'
        rewrite(target, temporary, document_only(
            lambda text: text.replace('<w:tab w:val="left" w:pos="720"/>', '<w:tab w:pos="720"/>', 1)))
        shutil.move(str(temporary), str(target))
        from docx.oxml.exceptions import InvalidXmlError
        with self.assertRaises(InvalidXmlError):
            checker.check_with_python_docx(target, [{'index': 0, 'kind': 'paragraph',
                                                     'page_break_before': None, 'widow_control': None,
                                                     'tabs': [{'position': 720, 'align': 'left', 'leader': None}]}])


if __name__ == '__main__':
    unittest.main()
