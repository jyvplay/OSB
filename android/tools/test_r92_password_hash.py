#!/usr/bin/env python3
"""Controls for the R92 password-hash oracle and for the parser bound this feature depends on.

Three groups, all artifact-light:

* a **third implementation** of Excel's legacy hash, written here from the algorithm's description, compared
  against every row the JVM probe wrote — so the corpus is checked by two independent implementations
  (openpyxl's and this one) rather than one;
* tamper tests proving the oracle refuses a corpus whose hash disagrees, is mis-spelled or is too small;
* an admissibility check on `BoundedXml`'s attribute ceiling: the `<sheetProtection>` this feature writes
  carries seventeen attributes, which the parser's previous ceiling of sixteen refused.

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r92_password_hash -v
"""
from __future__ import annotations

import re
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r92_password_hash as oracle

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r92/artifacts'
BOUNDED_XML = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt'


def hash_from_description(password: str) -> str:
    """The published rotate-and-xor, written independently of both the Kotlin and the openpyxl versions."""
    value = 0
    for index, char in enumerate(password):
        shifted = ord(char) << (index + 1)
        value ^= (shifted & 0x7FFF) | (shifted >> 15)
    value ^= len(password)
    value ^= 0xCE4B
    return format(value & 0xFFFF, '04X')


class HashArithmetic(unittest.TestCase):
    def test_known_constants(self):
        self.assertEqual(hash_from_description(''), 'CE4B')      # no characters: the constant alone
        self.assertEqual(hash_from_description('secret'), 'DAA7')  # matches both other implementations
        self.assertEqual(hash_from_description('a'), 'CE88')

    def test_hash_is_sixteen_bits(self):
        for password in ('x' * 15, '\uffff' * 15, 'A1!~'):
            self.assertRegex(hash_from_description(password), r'^[0-9A-F]{4}$')

    def test_order_matters(self):
        self.assertNotEqual(hash_from_description('ab'), hash_from_description('ba'))

    def test_corpus_matches_a_third_implementation(self):
        path = ARTIFACTS / 'password_hashes.tsv'
        if not path.is_file():
            self.skipTest('R92 artifacts are absent; run tools/run_r92_checks.py')
        for line in path.read_text(encoding='utf-8').splitlines():
            if not line.strip():
                continue
            password, written = line.split('\t')
            password = password.replace('\\t', '\t').replace('\\n', '\n')
            self.assertEqual(written, hash_from_description(password), password)


class CorpusTampering(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'password_hashes.tsv').is_file():
            self.skipTest('R92 artifacts are absent; run tools/run_r92_checks.py')
        self.folder = Path(tempfile.mkdtemp(prefix='r92-hash-'))
        self.rows = (ARTIFACTS / 'password_hashes.tsv').read_text(encoding='utf-8').splitlines()
        self.write(self.rows)

    def tearDown(self):
        shutil.rmtree(self.folder, ignore_errors=True)

    def write(self, rows):
        (self.folder / 'password_hashes.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')

    def test_undisturbed_corpus_passes(self):
        self.assertEqual(oracle.verify(self.folder)['passwords'], 311)

    def test_changed_hash_is_rejected(self):
        fields = self.rows[5].split('\t')
        fields[1] = '0000' if fields[1] != '0000' else '1111'
        self.rows[5] = '\t'.join(fields)
        self.write(self.rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_three_digit_spelling_is_rejected(self):
        fields = self.rows[3].split('\t')
        fields[1] = fields[1].lstrip('0') or '0'
        if len(fields[1]) == 4:
            fields[1] = '0' + fields[1][1:]      # still four digits, so the value must now disagree
        self.rows[3] = '\t'.join(fields)
        self.write(self.rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_lower_case_hash_is_rejected(self):
        fields = self.rows[2].split('\t')
        fields[1] = fields[1].lower()
        self.rows[2] = '\t'.join(fields)
        self.write(self.rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_small_corpus_is_rejected(self):
        self.write(self.rows[:10])
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_wrong_member_count_is_rejected(self):
        self.write(self.rows + ['lonely'])
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_missing_corpus_is_rejected(self):
        (self.folder / 'password_hashes.tsv').unlink()
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)


class ParserAdmissibility(unittest.TestCase):
    def test_the_written_record_needs_seventeen_attributes(self):
        path = ARTIFACTS / 'prot.xlsx'
        if not path.is_file():
            self.skipTest('R92 artifacts are absent')
        with zipfile.ZipFile(path) as archive:
            raw = archive.read('xl/worksheets/sheet1.xml').decode('utf-8')
        element = raw[raw.index('<sheetProtection'):raw.index('/>', raw.index('<sheetProtection'))]
        self.assertEqual(len(re.findall(r'="', element)), 17)

    def test_the_parser_ceiling_admits_it_and_remains_bounded(self):
        source = BOUNDED_XML.read_text()
        ceiling = int(re.search(r'attrs\.length<=(\d+)', source).group(1))
        self.assertGreaterEqual(ceiling, 21)      # CT_SheetProtection's schema maximum, strong hash included
        self.assertLessEqual(ceiling, 64)         # still a bound, not an absence of one


if __name__ == '__main__':
    unittest.main()
