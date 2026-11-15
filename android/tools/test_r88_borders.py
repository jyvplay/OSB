#!/usr/bin/env python3
"""Negative controls for the R88 Writer paragraph-border verifier.

Every test tampers exactly one thing — a package part, or the intent table — and asserts that
`tools/check_r88_writer_borders.py` refuses it. The undisturbed artifacts are checked first, because a
verifier that rejects everything would otherwise pass every control.

The controls skip when the R88 artifacts are absent (CI regenerates them with `tools/run_r88_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r88_borders -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path
from xml.etree import ElementTree

import check_r88_writer_borders as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r88/artifacts'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    """Copy one package, applying `transform` to the text of a single OPC part."""
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class BorderByteControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'borders.tsv').is_file():
            raise unittest.SkipTest('R88 artifacts are absent; run tools/run_r88_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r88-borders-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, transform) -> None:
        target = self.folder / f'{name}.docx'
        rewrite(ARTIFACTS / f'{name}.docx', target, 'word/document.xml', transform)

    def fail(self, require_docx: bool = True) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_docx)

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, True)
        self.assertEqual(totals['packages'], 5)
        self.assertEqual(totals['paragraphs'], 7)
        self.assertEqual(totals['bordered'], 5)
        self.assertEqual(totals['edges'], 14)
        self.assertEqual(totals['reader_paragraphs'], 7)
        self.assertEqual(totals['reader_bordered'], 5)

    def test_missing_edge_is_rejected(self):
        self.patch('box', lambda xml: xml.replace(
            '<w:left w:val="single" w:sz="8" w:space="1" w:color="1F4E79"/>', '', 1))
        self.fail()

    def test_changed_size_is_rejected(self):
        self.patch('box', lambda xml: xml.replace(
            '<w:top w:val="single" w:sz="8" w:space="1" w:color="1F4E79"/>',
            '<w:top w:val="single" w:sz="9" w:space="1" w:color="1F4E79"/>', 1))
        self.fail()

    def test_changed_colour_is_rejected(self):
        self.patch('box', lambda xml: xml.replace('1F4E79', '1F4E80', 1))
        self.fail()

    def test_lower_case_colour_is_rejected(self):
        # The model keeps the caller's case but the wire form is upper-case; the verifier compares bytes.
        self.patch('box', lambda xml: xml.replace('1F4E79', '1f4e79', 1))
        self.fail()

    def test_missing_attribute_is_rejected(self):
        self.patch('box', lambda xml: xml.replace(' w:space="1" w:color="1F4E79"', ' w:color="1F4E79"', 1))
        self.fail()

    def test_undeclared_edge_is_rejected(self):
        self.patch('plain', lambda xml: xml.replace(
            '<w:p><w:r>', '<w:p><w:pPr><w:pBdr><w:top w:val="single" w:sz="8" w:space="1" '
            'w:color="000000"/></w:pBdr></w:pPr><w:r>', 1))
        self.fail()

    def test_out_of_order_edges_are_rejected(self):
        def swap(xml: str) -> str:
            top = '<w:top w:val="double" w:sz="8" w:space="1" w:color="000000"/>'
            bottom = '<w:bottom w:val="dotted" w:sz="8" w:space="1" w:color="000000"/>'
            xml = xml.replace(top, '<w:__swap__/>', 1).replace(bottom, top, 1)
            return xml.replace('<w:__swap__/>', bottom, 1)

        self.patch('styles', swap)
        self.fail()

    def test_pbdr_after_shd_is_rejected(self):
        def move(xml: str) -> str:
            start = xml.index('<w:pBdr>')
            end = xml.index('</w:pBdr>') + len('</w:pBdr>')
            box = xml[start:end]
            xml = xml[:start] + xml[end:]
            shd = xml.index('<w:shd ')
            after = xml.index('/>', shd) + 2
            return xml[:after] + box + xml[after:]

        self.patch('combined', move)
        self.fail()

    def test_empty_pbdr_is_rejected(self):
        def empty(xml: str) -> str:
            start = xml.index('<w:pBdr>')
            end = xml.index('</w:pBdr>') + len('</w:pBdr>')
            return xml[:start] + '<w:pBdr></w:pBdr>' + xml[end:]

        self.patch('box', empty)
        self.fail()

    def test_dropped_intent_row_is_rejected(self):
        table = (self.folder / 'borders.tsv').read_text(encoding='utf-8').splitlines()
        (self.folder / 'borders.tsv').write_text('\n'.join(table[:-1]) + '\n', encoding='utf-8')
        self.fail()

    def test_invented_box_is_rejected(self):
        rows = (self.folder / 'borders.tsv').read_text(encoding='utf-8').splitlines()
        rows[1] = '\t'.join(['plain', '1', 'paragraph', 'top:single:8:1:000000', '-', '-'])
        (self.folder / 'borders.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail()

    def test_python_docx_path_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'borders.tsv')
        rows = table['box']
        rows[0]['edges'] = [{'edge': 'top', 'style': 'single', 'size': 8, 'space': 1,
                             'color': '808080'}]
        with self.assertRaises(checker.Fail):
            checker.verify_python_docx(self.folder, table)

    def test_cross_package_swap_is_rejected(self):
        # The intent table is per package: swapping two packages must not pass.
        shutil.copyfile(self.folder / 'boundary.docx', self.folder / 'box.docx')
        self.fail()

    def test_document_part_is_required(self):
        target = self.folder / 'plain.docx'
        with zipfile.ZipFile(target) as reader:
            parts = {item.filename: reader.read(item.filename) for item in reader.infolist()}
        parts.pop('word/document.xml')
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
            for name, payload in parts.items():
                writer.writestr(name, payload)
        self.fail()


if __name__ == '__main__':
    unittest.main()
