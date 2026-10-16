"""Mutations of actual receipts only: tests validator refusal, NOT synthetic Android execution."""
import copy,json,unittest
from pathlib import Path
from r21_evidence_gate import assess,UNSUPPORTED
ROOT=Path(__file__).resolve().parents[1]
class R21EvidenceTests(unittest.TestCase):
 def setUp(self):self.r=json.loads((ROOT/'docs/verification/r21-boundary-spike-result.json').read_text());self.sha=self.r['candidate']
 def test_actual_worker_spike_is_narrowly_admissible(self):self.assertTrue(assess(self.r,self.sha,'csv_worker_spike_api34')[0])
 def test_spike_is_not_workbench_or_full_gate(self):
  for c in ['r21_workbench_api34','r21_full_api34']:self.assertFalse(assess(self.r,self.sha,c)[0])
 def test_modal_shortcuts_are_refused_even_with_green_spike(self):
  for c in UNSUPPORTED:self.assertFalse(assess(self.r,self.sha,c)[0],c)
 def test_wrong_candidate_is_refused(self):self.assertFalse(assess(self.r,'f'*40,'csv_worker_spike_api34')[0])
 def test_mutated_counts_and_suite_are_refused(self):
  for k,v in [('counts',{'PASS':True,'FAIL':0,'SKIP':0}),('suite','full'),('device_conclusion','failure'),('url','https://example.com')]:
   r=copy.deepcopy(self.r);r[k]=v;self.assertFalse(assess(r,self.sha,'csv_worker_spike_api34')[0])
 def test_failed_real_ui_run_cannot_be_cherry_picked(self):
  r=json.loads((ROOT/'docs/verification/r21-first-ui-result.json').read_text())
  self.assertEqual({'PASS':4,'FAIL':2,'SKIP':0},r['counts'])
  later=json.loads((ROOT/'docs/verification/r21-ime-ui-result.json').read_text())
  self.assertFalse(assess(later,later['candidate'],'r21_workbench_api34')[0])
  for c in ['csv_worker_spike_api34','r21_workbench_api34']:self.assertFalse(assess(r,r['candidate'],c)[0])
 def test_earlier_104_case_green_run_is_not_later_workbench(self):
  r=json.loads((ROOT/'docs/verification/r21-boundary-full-result.json').read_text())
  self.assertFalse(assess(r,r['candidate'],'r21_full_api34')[0])
