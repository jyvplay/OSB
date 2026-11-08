import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

from check_r60_writer_reference import ARTIFACTS, validate


class NumberedWriterReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'doc-000-numbered.docx').is_file():
            raise unittest.SkipTest('Run actual R60 Writer contracts first')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for mode in ('default', 'numbered'):
            name = f'doc-000-{mode}.docx'
            shutil.copyfile(ARTIFACTS / name, self.root / name)

    def tearDown(self):
        self.temp.cleanup()

    def rejected(self):
        from docx.opc.exceptions import PackageNotFoundError
        with self.assertRaises((ValueError, KeyError, OSError, zipfile.BadZipFile, PackageNotFoundError)):
            validate(self.root, count=1)

    def mutate(self, part, before, after, mode='numbered', occurrence=1):
        path = self.root / f'doc-000-{mode}.docx'
        with zipfile.ZipFile(path) as package:
            items = [(entry, package.read(entry)) for entry in package.infolist()]
        changed = False
        with zipfile.ZipFile(path, 'w') as package:
            for entry, data in items:
                if entry.filename == part:
                    self.assertGreaterEqual(data.count(before), occurrence)
                    sections = data.split(before)
                    sections[occurrence - 1] += after
                    data = before.join(sections[:occurrence]) + before.join(sections[occurrence:])
                    # The reconstruction above replaced the selected occurrence only.
                    changed = True
                package.writestr(entry, data)
        self.assertTrue(changed)

    def test_actual_output(self):
        self.assertEqual(validate(self.root, count=1), 43)

    def test_unchosen_mode_must_keep_literal_prefix(self):
        self.mutate('word/document.xml', b'>1. item-0 ', b'>item-0 ', mode='default')
        self.rejected()

    def test_wrong_numbering_id_and_level(self):
        self.mutate('word/document.xml', b'<w:numId w:val="2"/>', b'<w:numId w:val="1"/>')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-numbered.docx', self.root / 'doc-000-numbered.docx')
        self.mutate('word/document.xml', b'<w:ilvl w:val="0"/>', b'<w:ilvl w:val="1"/>')
        self.rejected()

    def test_separated_group_must_reference_same_instance(self):
        self.mutate('word/document.xml', b'<w:numId w:val="2"/>', b'<w:numId w:val="1"/>', occurrence=3)
        self.rejected()

    def test_format_or_start_definition_cannot_be_faked(self):
        self.mutate('word/numbering.xml', b'<w:numFmt w:val="decimal"/>', b'<w:numFmt w:val="bullet"/>')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-numbered.docx', self.root / 'doc-000-numbered.docx')
        self.mutate('word/numbering.xml', b'<w:lvlText w:val="%1."/>', b'<w:lvlText w:val="%2."/>')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-numbered.docx', self.root / 'doc-000-numbered.docx')
        self.mutate('word/numbering.xml', b'<w:start w:val="1"/>', b'<w:start w:val="2"/>', occurrence=10)
        self.rejected()

    def test_literal_number_and_bold_text_cannot_change(self):
        self.mutate('word/document.xml', b'>2. literal<', b'>literal<')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'doc-000-numbered.docx', self.root / 'doc-000-numbered.docx')
        self.mutate('word/document.xml', b'<w:b/>', b'<w:b w:val="0"/>')
        self.rejected()

    def test_missing_corrupt_duplicate_and_extra(self):
        path = self.root / 'doc-000-numbered.docx'
        path.unlink(); self.rejected()
        path.write_bytes(b'not a ZIP'); self.rejected()
        shutil.copyfile(ARTIFACTS / path.name, path)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(path, 'a') as package:
                package.writestr('word/document.xml', package.read('word/document.xml'))
        self.rejected()
        shutil.copyfile(ARTIFACTS / path.name, path)
        (self.root / 'doc-001-numbered.docx').write_bytes(b'extra')
        self.rejected()


class NumberedWriterBuildHygieneTests(unittest.TestCase):
    def test_compiler_failure_removes_only_named_stale_outputs(self):
        import run_r60_writer as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r60writer/artifacts'
            out.mkdir(parents=True)
            stale = [out / 'doc-000-default.docx', out / 'doc-000-numbered.docx', out.parent / 'r60-writer.jar']
            for path in stale:
                path.write_bytes(b'stale')
            unrelated = out / 'doc-user-important.docx'
            unrelated.write_bytes(b'keep')
            env = dict(os.environ, KOTLINC=str(root / 'missing-compiler'), JAVA_HOME=str(root / 'missing-java'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError):
                    runner.main()
            self.assertTrue(all(not path.exists() for path in stale))
            self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__':
    unittest.main()
