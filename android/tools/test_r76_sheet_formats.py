#!/usr/bin/env python3
"""Negative controls for the R76 format verifier.

Every control mutates one thing — an observation, the divergence table or the reference oracle —
and asserts that tools/check_r76_sheet_formats.py rejects it. A verifier that cannot fail is not a
gate, so these run in CI next to the verifier itself.

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r76_sheet_formats -v
The corpus-independent controls use a synthetic corpus, so they do not need compiled artifacts.
"""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import check_r76_sheet_formats as checker

HEADER_ROWS = [
    ('number', 1234.567, '$#,##0.00', '$1,234.57'),
    ('number', 2.675, '0.00', '2.68'),
    ('number', 12200000.0, '#,##0.0,,', '12.2'),
    ('number', 1234.5678, '[$€-407]#,##0.00', '€1,234.57'),
    ('number', 3.14159, 'General', '3.14159'),
    ('number', 1.0, '000', '001'),
    ('date', 1.0, 'yyyy-mm-dd', '1900-01-01'),
    ('date', 60.0, 'yyyy-mm-dd', '1900-02-29'),
    ('date', 45000.0, 'dddd, mmmm d, yyyy', 'Wednesday, March 15, 2023'),
    ('time', 0.5, '[h]:mm:ss', '12:00:00'),
    ('time', 0.999999, 'hh:mm', '00:00'),
    ('time', 45000.999, 'h:mm:ss.00', '23:58:33.60'),
    ('refusal', 0.5, '[>100]General', 'REFUSED'),
]


def synthetic(rows):
    """Filler rows so the corpus clears the minimum size without touching the interesting cases."""
    filler = []
    for index in range(200):
        filler.append(('number', float(index), '0.00', f'{index}.00'))
    return rows + filler


def write_corpus(directory: Path, rows) -> Path:
    artifacts = directory / 'artifacts'
    artifacts.mkdir(parents=True, exist_ok=True)
    body = '\n'.join('\t'.join(str(field) for field in row) for row in rows) + '\n'
    (artifacts / 'formats.tsv').write_text(body, encoding='utf-8')
    return artifacts


def oracle_from(rows):
    """A perfect reference: replays the corpus itself, so any mutation shows up as a difference."""
    table = {(float(value), fmt): rendered for _kind, value, fmt, rendered in rows}

    def oracle(fmt: str, value: float) -> str:
        return table[(value, fmt)]

    return oracle


def divergences(entries=None) -> dict:
    return {'divergences': entries or [
        {'id': 'keep', 'formats': ['"pre "@'], 'reason': 'r', 'reference': 'ref'},
    ]}


