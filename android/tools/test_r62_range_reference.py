import csv
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import BadZipFile

import check_r62_range_reference as reference


class RangeReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.csv').is_file():
            raise unittest.SkipTest('Run actual R62 range contracts first')
        global openpyxl
        import openpyxl

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.csv').open(newline='', encoding='utf-8') as f:
            self.rows = list(csv.DictReader(f))

    def tearDown(self): self.tmp.cleanup()
    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, BadZipFile)):
            reference.check(self.folder)
    def find(self, predicate): return next(row for row in self.rows if predicate(row))
    def save_manifest(self):
        with (self.folder / 'cases.csv').open('w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=reference.FIELDS); writer.writeheader(); writer.writerows(self.rows)
    def workbook(self, row): return self.folder / ('result-' + row['id'] + '.xlsx')
    def modify(self, row, address, value, kind=None):
        path = self.workbook(row); book = openpyxl.load_workbook(path)
        cell = book.worksheets[0][address]; cell.value = value
        if kind is not None: cell.data_type = kind
        book.save(path); book.close()

    def test_actual_output(self):
        checks, accepted, refused = reference.check(self.folder)
        self.assertGreater(checks, 12000); self.assertEqual((accepted, refused), (369, 143))

    def test_boolean_cannot_be_silently_retyped_as_text(self):
        row = self.find(lambda r: r['status'] == 'accepted' and r['operation'] == 'FILL_LITERAL' and r['selected'] == 'C3')
        self.modify(row, 'C3', 'TRUE', 's'); self.rejected()

    def test_formula_looking_text_cannot_become_formula(self):
        row = self.find(lambda r: r['status'] == 'accepted' and r['operation'] == 'FILL_LITERAL' and r['selected'] == 'D4')
        self.modify(row, 'D4', '=1+1', 'f'); self.rejected()

    def test_conflict_cannot_be_promoted_to_accepted(self):
        row = self.find(lambda r: r['status'] == 'refused' and r['protect'] == 'true')
        row['status'] = 'accepted'; self.save_manifest(); self.rejected()

    def test_wrong_existing_formula_removed_count_rejected(self):
        row = self.find(lambda r: r['status'] == 'accepted' and int(r['formulas']) > 0)
        row['formulas'] = '0'; self.save_manifest(); self.rejected()

    def test_outside_cell_cannot_change(self):
        def untouched(row):
            if row['status'] != 'accepted': return False
            book = openpyxl.load_workbook(self.workbook(row))
            try: return book.worksheets[0]['E1'].value == 'outside'
            finally: book.close()
        row = self.find(untouched); self.modify(row, 'E1', 'altered', 's'); self.rejected()

    def test_missing_corrupt_extra_and_symlink_files_refused(self):
        row = self.find(lambda r: r['status'] == 'accepted'); file = self.workbook(row)
        file.unlink(); self.rejected()
        file.write_bytes(b'not a zip'); self.rejected()
        file.unlink(); file.symlink_to(reference.ARTIFACTS / file.name); self.rejected()
        file.unlink(); shutil.copyfile(reference.ARTIFACTS / file.name, file)
        (self.folder / 'result-999.xlsx').write_bytes(b'extra'); self.rejected()

    def test_invalid_rectangle_and_duplicate_ids_refused(self):
        old = self.rows[0]['range']
        self.rows[0]['range'] = 'A1:Z100'; self.save_manifest(); self.rejected()
        self.rows[0]['range'] = old
        self.rows[0]['id'] = self.rows[1]['id']; self.save_manifest(); self.rejected()


class RangeBuildHygiene(unittest.TestCase):
    def test_stdlib_discovery_without_artifacts_does_not_require_reader(self):
        with tempfile.TemporaryDirectory() as folder:
            script = '''
import sys, unittest
from pathlib import Path
import test_r62_range_reference as tests
import check_r62_range_reference as reference
reference.ARTIFACTS = Path(sys.argv[1]) / "missing"
result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(tests.RangeReferenceAttacks))
assert result.wasSuccessful() and len(result.skipped) == 1
try: reference.check(reference.ARTIFACTS)
except RuntimeError as e: assert "openpyxl" in str(e)
else: raise AssertionError("Reader must require openpyxl")
'''
            env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parent))
            result = subprocess.run([sys.executable, '-S', '-c', script, folder], env=env, capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_compile_failure_removes_only_named_stale_outputs(self):
        import run_r62_range as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); out = root / 'testing/build/r62sheet/artifacts'; out.mkdir(parents=True)
            stale = [out / 'result-000.xlsx', out / 'cases.csv', out.parent / 'r62-range.jar']
            for path in stale: path.write_bytes(b'stale')
            unrelated = out / 'important.xlsx'; unrelated.write_bytes(b'keep')
            env = dict(os.environ, KOTLINC=str(root / 'missing'), JAVA_HOME=str(root / 'missing'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError): runner.main()
            self.assertTrue(all(not p.exists() for p in stale)); self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__': unittest.main()
