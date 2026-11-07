import csv
from pathlib import Path
import shutil
import tempfile
import unittest

import check_r64_csv_reference as reference


class CsvReferenceAttacks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (reference.ARTIFACTS / 'cases.csv').is_file():
            raise unittest.SkipTest('Run real R64 CSV corpus first')

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name) / 'corpus'
        shutil.copytree(reference.ARTIFACTS, self.folder)
        with (self.folder / 'cases.csv').open(encoding='utf-8', newline='') as f:
            self.rows = list(csv.DictReader(f))

    def tearDown(self): self.tmp.cleanup()

    def rejected(self):
        with self.assertRaises((ValueError, KeyError, OSError, UnicodeError, csv.Error)):
            reference.check(self.folder)

    def replace(self, name, old, new):
        path = self.folder / name; raw = path.read_bytes()
        self.assertIn(old, raw); path.write_bytes(raw.replace(old, new, 1))

    def save_manifest(self):
        with (self.folder / 'cases.csv').open('w', encoding='utf-8', newline='') as f:
            w = csv.DictWriter(f, fieldnames=reference.FIELDS); w.writeheader(); w.writerows(self.rows)

    def test_actual(self): self.assertGreater(reference.check(self.folder), 700)
    def test_changed_formula_result(self):
        self.replace('sheet-000.csv', b'"25"', b'"26"'); self.rejected()
    def test_formula_source_not_values(self):
        self.replace('sheet-000.csv', b'"25"', b'"=A1*2"'); self.rejected()
    def test_malformed_quotes(self):
        self.replace('sheet-000.csv', b'"hello"', b'"hello'); self.rejected()
    def test_escaping_breakout(self):
        self.replace('sheet-000.csv', b'"hello"', b'"hello", "extra"'); self.rejected()
    def test_valid_but_noncanonical_unquoted(self):
        self.replace('sheet-000.csv', b'"hello"', b'hello'); self.rejected()
    def test_line_ending(self):
        self.replace('sheet-000.csv', b'\r\n', b'\n'); self.rejected()
    def test_bom(self):
        path = self.folder / 'sheet-000.csv'; path.write_bytes(b'\xef\xbb\xbf' + path.read_bytes()); self.rejected()
    def test_extra_cell(self):
        self.replace('sheet-000.csv', b'\r\n', b',"extra"\r\n'); self.rejected()
    def test_dangerous_refusal_cannot_be_forged(self):
        self.rows[16]['status'] = 'accepted'; self.save_manifest(); self.rejected()
    def test_refusal_cannot_emit_file(self):
        shutil.copyfile(self.folder / 'sheet-000.csv', self.folder / 'sheet-016.csv'); self.rejected()
    def test_missing_file(self):
        (self.folder / 'sheet-000.csv').unlink(); self.rejected()
    def test_extra_file(self):
        (self.folder / 'extra.csv').write_bytes(b'x'); self.rejected()
    def test_symlinked_file(self):
        path = self.folder / 'sheet-000.csv'; path.unlink(); path.symlink_to(self.folder / 'sheet-001.csv'); self.rejected()
    def test_reordered_cases(self):
        self.rows[0], self.rows[1] = self.rows[1], self.rows[0]; self.save_manifest(); self.rejected()


if __name__ == '__main__': unittest.main()
