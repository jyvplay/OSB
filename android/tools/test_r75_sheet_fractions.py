#!/usr/bin/env python3
"""Negative controls for the R75 fraction oracle.

The oracle's value is entirely in *rejecting* wrong observations, so these tests doctor a real
observation file and require a non-zero exit for each specific mutation. A green suite here means
the oracle is not vacuous; it says nothing about Excel.
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

import check_r75_sheet_fractions as oracle

ARTIFACTS = Path(__file__).resolve().parents[1] / 'testing/build/r75fractions/artifacts'


class FractionOracleChecks(unittest.TestCase):
    def setUp(self):
        if not (ARTIFACTS / 'fractions.tsv').is_file():
            raise unittest.SkipTest('run tools/run_r75_sheet_fractions.py first')
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        shutil.copyfile(ARTIFACTS / 'fractions.tsv', self.directory / 'fractions.tsv')
        self.rows = [line.split('\t') for line in
                     (self.directory / 'fractions.tsv').read_text(encoding='utf-8').splitlines() if line]

    def tearDown(self):
        self.temp.cleanup()

    def write(self, rows):
        (self.directory / 'fractions.tsv').write_text(
            '\n'.join('\t'.join(row) for row in rows) + '\n', encoding='utf-8')

    def test_untampered_corpus_passes(self):
        self.assertEqual(oracle.verify(self.directory), len(self.rows))

    def test_wrong_fraction_is_rejected(self):
        rows = [row[:] for row in self.rows]
        index = next(i for i, row in enumerate(rows) if row[2] == '1 1/4')
        rows[index][2] = '1 1/3'
        self.write(rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.directory)

    def test_padding_removal_is_rejected(self):
        rows = [row[:] for row in self.rows]
        index = next(i for i, row in enumerate(rows) if row[2].startswith('1  1/'))
        rows[index][2] = rows[index][2].replace('1  1/', '1 1/')
        self.write(rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.directory)

    def test_fixed_denominator_reduction_is_rejected(self):
        rows = [row[:] for row in self.rows]
        index = next(i for i, row in enumerate(rows) if row[1] == '?/8' and row[2].endswith('/8'))
        rows[index][2] = rows[index][2][:-2] + '/1'
        self.write(rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.directory)

    def test_sign_flip_is_rejected(self):
        rows = [row[:] for row in self.rows]
        rows[0][2] = '-' + rows[0][2]
        self.write(rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.directory)

    def test_truncated_corpus_is_rejected(self):
        self.write(self.rows[:10])
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.directory)

    def test_oracle_disagrees_with_bankers_rounding(self):
        # 7.0625 * 8 = 56.5 exactly; half-up is 57, Python's round() would say 56.
        self.assertEqual(oracle.half_up(56.5), 57)
        self.assertEqual(oracle.half_up(-56.5), -57)
        pattern = oracle.parse_pattern('?/8')
        self.assertEqual(oracle.choose(7.0625, pattern)[:2], (57, 8))

    def test_missing_file_is_rejected(self):
        (self.directory / 'fractions.tsv').unlink()
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.directory)


if __name__ == '__main__':
    unittest.main()
