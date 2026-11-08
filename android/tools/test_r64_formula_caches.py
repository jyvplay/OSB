"""Adversaries for the independent cached-value comparator, not Excel execution."""
from pathlib import Path
import shutil
import tempfile
import unittest
from zipfile import ZipFile

import check_r64_formula_caches as checker
import check_r64_sheet_copy_reference as sheet


class CopiedFormulaCacheAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (sheet.ARTIFACTS / 'cases.tsv').is_file():
            raise unittest.SkipTest('Run real R64 copy corpus first')
        import openpyxl  # noqa: F401
        import formualizer  # noqa: F401

    def test_typed_comparison_rejects_boolean_number_confusion(self):
        self.assertFalse(checker.equal_typed(True, 1))
        self.assertFalse(checker.equal_typed('1', 1))
        self.assertFalse(checker.equal_typed(1, 1.01))
        self.assertTrue(checker.equal_typed(1, 1.0))
        self.assertTrue(checker.equal_typed('#DIV/0!', {'type': 'Error', 'kind': 'Div'}))
        self.assertFalse(checker.equal_typed('#VALUE!', {'type': 'Error', 'kind': 'Div'}))

    def test_actual_differential(self):
        self.assertEqual(checker.check(), (486, 4))

    def test_right_formula_with_wrong_cache_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / 'corpus'; shutil.copytree(sheet.ARTIFACTS, folder)
            path = folder / 'result-008.xlsx'
            with ZipFile(path) as z: entries = [(entry, z.read(entry)) for entry in z.infolist()]
            old = b'<c r="N50"><f>B1+$B$2+D$3+$D4</f><v>6</v></c>'
            new = b'<c r="N50"><f>B1+$B$2+D$3+$D4</f><v>7</v></c>'
            with ZipFile(path, 'w') as z:
                for entry, data in entries:
                    if entry.filename == 'xl/worksheets/sheet1.xml':
                        self.assertIn(old, data); data = data.replace(old, new, 1)
                    z.writestr(entry, data)
            # Formula structure and relocation alone deliberately do not catch the forged cache.
            self.assertGreater(sheet.check(folder), 2900)
            with self.assertRaisesRegex(ValueError, 'Cached result differs'): checker.check(folder)

    def test_wrong_manual_value_cannot_be_disguised_as_reference_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / 'corpus'; shutil.copytree(sheet.ARTIFACTS, folder)
            path = folder / 'result-224.xlsx'
            with ZipFile(path) as z: entries = [(entry, z.read(entry)) for entry in z.infolist()]
            with ZipFile(path, 'w') as z:
                for entry, data in entries:
                    if entry.filename == 'xl/worksheets/sheet1.xml':
                        old = b'<c r="O52"><f>COUNT($A3:D$2)+$C$3</f><v>5</v></c>'
                        self.assertIn(old, data); data = data.replace(old, old.replace(b'<v>5</v>', b'<v>9</v>'), 1)
                    z.writestr(entry, data)
            with self.assertRaisesRegex(ValueError, 'Unexpected reference refusal'): checker.check(folder)


if __name__ == '__main__': unittest.main()
