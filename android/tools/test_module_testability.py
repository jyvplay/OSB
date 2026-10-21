import json
import unittest
import module_testability as census

class TestabilityCensusChecks(unittest.TestCase):
    def setUp(self):
        self.data=json.loads((census.ROOT/'docs/module-testability.json').read_text())
    def positive(self):return next(r for r in self.data['rows'] if r['substantial_testable_subset'])
    def test_current_pairings_and_denominator(self):
        census.validate(self.data)
        c=census.counts(self.data)
        self.assertEqual(56,c['total'])
        self.assertEqual(54,len([r for r in self.data['rows'] if r['module'] not in (':privacy',':feature:maps')]))
        self.assertEqual(c['total'],c['substantial_testable_subsets']+c['not_established'])
    def test_generated_report(self):self.assertEqual(census.render(self.data),(census.ROOT/'docs/MODULE-TESTABILITY.md').read_text())
    def test_label_without_behavioral_check_rejected(self):
        self.positive()['checks']=[]
        with self.assertRaises(ValueError):census.validate(self.data)
    def test_stale_source_rejected(self):
        r=self.positive();r['sha256'][r['production'][0]]='0'*64
        with self.assertRaises(ValueError):census.validate(self.data)
    def test_other_modules_source_cannot_earn_credit(self):
        self.positive()['production']=['tools/module_testability.py']
        with self.assertRaises(ValueError):census.validate(self.data)
    def test_duplicate_module_rejected(self):
        self.data['rows'].append(self.positive().copy())
        with self.assertRaises(ValueError):census.validate(self.data)
    def test_source_presence_does_not_upgrade_full_acceptance(self):
        audit=json.loads((census.ROOT/'docs/module-audit.json').read_text())
        self.assertGreater(census.counts(self.data)['substantial_testable_subsets'],0)
        self.assertEqual(0,sum(r['status']=='complete' for r in audit['modules']))
