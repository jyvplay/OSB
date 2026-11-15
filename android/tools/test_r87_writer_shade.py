#!/usr/bin/env python3
"""Negative controls for the R87 Writer paragraph-shading and tab-character verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and asserts that
tools/check_r87_writer_shade.py refuses the result. The controls skip when the artifacts are absent (they are
produced by tools/run_r87_writer_shade.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r87_writer_shade -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r87_writer_shade as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r87writer/artifacts'
DOCUMENTS = ('plain', 'shaded', 'shade-lower', 'shade-and-tabs', 'toc', 'mixed', 'boundary')


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


class ShadingControlTests(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'shade.tsv').is_file():
            self.skipTest('R87 writer shade artifacts are absent; run tools/run_r87_writer_shade.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r87-shade-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in DOCUMENTS:
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'shade.tsv', self.directory / 'shade.tsv')

    def verify(self):
        return checker.verify(self.directory, require_reader=False)

    def replace(self, document: str, transform):
        target = self.directory / f'{document}.docx'
        temporary = self.directory / f'{document}.docx.tmp'
        rewrite(target, temporary, document_only(transform))
        temporary.replace(target)

    # --- the undisturbed corpus must pass -------------------------------------------------------
    def test_undisturbed_packages_pass(self):
        totals = self.verify()
        self.assertEqual(totals['packages'], len(DOCUMENTS))
        self.assertEqual(totals['shaded'], 6)

    # --- the fill itself ------------------------------------------------------------------------
    def test_removed_fill_is_rejected(self):
        self.replace('shaded', lambda xml: xml.replace(
            '<w:shd w:val="clear" w:color="auto" w:fill="ABCDEF"/>', '', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_changed_fill_is_rejected(self):
        self.replace('shaded', lambda xml: xml.replace('w:fill="ABCDEF"', 'w:fill="FEDCBA"', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_pattern_fill_is_rejected(self):
        self.replace('shaded', lambda xml: xml.replace('w:val="clear"', 'w:val="pct20"', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_foreground_colour_is_rejected(self):
        self.replace('shaded', lambda xml: xml.replace('w:color="auto"', 'w:color="FF0000"', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_invented_fill_on_a_plain_paragraph_is_rejected(self):
        self.replace('plain', lambda xml: xml.replace(
            '<w:p><w:r><w:t xml:space="preserve">Body</w:t></w:r></w:p>',
            '<w:p><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="FFFFFF"/></w:pPr>'
            '<w:r><w:t xml:space="preserve">Body</w:t></w:r></w:p>', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    # --- CT_PPr order ---------------------------------------------------------------------------
    def test_fill_after_the_tabs_is_rejected(self):
        def transform(xml: str) -> str:
            shading = '<w:shd w:val="clear" w:color="auto" w:fill="00B050"/>'
            return xml.replace(shading, '', 1).replace('</w:tabs>', '</w:tabs>' + shading, 1)
        self.replace('shade-and-tabs', transform)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_fill_before_widow_control_is_rejected(self):
        self.replace('boundary', lambda xml: xml.replace(
            '<w:widowControl/><w:shd w:val="clear" w:color="auto" w:fill="FFFFFF"/>',
            '<w:shd w:val="clear" w:color="auto" w:fill="FFFFFF"/><w:widowControl/>', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_fill_after_the_alignment_is_rejected(self):
        def transform(xml: str) -> str:
            shading = '<w:shd w:val="clear" w:color="auto" w:fill="FFFFFF"/>'
            return xml.replace(shading, '', 1).replace('</w:pPr>', shading + '</w:pPr>', 1)
        self.replace('boundary', transform)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_fill_inside_a_table_cell_is_rejected(self):
        self.replace('mixed', lambda xml: xml.replace(
            '<w:tc><w:p>', '<w:tc><w:p><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="000000"/></w:pPr>', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    # --- the tab character path -----------------------------------------------------------------
    def test_removed_tab_character_is_rejected(self):
        self.replace('toc', lambda xml: xml.replace('<w:r><w:tab/></w:r>', '', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_extra_tab_character_is_rejected(self):
        self.replace('toc', lambda xml: xml.replace(
            '<w:t xml:space="preserve">7</w:t>', '<w:tab/><w:t xml:space="preserve">7</w:t>', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_tab_stop_cannot_stand_in_for_a_tab_character(self):
        # A `w:tab` inside `w:tabs` is a stop, not a character: a checker that counted every `w:tab`
        # element would see three here and pass, while the rendered line has no tab at all.
        def transform(xml: str) -> str:
            xml = xml.replace('<w:r><w:tab/></w:r>', '', 1)
            return xml.replace('</w:tabs>', '<w:tab w:val="left" w:pos="1440"/></w:tabs>', 1)
        self.replace('toc', transform)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_soft_break_injected_into_text_is_rejected(self):
        self.replace('plain', lambda xml: xml.replace(
            '<w:t xml:space="preserve">Body</w:t>', '<w:t xml:space="preserve">Bo</w:t><w:br/><w:t xml:space="preserve">dy</w:t>', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    # --- structure ------------------------------------------------------------------------------
    def test_removed_paragraph_is_rejected(self):
        def transform(xml: str) -> str:
            start = xml.index('<w:p>', xml.index('<w:body>'))
            end = xml.index('</w:p>', start) + len('</w:p>')
            return xml[:start] + xml[end:]
        self.replace('plain', transform)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_undeclared_package_is_rejected(self):
        shutil.copy(ARTIFACTS / 'plain.docx', self.directory / 'extra.docx')
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_missing_intent_file_is_rejected(self):
        (self.directory / 'shade.tsv').unlink()
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_truncated_intent_row_is_rejected(self):
        (self.directory / 'shade.tsv').write_text('plain\t0\theading\t-\t-\n')
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_invalid_fill_in_the_intent_table_is_rejected(self):
        table = (self.directory / 'shade.tsv').read_text().replace('\tABCDEF\t', '\tABC\t', 1)
        (self.directory / 'shade.tsv').write_text(table)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_shifted_paragraph_index_is_rejected(self):
        table = (self.directory / 'shade.tsv').read_text().replace('shaded\t1\t', 'shaded\t2\t', 1)
        (self.directory / 'shade.tsv').write_text(table)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_removed_tab_stop_is_rejected(self):
        self.replace('toc', lambda xml: xml.replace('<w:tab w:val="right" w:pos="7200" w:leader="dot"/>', '', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_changed_tab_leader_is_rejected(self):
        self.replace('toc', lambda xml: xml.replace('w:leader="dot"', 'w:leader="heavy"', 1))
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_descending_tab_stops_are_rejected(self):
        def transform(xml: str) -> str:
            return xml.replace(
                '<w:tab w:val="left" w:pos="1440"/><w:tab w:val="right" w:pos="7200" w:leader="dot"/>',
                '<w:tab w:val="right" w:pos="7200" w:leader="dot"/><w:tab w:val="left" w:pos="1440"/>', 1)
        self.replace('toc', transform)
        with self.assertRaises(checker.Fail):
            self.verify()



if __name__ == '__main__':
    unittest.main()