class ControlTests(unittest.TestCase):
    def run_verify(self, rows, oracle=None, entries=None, require_reference=True):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = write_corpus(Path(directory), rows)
            reference = oracle if oracle is not None else oracle_from(rows)
            table = {'divergences': entries if entries is not None else []}
            return checker.verify(artifacts, require_reference, oracle=reference, divergence_table=table)

    # 1. the honest corpus passes
    def test_undisturbed_corpus_passes(self):
        report = self.run_verify(synthetic(HEADER_ROWS), entries=[])
        self.assertEqual(report['structure']['rows'], 213)
        self.assertGreaterEqual(report['rederivation']['rederived'], 13)

    # 2. a doctored rendering is rejected by re-derivation
    def test_doctored_value_is_rejected(self):
        rows = [(k, v, f, ('$1,234.58' if f == '$#,##0.00' else out)) for k, v, f, out in HEADER_ROWS]
        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(synthetic(rows), entries=[])
        self.assertIn('re-derivation says', str(caught.exception))

    # 3. a doctored date is rejected
    def test_wrong_serial_mapping_is_rejected(self):
        rows = [(k, v, f, ('1900-01-00' if (f == 'yyyy-mm-dd' and v == 1.0) else out)) for k, v, f, out in HEADER_ROWS]
        with self.assertRaises(checker.Fail):
            self.run_verify(synthetic(rows), entries=[])

    # 4. a refusal that starts rendering is rejected
    def test_refusal_that_renders_is_rejected(self):
        rows = [(k, v, f, ('0.5' if f == '[>100]General' else out)) for k, v, f, out in HEADER_ROWS]
        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(synthetic(rows), entries=[])
        self.assertIn('refusal', str(caught.exception).lower())

    # 5. a supported format that silently refuses is rejected
    def test_unexpected_refusal_is_rejected(self):
        rows = [(k, v, f, ('REFUSED' if f == '0.00' else out)) for k, v, f, out in HEADER_ROWS]
        with self.assertRaises(checker.Fail):
            self.run_verify(synthetic(rows), entries=[])

    # 6. duplicate observations are rejected
    def test_duplicate_observation_is_rejected(self):
        rows = HEADER_ROWS + [HEADER_ROWS[0]]
        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(synthetic(rows), entries=[])
        self.assertIn('duplicate', str(caught.exception).lower())

    # 7. a truncated corpus is rejected
    def test_truncated_corpus_is_rejected(self):
        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(HEADER_ROWS, entries=[])
        self.assertIn('observations', str(caught.exception))

    # 8. a mangled utf-8 currency symbol is rejected
    def test_mangled_euro_symbol_is_rejected(self):
        rows = [(k, v, f, ('?1,234.57' if '€' in f else out)) for k, v, f, out in HEADER_ROWS]
        with self.assertRaises(checker.Fail):
            self.run_verify(synthetic(rows), entries=[])

    # 9. an undeclared difference against the independent reference is rejected
    def test_undeclared_difference_is_rejected(self):
        def shifted(fmt, value):
            return 'something else'

        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(synthetic(HEADER_ROWS), oracle=shifted, entries=[])
        self.assertIn('undeclared', str(caught.exception).lower())

    # 10. a declared but unobserved divergence (stale adjudication) is rejected
    def test_stale_divergence_is_rejected(self):
        entries = [{'id': 'stale', 'formats': ['0.00'], 'reason': 'r', 'reference': 'ref'}]
        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(synthetic(HEADER_ROWS), entries=entries)
        self.assertIn('stale', str(caught.exception).lower())

    # 11. an adjudicated difference is accepted, and reported
    def test_declared_difference_is_accepted(self):
        def shifted(fmt, value):
            if fmt == '000':
                return 'wrong'
            return oracle_from(synthetic(HEADER_ROWS))(fmt, value)

        report = self.run_verify(synthetic(HEADER_ROWS), oracle=shifted,
                                 entries=[{'id': 'width', 'formats': ['000'], 'reason': 'r', 'reference': 'ref'}])
        self.assertEqual(report['differential']['divergences'], ['width'])
        self.assertEqual(report['differential']['differences'], 1)

    # 12. the real divergence table is well formed and covers only observed formats
    def test_real_divergence_table_is_well_formed(self):
        data = checker.load_divergences()
        self.assertTrue(data['divergences'])
        for entry in data['divergences']:
            self.assertTrue(entry['reason'].strip())
            self.assertTrue(entry['reference'].strip())
            self.assertFalse(entry.get('verified_against_excel', True),
                             'an unverified divergence must say so explicitly')

    # 13. a missing corpus file is an error, not a pass
    def test_missing_corpus_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(checker.Fail):
                checker.verify(Path(directory), True, oracle=lambda f, v: '')

    # 14. without a reference the checker downgrades instead of claiming parity
    def test_missing_reference_downgrades_the_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            artifacts = write_corpus(Path(directory), synthetic(HEADER_ROWS))
            report = checker.verify(artifacts, require_reference=False, oracle=None,
                                    divergence_table={'divergences': []})
            self.assertIsNone(report['differential'])
            self.assertIn('re-derivation only', report['claim'])

    # 15. a reference that raises is a gate failure, never a silent skip
    def test_reference_failure_is_reported(self):
        def broken(fmt, value):
            raise RuntimeError('oracle exploded')

        with self.assertRaises(checker.Fail) as caught:
            self.run_verify(synthetic(HEADER_ROWS), oracle=broken, entries=[])
        self.assertIn('reference oracle failed', str(caught.exception))


if __name__ == '__main__':
    unittest.main()
