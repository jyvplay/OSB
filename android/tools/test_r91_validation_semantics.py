#!/usr/bin/env python3
"""Controls for the R91 decision oracle (`check_r91_validation_semantics.py`).

Two groups: unit tests of the oracle's own arithmetic (no artifacts needed), and tamper tests that prove the
oracle refuses a corpus whose verdicts disagree with it.

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r91_validation_semantics -v
"""
from __future__ import annotations

import shutil
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path

import check_r91_validation_semantics as oracle

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r91/artifacts'


class OracleArithmetic(unittest.TestCase):
    def test_utf16_length_counts_astral_characters_twice(self):
        self.assertEqual(oracle.utf16_length('abc'), 3)
        self.assertEqual(oracle.utf16_length('\u00e9'), 1)
        self.assertEqual(oracle.utf16_length('\U0001D11E'), 2)

    def test_spreadsheet_spelling_drops_the_fraction_for_integers(self):
        self.assertEqual(oracle.spreadsheet_spelling(Decimal('3.0')), '3')
        self.assertEqual(oracle.spreadsheet_spelling(Decimal('-0.0')), '0')
        self.assertEqual(oracle.spreadsheet_spelling(Decimal('3.5')), '3.5')

    def test_literal_refuses_function_calls_and_references(self):
        self.assertIsNone(oracle.literal('DATE(2020,1,1)'))
        self.assertIsNone(oracle.literal('$A$1'))
        self.assertIsNone(oracle.literal('Sheet1!A1'))
        self.assertEqual(oracle.literal(' 43831 '), Decimal('43831'))

    def test_list_items_requires_a_quoted_literal(self):
        self.assertEqual(oracle.list_items('"red, green"'), ['red', 'green'])
        self.assertIsNone(oracle.list_items('Config!A1:A3'))
        self.assertIsNone(oracle.list_items('"a"b"'))

    def test_whole_numbers_refuse_fractions_before_comparing(self):
        self.assertEqual(oracle.accepts('whole', 'between', '1', '100', 'num', '3.5'), 'false')
        self.assertEqual(oracle.accepts('whole', 'between', '1', '100', 'num', '50'), 'true')

    def test_list_matching_is_case_insensitive(self):
        self.assertEqual(oracle.accepts('list', '-', '"red,green"', None, 'txt', 'GREEN'), 'true')
        self.assertEqual(oracle.accepts('list', '-', '"red,green"', None, 'txt', 'blue'), 'false')

    def test_numeric_rules_refuse_text_cells(self):
        self.assertEqual(oracle.accepts('decimal', 'greaterThan', '0', None, 'txt', '5'), 'undecided')

    def test_custom_rules_are_never_decided(self):
        self.assertEqual(oracle.accepts('custom', '-', 'MOD(A1,2)=0', None, 'num', '2'), 'undecided')

    def test_between_and_not_between_are_complements(self):
        inside = oracle.accepts('decimal', 'between', '1', '10', 'num', '5')
        outside = oracle.accepts('decimal', 'notBetween', '1', '10', 'num', '5')
        self.assertEqual((inside, outside), ('true', 'false'))


class CorpusTampering(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'validation_verdicts.tsv').is_file():
            raise unittest.SkipTest('R91 artifacts are absent; run tools/run_r91_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r91-oracle-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        self.folder.mkdir()
        self.rows = (ARTIFACTS / 'validation_verdicts.tsv').read_text(encoding='utf-8').splitlines()
        self.write(self.rows)

    def write(self, rows):
        (self.folder / 'validation_verdicts.tsv').write_text('\n'.join(rows) + '\n', encoding='utf-8')

    def test_undisturbed_corpus_passes(self):
        totals = oracle.verify(self.folder)
        self.assertEqual(totals['verdicts'], 228)
        self.assertEqual(totals['rules'], 6)
        self.assertGreater(totals['decided'], 0)
        self.assertGreater(totals['undecided'], 0)

    def test_flipped_verdict_is_rejected(self):
        fields = self.rows[0].split('\t')
        fields[-1] = 'false' if fields[-1] != 'false' else 'true'
        self.rows[0] = '\t'.join(fields)
        self.write(self.rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_undecidable_reported_as_true_is_rejected(self):
        for index, row in enumerate(self.rows):
            fields = row.split('\t')
            if fields[1] == 'custom':
                fields[-1] = 'true'
                self.rows[index] = '\t'.join(fields)
                break
        self.write(self.rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_empty_corpus_is_rejected(self):
        self.write([])
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_corpus_without_a_refusal_row_is_rejected(self):
        kept = [row for row in self.rows if not row.endswith('\tundecided')]
        self.write(kept)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_wrong_member_count_is_rejected(self):
        self.rows.append('R1\twhole')
        self.write(self.rows)
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)

    def test_missing_corpus_is_rejected(self):
        (self.folder / 'validation_verdicts.tsv').unlink()
        with self.assertRaises(oracle.Fail):
            oracle.verify(self.folder)


if __name__ == '__main__':
    unittest.main()
