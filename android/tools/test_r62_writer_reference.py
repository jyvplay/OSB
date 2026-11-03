import csv
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

import check_r62_writer_reference as reference


class WriterReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.csv').is_file():
            raise unittest.SkipTest('Run actual R62 Writer contracts first')
        from docx import Document  # actual reader must be installed when artifacts exist
        cls.document_type = Document

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.csv').open(newline='', encoding='utf-8') as f: self.rows = list(csv.DictReader(f))
    def tearDown(self): self.tmp.cleanup()
    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, zipfile.BadZipFile)):
            reference.check(self.folder)
    def save_manifest(self):
        with (self.folder / 'cases.csv').open('w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=reference.FIELDS); writer.writeheader(); writer.writerows(self.rows)
    def mutate(self, name, before, after):
        path = self.folder / name
        with zipfile.ZipFile(path) as z: entries = [(e, z.read(e)) for e in z.infolist()]
        with zipfile.ZipFile(path, 'w') as z:
            for info, data in entries:
                if info.filename == 'word/document.xml':
                    self.assertIn(before, data); data = data.replace(before, after, 1)
                z.writestr(info, data)

    def test_actual_outputs(self): self.assertGreater(reference.check(self.folder), 8000)
    def test_missing_strike_not_accepted(self):
        self.mutate('doc-064.docx', b'<w:strike/>', b''); self.rejected()
    def test_strike_cannot_be_replaced_with_bold(self):
        self.mutate('doc-064.docx', b'<w:strike/>', b'<w:b/>'); self.rejected()
    def test_default_cannot_gain_strike(self):
        self.mutate('doc-000.docx', b'<w:b/>', b'<w:strike/><w:b/>'); self.rejected()
    def test_numbering_cannot_change(self):
        self.mutate('doc-192.docx', b'<w:numId w:val="2"/>', b'<w:numId w:val="1"/>'); self.rejected()
    def test_nested_ambiguous_text_cannot_be_silently_dropped(self):
        self.mutate('doc-064.docx', b'~~a **nested** b~~', b'a nested b'); self.rejected()
    def test_option_manifest_cannot_lie(self):
        self.rows[64]['strike'] = 'false'; self.save_manifest(); self.rejected()
    def test_missing_corrupt_extra_and_symlink_refused(self):
        file = self.folder / 'doc-064.docx'; file.unlink(); self.rejected()
        file.write_bytes(b'not a zip'); self.rejected()
        file.unlink(); file.symlink_to(reference.ARTIFACTS / file.name); self.rejected()
        file.unlink(); shutil.copyfile(reference.ARTIFACTS / file.name, file)
        (self.folder / 'doc-999.docx').write_bytes(b'extra'); self.rejected()
    def test_duplicate_zip_part_refused(self):
        file = self.folder / 'doc-064.docx'
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(file, 'a') as z: z.writestr('word/document.xml', b'<document/>')
        self.rejected()


class WriterBuildHygiene(unittest.TestCase):
    def test_stdlib_discovery_without_artifacts_and_missing_reader(self):
        with tempfile.TemporaryDirectory() as folder:
            script = '''
import sys, unittest
from pathlib import Path
import test_r62_writer_reference as tests
import check_r62_writer_reference as reference
reference.ARTIFACTS = Path(sys.argv[1]) / "missing"
result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(tests.WriterReferenceAttacks))
assert result.wasSuccessful() and len(result.skipped) == 1
try: reference.check(reference.ARTIFACTS)
except RuntimeError as e: assert "python-docx" in str(e)
else: raise AssertionError("Reader must require python-docx")
'''
            env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parent))
            result = subprocess.run([sys.executable, '-S', '-c', script, folder], env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
    def test_compiler_failure_removes_only_named_stale_outputs(self):
        import run_r62_writer as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); out = root / 'testing/build/r62writer/artifacts'; out.mkdir(parents=True)
            stale = [out / 'doc-000.docx', out / 'cases.csv', out.parent / 'r62-writer.jar']
            for path in stale: path.write_bytes(b'stale')
            unrelated = out / 'important.docx'; unrelated.write_bytes(b'keep')
            env = dict(os.environ, KOTLINC=str(root / 'missing'), JAVA_HOME=str(root / 'missing'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError): runner.main()
            self.assertTrue(all(not p.exists() for p in stale)); self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__': unittest.main()
