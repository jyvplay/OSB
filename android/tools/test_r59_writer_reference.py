import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from check_r59_writer_reference import ARTIFACTS, validate


class WriterCorpusReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'manifest.tsv').is_file():
            raise unittest.SkipTest('Run actual R59 Writer production corpus first')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / 'corpus'
        shutil.copytree(ARTIFACTS, self.root)

    def tearDown(self):
        self.temp.cleanup()

    def rejected(self):
        from docx.opc.exceptions import PackageNotFoundError
        with self.assertRaises((ValueError, KeyError, OSError, zipfile.BadZipFile, PackageNotFoundError)):
            validate(self.root)

    def mutate(self, before, after, name='doc-000-structured.docx'):
        path = self.root / name
        with zipfile.ZipFile(path) as package:
            items = [(entry, package.read(entry)) for entry in package.infolist()]
        changed = False
        with zipfile.ZipFile(path, 'w') as package:
            for entry, data in items:
                if entry.filename == 'word/document.xml':
                    self.assertEqual(data.count(before), 1)
                    data = data.replace(before, after)
                    changed = True
                package.writestr(entry, data)
        self.assertTrue(changed)

    def test_actual_writer_corpus(self):
        self.assertEqual(validate(self.root), 1801)

    def test_block_heading_and_bullet_mutations(self):
        self.mutate(b'<w:pStyle w:val="Heading1"/>', b'<w:pStyle w:val="Normal"/>')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-structured.docx', self.root / 'doc-000-structured.docx')
        self.mutate(b'<w:numId w:val="1"/>', b'<w:numId w:val="2"/>')
        self.rejected()

    def test_inline_bold_italic_and_mono_mutations(self):
        for before, after in ((b'<w:b/>', b'<w:b w:val="0"/>'), (b'<w:i/>', b'<w:i w:val="0"/>'),
                              (b'w:ascii="monospace" w:hAnsi="monospace"', b'w:ascii="serif" w:hAnsi="serif"')):
            shutil.copyfile(ARTIFACTS / 'doc-000-structured.docx', self.root / 'doc-000-structured.docx')
            self.mutate(before, after)
            self.rejected()

    def test_unmatched_marker_and_plain_text_mutations(self):
        self.mutate(b'>**unclosed ', b'>unclosed ')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-structured.docx', self.root / 'doc-000-structured.docx')
        self.mutate(b'># Hx', b'># Jx', name='doc-000-plain.docx')
        self.rejected()

    def test_missing_corrupt_and_duplicate_archive(self):
        target = self.root / 'doc-000-structured.docx'
        target.unlink(); self.rejected()
        target.write_bytes(b'not a document'); self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-structured.docx', target)
        with zipfile.ZipFile(target, 'a') as package:
            package.writestr('word/document.xml', package.read('word/document.xml'))
        self.rejected()

    def test_manifest_mutation(self):
        path = self.root / 'manifest.tsv'
        lines = path.read_text().splitlines()
        path.write_text('\n'.join(lines[:-1]) + '\n')
        self.rejected()


class WriterBuildHygieneTests(unittest.TestCase):
    def test_compiler_failure_removes_only_named_stale_outputs(self):
        import run_r59_writer as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r59writer/artifacts'
            out.mkdir(parents=True)
            stale = [out / 'manifest.tsv', out / 'doc-000-plain.docx', out / 'doc-000-structured.docx', out.parent / 'r59-writer.jar']
            for path in stale: path.write_bytes(b'stale')
            unrelated = out / 'doc-user-important.docx'
            unrelated.write_bytes(b'keep')
            env = dict(os.environ, KOTLINC=str(root / 'missing-compiler'), JAVA_HOME=str(root / 'missing-java'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError): runner.main()
            self.assertTrue(all(not path.exists() for path in stale))
            self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__':
    unittest.main()
