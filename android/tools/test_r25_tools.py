import copy
import json
import tempfile
import unittest
from pathlib import Path
from r25_ui_lab import collect
from r25_evidence_gate import expected, verify

ROOT = Path(__file__).resolve().parents[1]

class R25ToolTests(unittest.TestCase):
    def test_preflight_unknown_is_not_ready_and_repeat_actions_refused(self):
        # Artificial parser inputs only, never native execution evidence.
        state = dict(api=34, interactive=True, keyguard_locked=False, device_locked=False, credential_secure=False)
        row = dict(origin='android-instrumentation', case='parser-only', phase='candidate', before=dict(state, interactive=False), after=state, actions=['one-real-wake'])
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'r25-ui-preflight.jsonl'
            p.write_text(json.dumps(row)+'\n')
            self.assertEqual(collect(tmp)['summary']['not_ready_before'], 1)
            self.assertEqual(collect(tmp)['summary']['not_ready_after'], 0)
            for bad in (None, 'false', 1):
                changed = copy.deepcopy(row); changed['after']['interactive'] = bad
                p.write_text(json.dumps(changed)+'\n')
                with self.assertRaises(ValueError): collect(tmp)
            changed = copy.deepcopy(row); changed['actions'] *= 2
            p.write_text(json.dumps(changed)+'\n')
            with self.assertRaises(ValueError): collect(tmp)
            changed = copy.deepcopy(row); changed['before']['api'] = 37
            p.write_text(json.dumps(changed)+'\n')
            with self.assertRaises(ValueError): collect(tmp)

    def test_historical_counterexample_preflight_cannot_count_as_candidate(self):
        state = dict(api=34, interactive=True, keyguard_locked=False, device_locked=False, credential_secure=False)
        row = dict(origin='android-instrumentation', case='parser-only', phase='prior-source-counterexample', before=state, after=state, actions=[])
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp)/'r25-ui-preflight.jsonl').write_text(json.dumps(row)+'\n')
            report = collect(tmp)
            self.assertEqual(report['summary']['observations'], 0)
            self.assertEqual(report['summary']['other_phase_observations'], 1)
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(collect(tmp)['summary']['observations'], 0)

    def test_exact_named_native_contract_and_bad_receipt_mutations(self):
        self.assertEqual(len(expected('full')), 143)
        self.assertEqual(len(expected('readiness')), 6)
        candidate='a'*40  # Parser-only value; never sent to GitHub or recorded as a native run.
        report=dict(candidate=candidate, suite='focused', platform_api=34, conclusion='success', device_conclusion='success',
                    cases=[dict(case=name,result='PASS') for name in sorted(expected('readiness'))], counts=dict(PASS=6,FAIL=0,SKIP=0))
        self.assertEqual(verify(report,candidate,'readiness')['passed_cases'],6)
        for mutation in ('missing','skip','failure','wrong-source','wrong-api','duplicate'):
            r=copy.deepcopy(report)
            if mutation=='missing': r['cases'].pop()
            elif mutation=='skip': r['cases'][0]['result']='SKIP'
            elif mutation=='failure': r['conclusion']='failure'
            elif mutation=='wrong-source': r['candidate']='b'*40
            elif mutation=='wrong-api': r['platform_api']=37
            else: r['cases'].append(r['cases'][0])
            with self.assertRaises(ValueError): verify(r,candidate,'readiness')

    def test_real_failed_r24c_full_run_cannot_be_promoted(self):
        r=json.loads((ROOT/'docs/verification/r24c-tenth-main-result.json').read_text())
        self.assertEqual(r['counts'],dict(PASS=137,FAIL=5,SKIP=0))
        with self.assertRaises(ValueError): verify(r,r['candidate'],'full')
