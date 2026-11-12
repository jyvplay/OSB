#!/usr/bin/env python3
"""Negative controls for the R77 condition verifier.

Each control mutates exactly one thing — an observation, the record, the reference oracle or the
artifacts directory — and asserts that tools/check_r77_sheet_conditions.py rejects it. A gate that
cannot fail is not a gate.

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r77_sheet_conditions -v
"""
from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

import check_r77_sheet_conditions as checker

BASE_ROWS = [
    ('number', 150.0, '[>100]0.00;-0.00', '150.00'),
    ('number', 100.0, '[>100]0.00;-0.00', '-100.00'),
    ('number', 0.5, '[>100]0.00;-0.00', '-0.50'),
    ('number', 1234.0, '[>=1000]#,##0;[<0]0.00;0.0', '1,234'),
    ('number', -3.5, '[>=1000]#,##0;[<0]0.00;0.0', '3.50'),
    ('number', 12.0, '[>=1000]#,##0;[<0]0.00;0.0', '12.0'),
    ('number', 50.0, '[Red][<100]0;[Blue][>=100]0', '50'),
    ('number', 150.0, '[Red][<100]0;[Blue][>=100]0', '150'),
    ('number', -5.0, '[Red][<0]-0.00;[Green][>=0]+0.00', '-5.00'),
    ('number', 5.0, '[Red][<0]-0.00;[Green][>=0]+0.00', '+5.00'),
    ('number', 5.0, '[=5]"five";0.0', 'five'),
    ('number', 6.0, '[=5]"five";0.0', '6.0'),
    ('number', 2000000.0, '[>=1000000]#,##0.0,,"M";#,##0.0', '2.0M'),
    ('number', 1234.5, '[>=1000000]#,##0.0,,"M";#,##0.0', '1,234.5'),
    ('number', 1234.567, '#,##0.00', '1,234.57'),
    ('number', -1234.567, '#,##0.00', '-1,234.57'),
    ('number', 0.1234, '0.0%', '12.3%'),
    ('number', -0.25, '0.0%', '-25.0%'),
    ('number', 0.5, '0.0%', '50.0%'),
    ('refusal', 0.5, '[>100]General', 'REFUSED'),
    ('refusal', 1.0, '[>100]0.00;[<0]0.00', 'REFUSED'),
    ('refusal', 1.0, '[>100][<200]0.0;0', 'REFUSED'),
    ('refusal', 1.0, '[>1e3]0.0;0', 'REFUSED'),
    ('refusal', 1.0, '[abc]0.0', 'REFUSED'),
    ('refusal', 1.0, '0.0;[>100]0.0', 'REFUSED'),
]


def synthetic(rows):
    filler = [('number', 101.0 + index, '0.00', f'{101 + index}.00') for index in range(24)]
    return rows + filler


def write_corpus(directory: Path, rows) -> Path:
    artifacts = directory / 'artifacts'
    artifacts.mkdir(parents=True, exist_ok=True)
    body = '\n'.join('\t'.join(str(field) for field in row) for row in rows) + '\n'
    (artifacts / 'formats.tsv').write_text(body, encoding='utf-8')
    return artifacts


def perfect_oracle(rows, record=None):
    """Agrees with the corpus on the control formats and with the record on the pinned ones."""
    table = {(float(value), fmt): rendered for _kind, value, fmt, rendered in rows
             if '[' not in fmt}
    for entry in (record or {}).get('reference_pinned_observations', []):
        table[(float(entry['value']), entry['format'])] = entry['reference']

    def oracle(fmt: str, value: float) -> str:
        return table[(value, fmt)]

    return oracle


