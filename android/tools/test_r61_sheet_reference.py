import csv
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
from zipfile import BadZipFile

import openpyxl
import check_r61_sheet_reference as reference


class PolicyReferenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.csv').is_file():
            raise unittest.SkipTest('Run the actual R61 sheet contracts first')

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.csv').open(newline='', encoding='utf-8') as file:
            self.rows = list(csv.DictReader(file))

    def tearDown(self):
        self.tmp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, BadZipFile)):
            reference.check(self.folder)

    def save_manifest(self):
        with (self.folder / 'cases.csv').open('w', newline='', encoding='utf-8') as file:
            writer = csv.DictWriter(file, fieldnames=reference.FIELDS)
            writer.writeheader()
            writer.writerows(self.rows)

    def find(self, predicate):
        return next(row for row in self.rows if predicate(row))

    def edit_cell(self, row, address, value, kind=None):
        path = self.folder / ('result-' + row['id'] + '.xlsx')
        book = openpyxl.load_workbook(path)
        cell = book.worksheets[0][address]
        cell.value = value
        if kind is not None: cell.data_type = kind
        book.save(path)
        book.close()

    def test_actual_output(self):
        predicates, accepted, refused = reference.check(self.folder)
        self.assertEqual((accepted, refused), (259, 253))
        self.assertGreater(predicates, 7000)

    def test_quoted_numeric_cannot_silently_become_number(self):
        row = self.find(lambda r: r['status'] == 'accepted' and r['mode'] == 'DECIMALS' and
                        '"12.5"' in (self.folder / ('input-' + r['id'] + '.tsv')).read_text())
        path = self.folder / ('input-' + row['id'] + '.tsv')
        path.write_text(path.read_text().replace('"12.5"', '12.5', 1))
        self.rejected()

    def test_skipped_existing_cell_cannot_be_erased(self):
        row = self.find(lambda r: r['status'] == 'accepted' and r['skip'] == 'true' and
                        (self.folder / ('input-' + r['id'] + '.tsv')).read_text().split('\n')[0].split('\t')[0] in ('', '""'))
        self.edit_cell(row, 'A1', None)
        self.rejected()

    def test_existing_formula_cannot_be_retyped_as_text(self):
        def retained(row):
            if row['status'] != 'accepted': return False
            book = openpyxl.load_workbook(self.folder / ('result-' + row['id'] + '.xlsx'))
            try: return book.worksheets[0]['B2'].data_type == 'f'
            finally: book.close()
        row = self.find(retained)
        self.edit_cell(row, 'B2', '=1+2', 's')
        self.rejected()

    def test_conflict_cannot_be_promoted_to_success(self):
        row = self.find(lambda r: r['status'] == 'refused')
        row['status'] = 'accepted'
        self.save_manifest()
        self.rejected()

    def test_counts_and_transpose_are_independently_checked(self):
        row = self.find(lambda r: r['status'] == 'accepted' and r['skip'] == 'true')
        row['skipped'] = str(int(row['skipped']) + 1)
        self.save_manifest()
        self.rejected()

    def test_missing_corrupt_and_extra_artifacts_refused(self):
        row = self.find(lambda r: r['status'] == 'accepted')
        file = self.folder / ('result-' + row['id'] + '.xlsx')
        file.unlink(); self.rejected()
        file.write_bytes(b'not a zip'); self.rejected()
        shutil.copyfile(reference.ARTIFACTS / file.name, file)
        (self.folder / 'result-999.xlsx').write_bytes(b'extra')
        self.rejected()

    def test_symlink_is_not_an_independent_artifact(self):
        row = self.find(lambda r: r['status'] == 'accepted')
        file = self.folder / ('result-' + row['id'] + '.xlsx')
        file.unlink()
        file.symlink_to(reference.ARTIFACTS / file.name)
        self.rejected()


class PolicyBuildHygieneTests(unittest.TestCase):
    def test_compiler_failure_does_not_leave_named_stale_outputs(self):
        import run_r61_sheet as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r61sheet/artifacts'
            out.mkdir(parents=True)
            stale = [out / 'input-000.tsv', out / 'result-000.xlsx', out / 'cases.csv', out.parent / 'r61-sheet.jar']
            for path in stale: path.write_bytes(b'stale')
            unrelated = out / 'important.xlsx'
            unrelated.write_bytes(b'keep')
            env = dict(os.environ, KOTLINC=str(root / 'missing-compiler'), JAVA_HOME=str(root / 'missing-java'))
            with patch.object(runner, 'ROOT', root), patch.object(runner.bootstrap_host_tools, 'environment', return_value=env):
                with self.assertRaises(FileNotFoundError): runner.main()
            self.assertTrue(all(not p.exists() for p in stale))
            self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__':
    unittest.main()
