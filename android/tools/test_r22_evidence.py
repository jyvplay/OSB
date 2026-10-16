"""Mutate retained actual receipts, not Android execution or app functionality."""
import copy,json,unittest
from pathlib import Path
from r22_evidence_gate import assess,UNSUPPORTED
ROOT=Path(__file__).resolve().parents[1]
class R22EvidenceTests(unittest.TestCase):
 def setUp(self):self.r=json.loads((ROOT/'docs/verification/r22-platform-spike-result.json').read_text());self.sha=self.r['candidate']
 def test_actual_platform_spike_has_only_its_scope(self):
  self.assertTrue(assess(self.r,self.sha,'r22_platform_api34')[0])
  for c in ['r22_import_api34','r22_full_api34']:self.assertFalse(assess(self.r,self.sha,c)[0])
 def test_wrong_candidate(self):self.assertFalse(assess(self.r,'f'*40,'r22_platform_api34')[0])
 def test_mutated_metadata(self):
  for k,v in [('counts',{'PASS':True,'FAIL':0,'SKIP':0}),('suite','full'),('device_conclusion','failure'),('url','https://example.com'),('conclusion','failure')]:
   r=copy.deepcopy(self.r);r[k]=v;self.assertFalse(assess(r,self.sha,'r22_platform_api34')[0])
 def test_missing_duplicate_failed_and_skipped_cases(self):
  for mode in ['missing','duplicate','FAIL','SKIP']:
   r=copy.deepcopy(self.r)
   if mode=='missing':r['cases'].pop()
   elif mode=='duplicate':r['cases'].append(r['cases'][0])
   else:r['cases'][0]['result']=mode
   r['counts']={k:sum(x['result']==k for x in r['cases']) for k in ['PASS','FAIL','SKIP']}
   self.assertFalse(assess(r,self.sha,'r22_platform_api34')[0])
 def test_no_modal_shortcut(self):
  for c in UNSUPPORTED:self.assertFalse(assess(self.r,self.sha,c)[0],c)
 def test_inherited_failures_are_not_full_acceptance(self):
  for prefix in ['r22-inherited-native','r22-spike-full']:
   r=json.loads((ROOT/f'docs/verification/{prefix}-result.json').read_text())
   self.assertFalse(assess(r,r['candidate'],'r22_full_api34')[0])

 def test_failed_import_workflows_cannot_be_cherry_picked(self):
  for prefix in ['r22-first-import','r22-initialized-import','r22-picker-repair']:
   r=json.loads((ROOT/f'docs/verification/{prefix}-result.json').read_text())
   for claim in ['r22_platform_api34','r22_import_api34','r22_full_api34']:
    self.assertFalse(assess(r,r['candidate'],claim)[0])

 def test_actual_final_focused_scope_and_candidate(self):
  r=json.loads((ROOT/'docs/verification/r22-final-focused-result.json').read_text())
  self.assertEqual({'PASS':10,'FAIL':0,'SKIP':0},r['counts'])
  self.assertTrue(assess(r,r['candidate'],'r22_import_api34')[0])
  self.assertFalse(assess(r,self.sha,'r22_import_api34')[0])
  self.assertFalse(assess(r,r['candidate'],'r22_full_api34')[0])
  for claim in UNSUPPORTED:self.assertFalse(assess(r,r['candidate'],claim)[0])

 def test_actual_final_full_gate_and_retained_failures(self):
  r=json.loads((ROOT/'docs/verification/r22-final-native-result.json').read_text())
  self.assertEqual({'PASS':119,'FAIL':0,'SKIP':0},r['counts'])
  self.assertTrue(assess(r,r['candidate'],'r22_full_api34')[0])
  self.assertFalse(assess(r,self.sha,'r22_full_api34')[0])
  for claim in UNSUPPORTED:self.assertFalse(assess(r,r['candidate'],claim)[0])
  for prefix in ['r22-first-full','r22-initialized-full']:
   failed=json.loads((ROOT/f'docs/verification/{prefix}-result.json').read_text())
   self.assertFalse(assess(failed,failed['candidate'],'r22_full_api34')[0])
