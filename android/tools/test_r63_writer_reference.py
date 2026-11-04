import csv
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from zipfile import BadZipFile, ZipFile

import check_r63_writer_reference as reference


class WriterRestartReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.csv').is_file():
            raise unittest.SkipTest('Run actual R63 Writer contracts first')
        from docx import Document  # noqa: F401

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.csv').open(newline='', encoding='utf-8') as stream:
            self.rows = list(csv.DictReader(stream))

    def tearDown(self):
        self.tmp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, BadZipFile)):
            reference.check(self.folder)

    def mutate(self, name, part, before, after):
        path = self.folder / name
        with ZipFile(path) as package:
            entries = [(entry, package.read(entry)) for entry in package.infolist()]
        with ZipFile(path, 'w') as package:
            for entry, data in entries:
                if entry.filename == part:
                    self.assertIn(before, data)
                    data = data.replace(before, after, 1)
                package.writestr(entry, data)

    def save_manifest(self):
        with (self.folder / 'cases.csv').open('w', newline='', encoding='utf-8') as stream:
            writer = csv.DictWriter(stream, fieldnames=reference.FIELDS)
            writer.writeheader()
            writer.writerows(self.rows)

    def test_actual_outputs(self):
        self.assertGreater(reference.check(self.folder), 6500)

    def test_new_id_without_start_override_is_detected(self):
        self.mutate('doc-096.docx', 'word/numbering.xml',
                    b'<w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/></w:lvlOverride>', b'')
        self.rejected()

    def test_wrong_restart_value_is_detected(self):
        self.mutate('doc-096.docx', 'word/numbering.xml', b'<w:startOverride w:val="1"/>', b'<w:startOverride w:val="2"/>')
        self.rejected()

    def test_wrong_restart_level_is_detected(self):
        self.mutate('doc-096.docx', 'word/numbering.xml', b'<w:lvlOverride w:ilvl="0">', b'<w:lvlOverride w:ilvl="1">')
        self.rejected()

    def test_wrong_abstract_definition_is_detected(self):
        self.mutate('doc-096.docx', 'word/numbering.xml',
                    b'<w:num w:numId="3"><w:abstractNumId w:val="1"/>',
                    b'<w:num w:numId="3"><w:abstractNumId w:val="0"/>')
        self.rejected()

    def test_merged_groups_are_detected(self):
        self.mutate('doc-096.docx', 'word/document.xml', b'<w:numId w:val="3"/>', b'<w:numId w:val="2"/>')
        self.rejected()

    def test_missing_instance_is_detected(self):
        self.mutate('doc-096.docx', 'word/numbering.xml', b'w:numId="3"', b'w:numId="9"')
        self.rejected()

    def test_restart_cannot_appear_in_legacy_output(self):
        self.mutate('doc-064.docx', 'word/numbering.xml', b'</w:numbering>',
                    b'<w:num w:numId="3"><w:abstractNumId w:val="1"/></w:num></w:numbering>')
        self.rejected()

    def test_unrecognized_number_prefix_cannot_disappear(self):
        self.mutate('doc-096.docx', 'word/document.xml', b'2. literal', b'literal')
        self.rejected()

    def test_strike_option_is_checked(self):
        self.mutate('doc-160.docx', 'word/document.xml', b'<w:strike/>', b'')
        self.rejected()

    def test_manifest_cannot_claim_restart_was_off(self):
        self.rows[96]['restart'] = 'false'
        self.save_manifest()
        self.rejected()

    def test_missing_corrupt_extra_and_symlink_refused(self):
        file = self.folder / 'doc-096.docx'
        file.unlink(); self.rejected()
        file.write_bytes(b'not a document'); self.rejected()
        file.unlink(); file.symlink_to(reference.ARTIFACTS / file.name); self.rejected()
        file.unlink(); shutil.copyfile(reference.ARTIFACTS / file.name, file)
        (self.folder / 'extra.docx').write_bytes(b'extra'); self.rejected()


class WriterRestartBuildHygiene(unittest.TestCase):
    def test_missing_corpus_discovery_and_missing_reader(self):
        with tempfile.TemporaryDirectory() as folder:
            script = '''
import sys, unittest
from pathlib import Path
import test_r63_writer_reference as tests
import check_r63_writer_reference as reference
reference.ARTIFACTS = Path(sys.argv[1]) / "missing"
result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(tests.WriterRestartReferenceAttacks))
assert result.wasSuccessful() and len(result.skipped) == 1
try: reference.check(reference.ARTIFACTS)
except RuntimeError as e: assert "python-docx" in str(e)
else: raise AssertionError("Reader must require python-docx")
'''
            env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parent))
            result = subprocess.run([sys.executable, '-S', '-c', script, folder], env=env,
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_compiler_invocation_failure_cleans_only_owned_artifacts(self):
        import run_r63_writer_restart as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r63writer/artifacts'
            out.mkdir(parents=True)
            stale = [out / 'doc-000.docx', out / 'cases.csv', out.parent / 'r63-writer.jar']
            for path in stale:
                path.write_bytes(b'stale')
            unrelated = out / 'important.docx'
            unrelated.write_bytes(b'keep')
            tools = root / 'tools'
            tools.mkdir()
            (tools / 'run_r63_writer_restart.py').write_bytes(Path(runner.__file__).read_bytes())
            (tools / 'bootstrap_host_tools.py').write_bytes(Path(runner.bootstrap_host_tools.__file__).read_bytes())
            # Exercise the real runner and compiler with absent production sources.
            result = subprocess.run([sys.executable, str(tools / 'run_r63_writer_restart.py')], capture_output=True, timeout=30)
            self.assertNotEqual(result.returncode, 0)
            self.assertTrue(all(not path.exists() for path in stale))
            self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__':
    unittest.main()
