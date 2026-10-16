"""Offline mutations of actual retained receipts; not new Android execution."""
import copy,json,unittest
from pathlib import Path
from r20_evidence_gate import assess,UNSUPPORTED
ROOT=Path(__file__).resolve().parents[1]
class R20EvidenceTests(unittest.TestCase):
 def setUp(self):self.r=json.loads((ROOT/'docs/verification/r20-spike-result.json').read_text());self.sha=self.r['candidate']
 def test_spike_cannot_promote_later_workspace(self):self.assertFalse(assess(self.r,self.sha,'offline_workspace_api34')[0])
 def test_modal_shortcuts_refused(self):
  for claim in UNSUPPORTED:self.assertFalse(assess(self.r,self.sha,claim)[0],claim)
 def test_candidate_suite_and_counts_mutations_refused(self):
  self.assertFalse(assess(self.r,'f'*40,'offline_workspace_api34')[0])
  for key,value in [('suite','full'),('conclusion','failure'),('counts',{'PASS':True,'FAIL':0,'SKIP':0}),('url','https://example.com')]:
   r=copy.deepcopy(self.r);r[key]=value;self.assertFalse(assess(r,self.sha,'offline_workspace_api34')[0])
 def test_duplicate_case_refused(self):
  r=copy.deepcopy(self.r);r['cases'].append(r['cases'][0]);r['counts']['PASS']+=1
  self.assertFalse(assess(r,self.sha,'offline_workspace_api34')[0])

 def test_actual_workspace_receipt_passes_only_its_narrow_claim(self):
  r=json.loads((ROOT/'docs/verification/r20-privacy-result.json').read_text())
  self.assertTrue(assess(r,r['candidate'],'offline_workspace_api34')[0])
  for claim in UNSUPPORTED:self.assertFalse(assess(r,r['candidate'],claim)[0],claim)
  for field,value in [('suite','full'),('device_conclusion','failure'),('counts',{'PASS':7,'FAIL':0,'SKIP':0})]:
   bad=copy.deepcopy(r);bad[field]=value;self.assertFalse(assess(bad,r['candidate'],'offline_workspace_api34')[0])

 def test_actual_earlier_main_candidate_is_only_its_own_gate(self):
  r=json.loads((ROOT/'docs/verification/r20-first-native-result.json').read_text())
  self.assertTrue(assess(r,r['candidate'],'origin_lease_api34')[0])
  self.assertFalse(assess(r,r['candidate'],'counts_export_api34')[0]) # New timezone attack is required after repair.
  self.assertFalse(assess(r,'f'*40,'counts_export_api34')[0])
  repaired=json.loads((ROOT/'docs/verification/r20-final-native-result.json').read_text())
  self.assertEqual({'PASS':101,'FAIL':0,'SKIP':0},repaired['counts'])
  for claim in ['counts_export_api34','origin_lease_api34']:self.assertTrue(assess(repaired,repaired['candidate'],claim)[0])
  self.assertFalse(assess(repaired,r['candidate'],'counts_export_api34')[0])
 def test_actual_later_main_failure_cannot_be_cherry_picked(self):
  for prefix in ['r20-native','r20-notes-main','r20-input-latest-main']:
   r=json.loads((ROOT/f'docs/verification/{prefix}-result.json').read_text())
   for claim in ['counts_export_api34','origin_lease_api34']:self.assertFalse(assess(r,r['candidate'],claim)[0])

 def test_actual_input_feedback_failure_cannot_promote_workspace(self):
  r=json.loads((ROOT/'docs/verification/r20-input-first-result.json').read_text())
  self.assertEqual({'PASS':5,'FAIL':1,'SKIP':0},r['counts'])
  self.assertFalse(assess(r,r['candidate'],'offline_workspace_api34')[0])
  repaired=json.loads((ROOT/'docs/verification/r20-feedback-privacy-result.json').read_text())
  self.assertTrue(assess(repaired,repaired['candidate'],'offline_workspace_api34')[0])
  self.assertFalse(assess(repaired,r['candidate'],'offline_workspace_api34')[0])
