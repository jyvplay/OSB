"""Metadata mutations, not generated/fake Android observations. Real failed/success receipts also checked."""
import copy
import json
from pathlib import Path
import unittest
from r24_evidence_gate import RULES,assess
ROOT=Path(__file__).resolve().parents[1]
class R24EvidenceTests(unittest.TestCase):
    def test_actual_first_ui_compile_failure_and_old_pass_cannot_authorize_new_ui(self):
        failed=json.loads((ROOT/'docs/verification/r24-first-ui-result.json').read_text())
        self.assertEqual({'PASS':0,'FAIL':0,'SKIP':0},failed['counts'])
        self.assertFalse(assess(failed,failed['candidate'],'r24_desktop_api34')[0])
        old=json.loads((ROOT/'docs/verification/r23-final-privacy-result.json').read_text())
        self.assertFalse(assess(old,old['candidate'],'r24_screenoff_api34')[0])
    def test_actual_new_screen_off_receipt_has_narrow_scope(self):
        actual=json.loads((ROOT/'docs/verification/r24-first-privacy-result.json').read_text())
        self.assertEqual({'PASS':18,'FAIL':0,'SKIP':0},actual['counts'])
        self.assertTrue(assess(actual,actual['candidate'],'r24_screenoff_api34')[0])
        self.assertFalse(assess(actual,'f'*40,'r24_screenoff_api34')[0])
        for claim in ['r24_screenoff_api37','r24_desktop_api34','r24_pdf_boundary_api34','all_hardware_secure','full_product','tails_equivalent']:
            self.assertFalse(assess(actual,actual['candidate'],claim)[0])
    def test_declared_policy_names_cannot_be_omitted_duplicated_or_marked_nonpass(self):
        for claim,names in RULES.items():
            base={'run':1,'url':'https://github.com/jyvplay/OSB/actions/runs/1','platform_api':37 if claim.endswith('api37') else 34,'candidate':'a'*40,'conclusion':'success','device_conclusion':'success','suite':'privacy' if 'screenoff' in claim else 'focused',
                  'cases':[{'case':n,'result':'PASS'} for n in sorted(names)],'counts':{'PASS':len(names),'FAIL':0,'SKIP':0}}
            self.assertTrue(assess(base,'a'*40,claim)[0]) # Policy grammar, NOT a native execution receipt.
            for i in range(len(names)):
                bad=copy.deepcopy(base);bad['cases'].pop(i);bad['counts']['PASS']-=1
                self.assertFalse(assess(bad,'a'*40,claim)[0])
            for mutation in ['FAIL','SKIP','duplicate','bool','wrong_context']:
                bad=copy.deepcopy(base)
                if mutation in ['FAIL','SKIP']:bad['cases'][0]['result']=mutation;bad['counts']['PASS']-=1;bad['counts'][mutation]=1
                elif mutation=='duplicate':bad['cases'].append(bad['cases'][0]);bad['counts']['PASS']+=1
                elif mutation=='bool':bad['counts']['FAIL']=False
                else:bad['suite']='full' if 'screenoff' in claim else 'privacy'
                self.assertFalse(assess(bad,'a'*40,claim)[0])

    def test_actual_api34_receipt_never_proves_api37_or_malformed_run(self):
        actual=json.loads((ROOT/'docs/verification/r24-first-privacy-result.json').read_text())
        from r23_evidence_gate import assess as old_assess
        bad=copy.deepcopy(actual);bad['platform_api']=37
        self.assertFalse(old_assess(bad,bad['candidate'],'r23_privacy_api34')[0])
        for value in [True,-1,'1',None]:
            bad=copy.deepcopy(actual);bad['run']=value
            self.assertFalse(assess(bad,bad['candidate'],'r24_screenoff_api34')[0])
        bad=copy.deepcopy(actual);bad['suite']=[]
        self.assertFalse(assess(bad,bad['candidate'],'r24_screenoff_api34')[0])
