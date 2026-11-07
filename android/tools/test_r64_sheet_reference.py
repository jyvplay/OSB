import csv
from pathlib import Path
import shutil
import tempfile
import unittest
from zipfile import BadZipFile
import xml.etree.ElementTree as ET

import check_r64_sheet_copy_reference as reference


class SheetCopyReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.tsv').is_file():
            raise unittest.SkipTest('Run real R64 sheet copy corpus first')
        import openpyxl  # noqa: F401

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.tsv').open(encoding='utf-8', newline='') as f:
            self.rows = list(csv.DictReader(f, delimiter='\t'))

    def tearDown(self): self.tmp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, BadZipFile, ET.ParseError)):
            reference.check(self.folder)

    def save_manifest(self):
        with (self.folder / 'cases.tsv').open('w', encoding='utf-8', newline='') as f:
            w = csv.DictWriter(f, fieldnames=reference.FIELDS, delimiter='\t')
            w.writeheader(); w.writerows(self.rows)

    def edit_cell(self, name, address, value):
        from openpyxl import load_workbook
        path = self.folder / name
        book = load_workbook(path)
        book.active[address] = value
        book.save(path)
        book.close()

    def test_actual(self): self.assertGreater(reference.check(self.folder), 2900)
    def test_wrong_relocated_formula(self):
        self.edit_cell('result-008.xlsx', 'N50', '=A1+$B$2+C$3+$D4'); self.rejected()
    def test_lost_absolute_anchor(self):
        self.edit_cell('result-008.xlsx', 'N50', '=B1+B$2+D$3+$D4'); self.rejected()
    def test_wrong_value_type(self):
        self.edit_cell('result-008.xlsx', 'N50', 'not a formula'); self.rejected()
    def test_unexpected_extra_cell(self):
        self.edit_cell('result-000.xlsx', 'Y99', 'extra'); self.rejected()
    def test_untouched_source_changed(self):
        self.edit_cell('result-008.xlsx', 'A1', 1000); self.rejected()
    def test_changed_preview_counts(self):
        self.rows[8]['inserted'] = '0'; self.save_manifest(); self.rejected()
    def test_policy_is_not_manifest_controlled(self):
        self.rows[8]['protect'] = 'true'; self.save_manifest(); self.rejected()
    def test_refusal_cannot_turn_into_success(self):
        self.rows[12]['status'] = 'accepted'; self.save_manifest(); self.rejected()
    def test_missing_workbook(self):
        (self.folder / 'result-000.xlsx').unlink(); self.rejected()
    def test_extra_workbook_for_refusal(self):
        shutil.copyfile(self.folder / 'result-000.xlsx', self.folder / 'result-012.xlsx'); self.rejected()
    def test_extra_artifact(self):
        (self.folder / 'stray.xlsx').write_bytes(b'x'); self.rejected()
    def test_symlinked_workbook(self):
        path = self.folder / 'result-000.xlsx'; path.unlink(); path.symlink_to(self.folder / 'result-001.xlsx'); self.rejected()
    def test_reordered_cases(self):
        self.rows[0], self.rows[1] = self.rows[1], self.rows[0]; self.save_manifest(); self.rejected()


if __name__ == '__main__': unittest.main()
