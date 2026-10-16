"""Mutate ACTUAL retained receipts; does not fabricate Android evidence or app behavior."""
import copy
import json
from pathlib import Path
import unittest
from r23_evidence_gate import assess, UNSUPPORTED

ROOT = Path(__file__).resolve().parents[1]


class R23EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.old = json.loads((ROOT / 'docs/verification/r21-baseline-privacy-result.json').read_text())

    def test_historical_companion_pass_is_not_r23(self):
        for claim in ['r23_input_api34', 'r23_memory_api34', 'r23_privacy_api34']:
            self.assertFalse(assess(self.old, self.old['candidate'], claim)[0])

    def test_wrong_candidate_or_apk_is_rejected(self):
        self.assertFalse(assess(self.old, 'f' * 40, 'r23_input_api34')[0])
        self.assertFalse(assess(self.old, self.old['candidate'][:7], 'r23_input_api34')[0])
        main = json.loads((ROOT / 'docs/verification/r22-final-native-result.json').read_text())
        self.assertFalse(assess(main, main['candidate'], 'r23_input_api34')[0])

    def test_counterfeit_shape_never_widens_claim(self):
        for claim in UNSUPPORTED:
            self.assertFalse(assess(self.old, self.old['candidate'], claim)[0], claim)
        for key, value in [('run', True), ('url', 'https://example.com'), ('suite', 'full'), ('conclusion', 'failure'),
                           ('device_conclusion', 'failure'), ('counts', {'PASS': True, 'FAIL': 0, 'SKIP': 0}), ('cases', None)]:
            mutated = copy.deepcopy(self.old); mutated[key] = value
            self.assertFalse(assess(mutated, self.old['candidate'], 'r23_input_api34')[0])

    def test_actual_first_r23_pass_has_only_its_named_scope(self):
        first = json.loads((ROOT / 'docs/verification/r23-first-privacy-result.json').read_text())
        self.assertEqual({'PASS': 12, 'FAIL': 0, 'SKIP': 0}, first['counts'])
        self.assertTrue(assess(first, first['candidate'], 'r23_input_api34')[0])
        self.assertFalse(assess(first, first['candidate'], 'r23_memory_api34')[0])
        self.assertFalse(assess(first, first['candidate'], 'r23_privacy_api34')[0])
        for claim in UNSUPPORTED:
            self.assertFalse(assess(first, first['candidate'], claim)[0])
        for value in [[], {}, True]:
            malformed = copy.deepcopy(first); malformed['cases'][0]['result'] = value
            self.assertFalse(assess(malformed, first['candidate'], 'r23_input_api34')[0])

    def test_malformed_input_is_refusal_not_exception(self):
        for receipt in [None, [], {}, {'cases': [{}]}]:
            self.assertFalse(assess(receipt, None, 'r23_input_api34')[0])
        for mode in ['duplicate', 'FAIL', 'SKIP', 'missing']:
            mutated = copy.deepcopy(self.old)
            if mode == 'duplicate': mutated['cases'].append(mutated['cases'][0])
            elif mode == 'missing': mutated['cases'].pop()
            else: mutated['cases'][0]['result'] = mode
            mutated['counts'] = {key: sum(case['result'] == key for case in mutated['cases']) for key in ['PASS', 'FAIL', 'SKIP']}
            self.assertFalse(assess(mutated, mutated['candidate'], 'r23_input_api34')[0])


if __name__ == '__main__': unittest.main()
