"""Real Python tests of traceability validation, not Android implementation tests."""
import copy
import json
import unittest
from pathlib import Path
import requirements

ROOT=Path(__file__).resolve().parents[1]

class TraceabilityTest(unittest.TestCase):
    def setUp(self):
        self.rows=json.loads((ROOT/'docs/feature-register.json').read_text())
        self.spikes=json.loads((ROOT/'docs/spikes.json').read_text())

    def test_generated_csv_matches_register(self):
        requirements.validate(self.rows,self.spikes)
        self.assertEqual(requirements.render(self.rows),(ROOT/'requirements.csv').read_text())

    def test_missing_and_duplicate_features_are_rejected(self):
        for rows in (self.rows[1:], self.rows+[copy.deepcopy(self.rows[0])]):
            with self.assertRaises(AssertionError): requirements.validate(rows,self.spikes)

    def test_spike_pass_requires_evidence(self):
        self.spikes[0]['status']='passed'
        with self.assertRaises(AssertionError): requirements.validate(self.rows,self.spikes)

    def test_unknown_gate_is_rejected(self):
        self.rows[0]['gates'].append('S999')
        with self.assertRaises(AssertionError): requirements.validate(self.rows,self.spikes)

    def test_every_row_has_a_route_and_a_test_obligation(self):
        for field in ('route','tests'):
            rows=copy.deepcopy(self.rows)
            rows[0][field]=''
            with self.assertRaises(AssertionError): requirements.validate(rows,self.spikes)

if __name__=='__main__': unittest.main()
