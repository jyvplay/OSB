#!/usr/bin/env python3
"""Attack the actual independent R58 reader; no Android execution is implied."""
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile

import check_r58_reference as reference


class TextGridReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        try:
            import openpyxl  # noqa: F401 - host-only external reader
        except ImportError:
            raise unittest.SkipTest('Install openpyxl for this host-only reference gate')
        cls.original = reference.ARTIFACTS
        if not (cls.original / 'pasted.xlsx').is_file():
            raise unittest.SkipTest('Run tools/run_r58_contracts.py before comparator attacks')

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        for name in ('pasted.xlsx', 'input.tsv'):
            shutil.copyfile(self.original / name, self.directory / name)

    def tearDown(self):
        self.temp.cleanup()

    def mutate(self, before: bytes, after: bytes):
        path = self.directory / 'pasted.xlsx'
        with zipfile.ZipFile(path) as source:
            parts = {item.filename: source.read(item) for item in source.infolist()}
        name = 'xl/worksheets/sheet1.xml'
        self.assertIn(before, parts[name])
        parts[name] = parts[name].replace(before, after, 1)
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_STORED) as result:
            for part, data in parts.items():
                result.writestr(part, data)

    def test_actual_output(self):
        self.assertEqual(reference.validate(self.directory), 31)

    def test_missing_input(self):
        (self.directory / 'input.tsv').unlink()
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_changed_input(self):
        (self.directory / 'input.tsv').write_text('changed', encoding='utf-8')
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_formula_masquerading_as_text(self):
        self.mutate(b'<c r="B1" t="inlineStr"><is><t xml:space="preserve">=1+1</t></is></c>',
                    b'<c r="B1"><f>1+1</f><v>2</v></c>')
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_text_mutation(self):
        self.mutate(b'  hello  ', b'hello')
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_carriage_return_removed(self):
        self.mutate(b'line 1&#13;\nline 2', b'line 1\nline 2')
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_cleared_cell_is_not_resurrected(self):
        self.mutate(b'<row r="2">', b'<row r="2"><c r="B2" t="inlineStr"><is><t>old</t></is></c>')
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_unrelated_formula_is_preserved(self):
        self.mutate(b'<f>3+4</f>', b'<f>3+5</f>')
        with self.assertRaises(Exception): reference.validate(self.directory)

    def test_corrupt_zip(self):
        (self.directory / 'pasted.xlsx').write_bytes(b'not a ZIP')
        with self.assertRaises(Exception): reference.validate(self.directory)


if __name__ == '__main__':
    unittest.main()
