#!/usr/bin/env python3
"""Negative controls for the R90 Writer decoration verifier (`w:vertAlign`, `w:highlight`).

Each test tampers one thing — a package part or one intent row — and asserts that
`tools/check_r90_writer_decorations.py` refuses it. The undisturbed artifacts are checked first, so a
verifier that rejects everything cannot pass.

Skips when the R90 artifacts are absent (CI regenerates them with `tools/run_r90_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r90_decorations -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r90_writer_decorations as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r90/artifacts'
HAVE_DOCX = importlib.util.find_spec('docx') is not None


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class DecorationControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'decorations.tsv').is_file():
            raise unittest.SkipTest('R90 artifacts are absent; run tools/run_r90_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r90-runs-'))

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
            checker.verify(self.folder, HAVE_DOCX)

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_DOCX)
        self.assertEqual(totals['packages'], 5)
        self.assertEqual(totals['runs'], 10)
        self.assertEqual(totals['aligned'], 4)
        self.assertEqual(totals['highlighted'], 5)
        if HAVE_DOCX:                      # python-docx is installed in CI, not necessarily on a host
            self.assertEqual(totals['reader_aligned'], 4)
            self.assertEqual(totals['reader_highlighted'], 5)

    def test_missing_alignment_is_rejected(self):
        self.patch('align', lambda xml: xml.replace('<w:vertAlign w:val="superscript"/>', '', 1))
        self.fail()

    def test_changed_alignment_is_rejected(self):
        self.patch('align', lambda xml: xml.replace('"superscript"', '"subscript"', 1))
        self.fail()

    def test_missing_highlight_is_rejected(self):
        self.patch('highlight', lambda xml: xml.replace('<w:highlight w:val="yellow"/>', '', 1))
        self.fail()

    def test_changed_highlight_is_rejected(self):
        self.patch('highlight', lambda xml: xml.replace('w:val="yellow"', 'w:val="green"', 1))
        self.fail()

    def test_reordered_rpr_is_rejected(self):
        def swap(xml: str) -> str:
            highlight = '<w:highlight w:val="cyan"/>'
            size = '<w:sz w:val="28"/>'
            return xml.replace(size + highlight, highlight + size, 1)

        self.patch('combined', swap)
        self.fail()

    def test_invented_decoration_is_rejected(self):
        self.patch('plain', lambda xml: xml.replace(
            '<w:r><w:t', '<w:r><w:rPr><w:highlight w:val="yellow"/></w:rPr><w:t', 1))
        self.fail()

    def test_dropped_intent_row_is_rejected(self):
        rows = (self.folder / 'decorations.tsv').read_text(encoding='utf-8').splitlines()
        (self.folder / 'decorations.tsv').write_text('\n'.join(rows[:-1]) + '\n', encoding='utf-8')
        self.fail()

    def test_invented_intent_is_rejected(self):
        rows = (self.folder / 'decorations.tsv').read_text(encoding='utf-8').splitlines()
        fields = rows[0].split('\t')
        fields[5] = 'red'
        rows[0] = '\t'.join(fields)
        (self.folder / 'decorations.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')
        self.fail()

    @unittest.skipUnless(HAVE_DOCX, 'python-docx is not installed')
    def test_unmapped_highlight_mapping_is_refused(self):
        # An ST token this verifier has no documented WD_COLOR_INDEX for must be refused, not guessed.
        table = checker.intent_table(self.folder / 'decorations.tsv')
        table['highlight'][0][5] = 'darkCyan'
        with self.assertRaises(checker.Fail):
            checker.verify_python_docx(self.folder, table)

    @unittest.skipUnless(HAVE_DOCX, 'python-docx is not installed')
    def test_python_docx_path_disagrees_with_a_tampered_table(self):
        table = checker.intent_table(self.folder / 'decorations.tsv')
        table['align'][0][4] = 'subscript'
        with self.assertRaises(checker.Fail):
            checker.verify_python_docx(self.folder, table)

    def test_cross_package_swap_is_rejected(self):
        shutil.copyfile(self.folder / 'plain.docx', self.folder / 'align.docx')
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