class ConditionVerifierTests(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.directory = Path(self._tmp.name)
        self.record = json.loads(checker.RECORD.read_text(encoding='utf-8'))

    def tearDown(self):
        self._tmp.cleanup()

    def run_verify(self, rows, record=None, oracle=None, require_reference=False):
        artifacts = write_corpus(self.directory, rows)
        active_record = record if record is not None else self.record
        return checker.verify(artifacts, require_reference,
                              oracle=oracle if oracle is not None else perfect_oracle(rows, active_record),
                              record=active_record)

    # -- the undisturbed corpus must pass ---------------------------------------------------
    def test_undisturbed_corpus_passes(self):
        report = self.run_verify(synthetic(BASE_ROWS))
        self.assertEqual(report['rows'], 49)
        self.assertEqual(report['refusals'], 6)
        self.assertEqual(report['rederived'], 49)

    # -- observation-level attacks -----------------------------------------------------------
    def test_doctored_rendered_value_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        rows[0] = ('number', 150.0, '[>100]0.00;-0.00', '151.00')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_fallback_value_rendered_with_the_condition_section_is_rejected(self):
        # 100 is not > 100, so the fallback section must be used; claiming 100.00 is wrong.
        rows = synthetic(BASE_ROWS)
        index = [i for i, row in enumerate(rows) if row[1] == 100.0][0]
        rows[index] = ('number', 100.0, '[>100]0.00;-0.00', '100.00')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_refusal_that_renders_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        rows[19] = ('refusal', 0.5, '[>100]General', '0.50')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_rendered_row_that_refuses_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        rows[0] = ('number', 150.0, '[>100]0.00;-0.00', 'REFUSED')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_duplicate_observation_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        rows.append(rows[0])
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_truncated_corpus_is_rejected(self):
        with self.assertRaises(checker.Fail):
            self.run_verify(BASE_ROWS[:5])

    def test_missing_corpus_is_an_error(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.directory / 'absent', False, oracle=perfect_oracle(BASE_ROWS),
                           record=self.record)

    # -- grammar-level attacks ---------------------------------------------------------------
    def test_scientific_threshold_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        index = [i for i, row in enumerate(rows) if row[2] == '[>1e3]0.0;0'][0]
        rows[index] = ('number', 1.0, '[>1e3]0.0;0', '1')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_condition_after_unconditional_section_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        index = [i for i, row in enumerate(rows) if row[2] == '0.0;[>100]0.0'][0]
        rows[index] = ('number', 1.0, '0.0;[>100]0.0', '100.0')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    def test_two_conditions_in_one_section_are_rejected(self):
        rows = synthetic(BASE_ROWS)
        index = [i for i, row in enumerate(rows) if row[2] == '[>100][<200]0.0;0'][0]
        rows[index] = ('number', 1.0, '[>100][<200]0.0;0', '1')
        with self.assertRaises(checker.Fail):
            self.run_verify(rows)

    # -- reference-level attacks -------------------------------------------------------------
    def test_changed_reference_observation_is_rejected(self):
        record = copy.deepcopy(self.record)
        record['reference_pinned_observations'][0]['reference'] = '50'
        rows = synthetic(BASE_ROWS)
        # The oracle keeps telling the truth: the record is what drifted.
        with self.assertRaises(checker.Fail):
            self.run_verify(rows, record=record, oracle=perfect_oracle(rows, self.record))

    def test_control_row_differing_from_the_reference_is_rejected(self):
        rows = synthetic(BASE_ROWS)
        # A "perfect" oracle that agrees with everything would hide a broken control row; here the
        # oracle is the truth and the corpus claims something else.
        artifacts = write_corpus(self.directory, rows)

        def oracle(fmt: str, value: float) -> str:
            return '1,234.57' if fmt == '#,##0.00' else 'unexpected'

        with self.assertRaises(checker.Fail):
            checker.verify(artifacts, False, oracle=oracle, record=self.record)

    def test_missing_record_schema_is_rejected(self):
        record = copy.deepcopy(self.record)
        record['schema'] = 'r76-format-divergences/1'
        with self.assertRaises(checker.Fail):
            self.run_verify(synthetic(BASE_ROWS), record=record)

    def test_absent_reference_downgrades_when_allowed_and_fails_when_required(self):
        artifacts = write_corpus(self.directory, synthetic(BASE_ROWS))
        original = checker.load_reference
        checker.load_reference = lambda: (_ for _ in ()).throw(checker.Fail('no ssf'))
        try:
            report = checker.verify(artifacts, False, oracle=None, record=self.record)
            self.assertIn('downgraded', report['control_differential'])
            with self.assertRaises(checker.Fail):
                checker.verify(artifacts, True, oracle=None, record=self.record)
        finally:
            checker.load_reference = original


if __name__ == '__main__':
    unittest.main()
