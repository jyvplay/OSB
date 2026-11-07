from pathlib import Path
import os
import shutil
import tempfile
import unittest
import zipfile

from check_r59_reference import ARTIFACTS, classify, validate


class TypedGridReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'typed.xlsx').is_file():
            raise unittest.SkipTest('Run actual R59 production contracts first')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for name in ('typed-input.tsv', 'typed.xlsx', 'dependent.xlsx', 'classifications.tsv'):
            shutil.copyfile(ARTIFACTS / name, self.root / name)

    def tearDown(self):
        self.temp.cleanup()

    def rejected(self):
        with self.assertRaises((AssertionError, KeyError, ValueError, FileNotFoundError, zipfile.BadZipFile)):
            validate(self.root)

    def xml_mutation(self, before, after, name='typed.xlsx'):
        path = self.root / name
        with zipfile.ZipFile(path) as package:
            entries = [(entry, package.read(entry)) for entry in package.infolist()]
        changed = False
        with zipfile.ZipFile(path, 'w') as output:
            for entry, data in entries:
                if entry.filename == 'xl/worksheets/sheet1.xml':
                    self.assertEqual(data.count(before), 1)
                    data = data.replace(before, after)
                    changed = True
                output.writestr(entry, data)
        self.assertTrue(changed)

    def test_actual_output(self):
        self.assertGreaterEqual(validate(self.root), 3060)

    def test_numeric_value_changed(self):
        self.xml_mutation(b'<c r="C1"><v>12.5</v></c>', b'<c r="C1"><v>12.6</v></c>')
        self.rejected()

    def test_formula_masquerading_as_text(self):
        self.xml_mutation(b'<c r="F1" t="inlineStr"><is><t xml:space="preserve">=1+1</t></is></c>',
                          b'<c r="F1"><f>1+1</f><v>2</v></c>')
        self.rejected()

    def test_identifier_loses_zero(self):
        self.xml_mutation(b'<t xml:space="preserve">0012</t>', b'<t xml:space="preserve">12</t>')
        self.rejected()

    def test_quoted_number_becomes_numeric(self):
        self.xml_mutation(b'<c r="D1" t="inlineStr"><is><t xml:space="preserve">42</t></is></c>',
                          b'<c r="D1"><v>42</v></c>')
        self.rejected()

    def test_cleared_cell_is_not_resurrected(self):
        self.xml_mutation(b'<c r="F3" t="inlineStr">', b'<c r="E3" t="inlineStr">')
        self.rejected()

    def test_dependent_formula_cache_tampering(self):
        self.xml_mutation(b'<f>A1+1</f><v>13.5</v>', b'<f>A1+1</f><v>12.5</v>', name='dependent.xlsx')
        self.rejected()

    def test_corpus_kind_mutation(self):
        path = self.root / 'classifications.tsv'
        lines = path.read_text().splitlines()
        index = next(i for i, line in enumerate(lines) if line.endswith('\tNUMBER'))
        lines[index] = lines[index].removesuffix('NUMBER') + 'TEXT'
        path.write_text('\n'.join(lines) + '\n')
        self.rejected()

    def test_missing_corrupt_and_source_mutation(self):
        path = self.root / 'typed.xlsx'
        path.unlink(); self.rejected()
        path.write_bytes(b'not a zip'); self.rejected()
        shutil.copyfile(ARTIFACTS / 'typed.xlsx', path)
        (self.root / 'typed-input.tsv').write_text('not the input')
        self.rejected()

    def test_shortcut_does_not_convert_dangerous_or_formatted_tokens(self):
        for value in ('0012', '1.0', '-0', '+1', '1e3', '=2+2', 'NaN', '9' * 16):
            self.assertEqual(classify(value, False), 'TEXT')
        self.assertEqual(classify('0.1', False), 'NUMBER')
        self.assertEqual(classify('0.1', True), 'TEXT')


class BuildHygieneTests(unittest.TestCase):
    def test_failed_compiler_cannot_leave_stale_artifacts(self):
        from unittest.mock import patch
        import run_r59_contracts as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r59'
            (out / 'artifacts').mkdir(parents=True)
            files = [out / 'r59-contracts.jar', *(out / 'artifacts' / name for name in
                     ('typed-input.tsv', 'typed.xlsx', 'dependent.xlsx', 'classifications.tsv'))]
            for path in files:
                path.write_bytes(b'stale')
            env = dict(os.environ, KOTLINC=str(root / 'missing-compiler'), JAVA_HOME=str(root / 'missing-java'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError):
                    runner.main()
            self.assertTrue(all(not path.exists() for path in files))


if __name__ == '__main__':
    unittest.main()
