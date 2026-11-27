#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Pinned regression vectors, NOT substitute evidence for JVM execution.

The hashes bind input+expected verdict after the differential run. They detect
accidental oracle/corpus changes; pinning does not prove either is correct.
Run run_r146_checks.py for production compilation, execution and fault controls.
"""
from collections import Counter
import hashlib
import json
from pathlib import Path
import unittest
from r146_corpus import build_corpus
from r146_oracle import FEATURES, Refusal, enc, dec, oracle, _fmt_java, _as_int, _as_long

CASES = build_corpus()
PINS = json.loads(Path(__file__).with_name('r146_vectors.json').read_text())


class CorpusContract(unittest.TestCase):
    def test_at_least_five_cases_per_feature(self):
        counts = Counter(f for f, _, _ in CASES)
        self.assertEqual(set(counts), set(FEATURES))
        self.assertTrue(all(n >= 5 for n in counts.values()), counts)
        self.assertEqual(len(CASES), len(PINS))

    def test_refusal_and_success_both_exercised_in_each_lane(self):
        results = {f: set() for f in FEATURES}
        for feature, payload, _ in CASES:
            try:
                oracle(feature, payload)
                results[feature].add('ok')
            except Refusal:
                results[feature].add('refused')
        for feature, outcomes in results.items():
            with self.subTest(feature=feature):
                self.assertEqual(outcomes, {'ok', 'refused'})

    def test_fixed_point_transport(self):
        for text in ['%,|~;#:=@\n\r\t', '', 'a=100%,b', 'plain', 'é漢🙂']:
            self.assertEqual(dec(enc(text)), text)

    def test_rounding_ties_are_not_python_bankers_rounding(self):
        self.assertEqual(_fmt_java(42.626953125, 8), '42.62695313')
        self.assertEqual(_fmt_java(-0.0, 4), '-0.0000')
        self.assertEqual(_as_long('9223372036854775807'), 9223372036854775807)
        with self.assertRaises(Refusal):
            _as_int('2147483648')
        with self.assertRaises(Refusal):
            _as_long('9223372036854775808')

    def test_repaired_hyphenated_odf_tags(self):
        data = '<office:document-meta><office:meta><meta:editing-cycles>3</meta:editing-cycles><dc:title>x</dc:title></office:meta></office:document-meta>'
        result = oracle('odfmeta', enc(data))
        self.assertIn('<dc:title>x</dc:title>', result)
        self.assertNotIn('editing-cycles', result)


class PinnedVectors(unittest.TestCase):
    pass


def make_case(index, feature, payload, note):
    def test(self):
        try:
            verdict = oracle(feature, payload)
        except Refusal:
            verdict = '!REF'
        pin = PINS[index]
        self.assertEqual(pin['case'], index)
        self.assertEqual(pin['lane'], feature)
        self.assertEqual(pin['note'], note)
        self.assertEqual(hashlib.sha256((feature + '\0' + payload + '\0' + verdict).encode()).hexdigest(), pin['sha256'])
    test.__doc__ = note
    return test


for i, (feature, payload, note) in enumerate(CASES):
    setattr(PinnedVectors, 'test_%04d_%s' % (i, feature), make_case(i, feature, payload, note))


if __name__ == '__main__':
    unittest.main()
