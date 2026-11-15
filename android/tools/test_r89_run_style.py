#!/usr/bin/env python3
"""Negative controls for the R89 Writer run-styling verifier.

Each test tampers one thing — a package part or one intent row — and asserts that
`tools/check_r89_writer_run_style.py` refuses it. The undisturbed artifacts are checked first, because a
verifier that rejects everything would otherwise pass every control.

Skips when the R89 artifacts are absent (CI regenerates them with `tools/run_r89_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r89_run_style -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r89_writer_run_style as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r89/artifacts'


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class RunStyleControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'runs.tsv').is_file():
            raise unittest.SkipTest('R89 artifacts are absent; run tools/run_r89_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r89-runs-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, transform) -> None:
        rewrite(ARTIFACTS / f'{name}.docx', self.folder / f'{name}.docx', 'word/document.xml', transform)

    def fail(self) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, True)

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, True)
        self.assertEqual(totals['packages'], 6)
        self.assertEqual(totals['runs'], 10)
        self.assertEqual(totals['styled'], 8)
        self.assertEqual(totals['reader_runs'], 10)
        self.assertEqual(totals['reader_colours'], 5)
        self.assertEqual(totals['reader_sizes'], 6)

    def test_missing_colour_is_rejected(self):
        self.patch('color', lambda xml: xml.replace('<w:color w:val="FF0000"/>', '', 1))
        self.fail()

    def test_changed_colour_is_rejected(self):
        self.patch('color', lambda xml: xml.replace('FF0000', 'FF0001', 1))
        self.fail()

    def test_lower_case_colour_is_rejected(self):
        self.patch('color', lambda xml: xml.replace('FF0000', 'ff0000', 1))
        self.fail()

    def test_missing_size_is_rejected(self):
        self.patch('size', lambda xml: xml.replace('<w:sz w:val="48"/>', '', 1))
        self.fail()

    def test_changed_size_is_rejected(self):
        self.patch('size', lambda xml: xml.replace('<w:sz w:val="48"/>', '<w:sz w:val="44"/>', 1))
        self.fail()

    def test_reordered_rpr_is_rejected(self):
        def swap(xml: str) -> str:
            colour = '<w:color w:val="1F4E79"/>'
            size = '<w:sz w:val="28"/>'
            return xml.replace(colour + size, size + colour, 1)

        self.patch('both', swap)
        self.fail()

    def test_invented_rpr_is_rejected(self):
        self.patch('plain', lambda xml: xml.replace(
            '<w:r><w:t', '<w:r><w:rPr><w:color w:val="000000"/></w:rPr><w:t', 1))
        self.fail()

    def test_changed_text_is_rejected(self):
        self.patch('size', lambda xml: xml.replace('Big', 'Bg', 1))
        self.fail()

    def test_dropped_intent_row_is_rejected(self):
        rows = (self.folder / 'runs.tsv').read_text(encoding='utf-8').splitlines()
        (self.folder / 'runs.tsv').write_text('\n'.join(rows[:-1]) + '\n', encoding='utf-8')
        self.fail()

    def test_invented_intent_is_rejected(self):
        rows = (self.folder / 'runs.tsv').read_text(encoding='utf-8').splitlines()
        rows[0] = rows[0].replace('\t-\t-\t', '\t00FF00\t-\t', 1)
        (self.folder / 'runs.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail()

    def test_python_docx_path_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'runs.tsv')
        table['color'][0][4] = '00FF00'
        with self.assertRaises(checker.Fail):
            checker.verify_python_docx(self.folder, table)

    def test_cross_package_swap_is_rejected(self):
        shutil.copyfile(self.folder / 'size.docx', self.folder / 'color.docx')
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
