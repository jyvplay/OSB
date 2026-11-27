# SPDX-License-Identifier: Apache-2.0
"""Fail-detecting tests for harness shortcuts; no compiler invocation here."""
import unittest
from run_r146_checks import diff_results, control_wiring, control_binding
from r146_corpus import build_corpus

class ResultIntegrity(unittest.TestCase):
    cases = [('geo', '', 'test')]
    expected = {'c0000': 'ok'}

    def test_real_match(self):
        self.assertFalse(diff_results('c0000\tgeo\tok\n', self.expected, self.cases))

    def test_empty_results_cannot_pass(self):
        self.assertTrue(diff_results('', self.expected, self.cases))

    def test_duplicate_results_cannot_pass(self):
        self.assertTrue(diff_results('c0000\tgeo\tbad\nc0000\tgeo\tok\n', self.expected, self.cases))

    def test_wrong_lane_cannot_pass(self):
        self.assertTrue(diff_results('c0000\tpdf\tok\n', self.expected, self.cases))

    def test_malformed_and_extra_results_cannot_pass(self):
        self.assertTrue(diff_results('garbage', self.expected, self.cases))
        self.assertTrue(diff_results('c0000\tgeo\tok\nc0001\tgeo\tok\n', self.expected, self.cases))

    def test_thin_corpus_cannot_pass(self):
        self.assertTrue(control_wiring(self.cases))
        self.assertFalse(control_wiring(build_corpus()))

    def test_real_sources_bound(self):
        self.assertFalse(control_binding())
