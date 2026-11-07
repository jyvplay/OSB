import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

from check_r60_sheet_reference import ARTIFACTS, validate


class TransposedSheetReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'transposed-000.xlsx').is_file():
            raise unittest.SkipTest('Run actual R60 sheet contracts first')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for name in ('input-000.tsv', 'transposed-000.xlsx'):
            shutil.copyfile(ARTIFACTS / name, self.root / name)

    def tearDown(self):
        self.temp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, zipfile.BadZipFile)):
            validate(self.root, count=1)

    def mutate(self, before, after):
        path = self.root / 'transposed-000.xlsx'
        with zipfile.ZipFile(path) as package:
            entries = [(entry, package.read(entry)) for entry in package.infolist()]
        with zipfile.ZipFile(path, 'w') as package:
            for entry, data in entries:
                if entry.filename == 'xl/worksheets/sheet1.xml':
                    self.assertEqual(data.count(before), 1)
                    data = data.replace(before, after)
                package.writestr(entry, data)

    def test_actual_output(self):
        self.assertEqual(validate(self.root, count=1), 18)

    def test_transpose_coordinates_cannot_be_omitted(self):
        self.mutate(b'r="C2"', b'r="C1"')
        self.rejected()

    def test_numeric_value_and_quoted_type_are_checked(self):
        self.mutate(b'<v>12.5</v>', b'<v>12.6</v>')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'transposed-000.xlsx', self.root / 'transposed-000.xlsx')
        self.mutate(b'<c r="B3" t="inlineStr"><is><t xml:space="preserve">42</t></is></c>',
                    b'<c r="B3"><v>42</v></c>')
        self.rejected()

    def test_formula_smuggling_and_blank_resurrection(self):
        self.mutate(b'<c r="B4" t="inlineStr"><is><t xml:space="preserve">=1+1</t></is></c>',
                    b'<c r="B4"><f>1+1</f><v>2</v></c>')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'transposed-000.xlsx', self.root / 'transposed-000.xlsx')
        self.mutate(b'</sheetData>', b'<row r="5"><c r="C4"><v>9</v></c></row></sheetData>')
        self.rejected()

    def test_input_and_artifact_hygiene(self):
        (self.root / 'input-000.tsv').write_text('fake input')
        self.rejected()
        shutil.copyfile(ARTIFACTS / 'input-000.tsv', self.root / 'input-000.tsv')
        output = self.root / 'transposed-000.xlsx'
        output.unlink(); self.rejected()
        output.write_bytes(b'bad zip'); self.rejected()
        shutil.copyfile(ARTIFACTS / output.name, output)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(output, 'a') as package:
                package.writestr('xl/worksheets/sheet1.xml', package.read('xl/worksheets/sheet1.xml'))
        self.rejected()
        shutil.copyfile(ARTIFACTS / output.name, output)
        (self.root / 'transposed-001.xlsx').write_bytes(b'extra')
        self.rejected()


class TransposeBuildHygieneTests(unittest.TestCase):
    def test_compiler_failure_removes_named_stale_outputs_only(self):
        import run_r60_sheet as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r60sheet/artifacts'
            out.mkdir(parents=True)
            stale = [out / 'input-000.tsv', out / 'transposed-000.xlsx', out.parent / 'r60-sheet.jar']
            for path in stale:
                path.write_bytes(b'stale')
            unrelated = out / 'important.xlsx'
            unrelated.write_bytes(b'keep')
            env = dict(os.environ, KOTLINC=str(root / 'missing-compiler'), JAVA_HOME=str(root / 'missing-java'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError):
                    runner.main()
            self.assertTrue(all(not p.exists() for p in stale))
            self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__':
    unittest.main()
