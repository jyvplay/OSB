import base64
import csv
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from zipfile import BadZipFile

import check_r63_formula_reference as reference


class FormulaReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.tsv').is_file():
            raise unittest.SkipTest('Run actual R63 formula-fill contracts first')
        global openpyxl
        import openpyxl

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.tsv').open(newline='', encoding='utf-8') as stream:
            self.rows = list(csv.DictReader(stream, delimiter='\t'))

    def tearDown(self):
        self.tmp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, BadZipFile)):
            reference.check(self.folder)

    def save_manifest(self):
        with (self.folder / 'cases.tsv').open('w', newline='', encoding='utf-8') as stream:
            writer = csv.DictWriter(stream, fieldnames=reference.FIELDS, delimiter='\t')
            writer.writeheader()
            writer.writerows(self.rows)

    def modify(self, ident, address, value, kind=None):
        path = self.folder / ('result-' + ident + '.xlsx')
        book = openpyxl.load_workbook(path)
        cell = book.worksheets[0][address]
        cell.value = value
        if kind is not None:
            cell.data_type = kind
        book.save(path)
        book.close()

    def test_actual_outputs(self):
        count, accepted, refused = reference.check(self.folder)
        self.assertGreater(count, 25000)
        self.assertEqual((accepted, refused), (483, 29))

    def test_modal_shortcut_copying_formula_verbatim_is_detected(self):
        self.modify('000', 'N50', reference.FORMULAS[0])
        self.rejected()

    def test_shifted_absolute_column_is_detected(self):
        self.modify('000', 'N50', '=SUM(B1:C2)+$D$3+E$4+$E5+1E2')
        self.rejected()

    def test_shifted_absolute_row_is_detected(self):
        self.modify('000', 'N50', '=SUM(B1:C2)+$C$3+E$5+$E5+1E2')
        self.rejected()

    def test_untranslated_range_endpoint_is_detected(self):
        self.modify('000', 'N50', '=SUM(B1:B2)+$C$3+E$4+$E5+1E2')
        self.rejected()

    def test_modified_exponent_is_detected(self):
        self.modify('000', 'N50', '=SUM(B1:C2)+$C$3+E$4+$E5+1E3')
        self.rejected()

    def test_reference_like_quoted_text_is_not_translated(self):
        self.modify('064', 'N51', '=IF(B2>0,"B2 ""C3""",C3)')
        self.rejected()

    def test_formula_retyped_as_text_is_detected(self):
        self.modify('000', 'N50', '=SUM(B1:C2)+$C$3+E$4+$E5+1E2', 's')
        self.rejected()

    def test_outside_cell_change_is_detected(self):
        self.modify('000', 'Z100', 'not outside')
        self.rejected()

    def test_original_formula_change_is_detected(self):
        self.modify('000', 'M50', '=1')
        self.rejected()

    def test_extra_populated_cell_is_detected(self):
        self.modify('000', 'Z99', 'unexpected')
        self.rejected()

    def test_conflict_cannot_be_promoted_to_accepted(self):
        row = next(row for row in self.rows if row['status'] == 'refused')
        row['status'] = 'accepted'
        self.save_manifest()
        self.rejected()

    def test_counts_and_seeded_rectangles_cannot_lie(self):
        self.rows[0]['written'] = '0'
        self.save_manifest()
        self.rejected()
        self.rows[0]['written'] = '1'
        self.rows[0]['range'] = 'M50:M50'
        self.save_manifest()
        self.rejected()

    def test_formula_manifest_cannot_be_replaced(self):
        self.rows[0]['formula64'] = base64.b64encode(b'=1').decode()
        self.save_manifest()
        self.rejected()

    def test_missing_corrupt_extra_and_symlink_refused(self):
        file = self.folder / 'result-000.xlsx'
        file.unlink()
        self.rejected()
        file.write_bytes(b'not a workbook')
        self.rejected()
        file.unlink()
        file.symlink_to(reference.ARTIFACTS / file.name)
        self.rejected()
        file.unlink()
        shutil.copyfile(reference.ARTIFACTS / file.name, file)
        (self.folder / 'extra.xlsx').write_bytes(b'extra')
        self.rejected()


class FormulaBuildHygiene(unittest.TestCase):
    def test_missing_corpus_discovery_and_missing_reader(self):
        with tempfile.TemporaryDirectory() as folder:
            script = '''
import sys, unittest
from pathlib import Path
import test_r63_formula_reference as tests
import check_r63_formula_reference as reference
reference.ARTIFACTS = Path(sys.argv[1]) / "missing"
result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(tests.FormulaReferenceAttacks))
assert result.wasSuccessful() and len(result.skipped) == 1
try: reference.check(reference.ARTIFACTS)
except RuntimeError as e: assert "openpyxl" in str(e)
else: raise AssertionError("Reader must require openpyxl")
'''
            env = dict(os.environ, PYTHONPATH=str(Path(__file__).resolve().parent))
            result = subprocess.run([sys.executable, '-S', '-c', script, folder], env=env,
                                    capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_compiler_invocation_failure_cleans_only_owned_artifacts(self):
        import run_r63_formula_fill as runner
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            out = root / 'testing/build/r63sheet/artifacts'
            out.mkdir(parents=True)
            stale = [out / 'result-000.xlsx', out / 'cases.tsv', out.parent / 'r63-formula-fill.jar']
            for path in stale:
                path.write_bytes(b'stale')
            unrelated = out / 'important.xlsx'
            unrelated.write_bytes(b'keep')
            tools = root / 'tools'
            tools.mkdir()
            (tools / 'run_r63_formula_fill.py').write_bytes(Path(runner.__file__).read_bytes())
            (tools / 'bootstrap_host_tools.py').write_bytes(Path(runner.bootstrap_host_tools.__file__).read_bytes())
            # Exercise the real runner and compiler with absent production sources.
            result = subprocess.run([sys.executable, str(tools / 'run_r63_formula_fill.py')], capture_output=True, timeout=30)
            self.assertNotEqual(result.returncode, 0)
            self.assertTrue(all(not path.exists() for path in stale))
            self.assertEqual(unrelated.read_bytes(), b'keep')


if __name__ == '__main__':
    unittest.main()
