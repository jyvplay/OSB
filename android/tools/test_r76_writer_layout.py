#!/usr/bin/env python3
"""Negative controls for the R76 Writer layout verifier.

Each control copies a real package produced by the Kotlin probe, breaks exactly one thing, and
asserts that tools/check_r76_writer_layout.py refuses the result. The controls skip when the
artifacts are absent (they are produced by tools/run_r76_writer_layout.py and by CI).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r76_writer_layout -v
"""
from __future__ import annotations

import re
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r76_writer_layout as checker

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r76writer/artifacts'
REFERENCE = ARTIFACTS / 'a4-footer.docx'


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


def write_intent(directory: Path, rows) -> None:
    (directory / 'layout.tsv').write_text(
        '\n'.join('\t'.join(str(field) for field in row) for row in rows) + '\n', encoding='utf-8')


class LayoutControlTests(unittest.TestCase):
    def setUp(self):
        if not REFERENCE.is_file():
            self.skipTest('R76 writer layout artifacts are absent; run tools/run_r76_writer_layout.py')
        self.directory = Path(tempfile.mkdtemp(prefix='r76-layout-'))
        self.addCleanup(shutil.rmtree, self.directory, ignore_errors=True)
        for name in ('a4-footer', 'letter-landscape', 'mixed-layout', 'columns-restart-odd',
                     'continuous-columns', 'first-page', 'legacy'):
            shutil.copy(ARTIFACTS / f'{name}.docx', self.directory / f'{name}.docx')
        shutil.copy(ARTIFACTS / 'layout.tsv', self.directory / 'layout.tsv')

    def verify(self):
        return checker.verify(self.directory)

    def expect_failure(self, fragment: str):
        with self.assertRaises(checker.Fail) as caught:
            self.verify()
        self.assertIn(fragment.lower(), str(caught.exception).lower(), str(caught.exception))

    def test_undisturbed_packages_pass(self):
        self.assertEqual(self.verify()['layout_documents'], 6)

    def test_missing_footer_part_is_rejected(self):
        rewrite(REFERENCE, self.directory / 'a4-footer.docx',
                lambda name, payload: None if name == 'word/footer1.xml' else payload)
        self.expect_failure('missing part')

    def test_missing_content_type_override_is_rejected(self):
        def transform(name, payload):
            if name != '[Content_Types].xml':
                return payload
            text = payload.decode('utf-8')
            text = re.sub(r'<Override PartName="/word/footer1\.xml"[^>]*/>', '', text)
            return text.encode('utf-8')
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('content-type')

    def test_wrong_page_width_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'w:w="11906"', b'w:w="11905"', 1)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('pgsz')

    def test_swapped_orientation_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'w:h="16838"/>', b'w:h="16838" w:orient="landscape"/>', 1)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('orient')

    def test_changed_margin_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'w:left="1440"', b'w:left="1441"', 1)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('margin')

    def test_duplicate_relationship_id_is_rejected(self):
        def transform(name, payload):
            if name != 'word/_rels/document.xml.rels':
                return payload
            return payload.replace(b'Id="rIdFtr1"', b'Id="rId1"', 1)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('duplicate relationship')

    def test_unknown_field_instruction_is_rejected(self):
        def transform(name, payload):
            if name != 'word/footer1.xml':
                return payload
            return payload.replace(b'w:instr=" PAGE "', b'w:instr=" DATE "', 1)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('unsupported field')

    def test_missing_reference_with_a_part_present_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return re.sub(rb'<w:footerReference[^/]*/>', b'', payload)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('missing footer reference')

    def test_legacy_package_gaining_a_header_is_rejected(self):
        rewrite(ARTIFACTS / 'a4-footer.docx', self.directory / 'legacy.docx',
                lambda name, payload: payload)
        with self.assertRaises(checker.Fail):
            self.verify()

    def test_unexpected_package_is_rejected(self):
        shutil.copy(ARTIFACTS / 'a4-footer.docx', self.directory / 'stowaway.docx')
        self.expect_failure('unexpected packages')

    def test_missing_intent_table_is_rejected(self):
        (self.directory / 'layout.tsv').unlink()
        self.expect_failure('intent table')

    def test_extra_column_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'w:num="3"', b'w:num="4"', 1)
        rewrite(ARTIFACTS / 'columns-restart-odd.docx', self.directory / 'columns-restart-odd.docx', transform)
        self.expect_failure('w:cols')

    def test_page_number_restart_removed_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return re.sub(rb'<w:pgNumType[^/]*/>', b'', payload)
        rewrite(ARTIFACTS / 'columns-restart-odd.docx', self.directory / 'columns-restart-odd.docx', transform)
        self.expect_failure('pgnumtype')

    def test_section_type_on_a_next_page_section_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'<w:pgSz ', b'<w:type w:val="continuous"/><w:pgSz ', 1)
        rewrite(REFERENCE, self.directory / 'a4-footer.docx', transform)
        self.expect_failure('nextpage section must not carry w:type')

    def test_title_page_element_removed_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'<w:titlePg/>', b'', 1)
        rewrite(ARTIFACTS / 'first-page.docx', self.directory / 'first-page.docx', transform)
        self.expect_failure('titlepg is absent')

    def test_first_reference_downgraded_to_default_is_rejected(self):
        def transform(name, payload):
            if name != 'word/document.xml':
                return payload
            return payload.replace(b'w:type="first"', b'w:type="default"', 1)
        rewrite(ARTIFACTS / 'first-page.docx', self.directory / 'first-page.docx', transform)
        self.expect_failure('references of headerReference')

    def test_changed_first_page_text_is_rejected(self):
        def transform(name, payload):
            if name != 'word/header2.xml':
                return payload
            return payload.replace(b'Cover - not for circulation', b'Cover - for circulation', 1)
        rewrite(ARTIFACTS / 'first-page.docx', self.directory / 'first-page.docx', transform)
        self.expect_failure('first_header text')

    def test_missing_first_page_part_is_rejected(self):
        rewrite(ARTIFACTS / 'first-page.docx', self.directory / 'first-page.docx',
                lambda name, payload: None if name == 'word/footer2.xml' else payload)
        self.expect_failure('missing part')

    def test_declared_footer_without_text_is_rejected(self):
        rows = [line.split('\t') for line in (self.directory / 'layout.tsv').read_text().splitlines()]
        rows[0][11] = ''  # footer column of the first row
        write_intent(self.directory, rows)
        self.expect_failure('unexpected footer reference')


if __name__ == '__main__':
    unittest.main()
