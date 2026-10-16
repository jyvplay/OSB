"""Policy parser fixtures ONLY. They are not Android executions or independent review."""
import copy,unittest
from r18_evidence_gate import TASKS,OWNED,assess
class R18EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.candidate='a'*40
        self.receipt={'run':1,'url':'https://github.com/jyvplay/OSB/actions/runs/1',
          'candidate':self.candidate,'conclusion':'success','device_conclusion':'success',
          'cases':[{'case':c,'result':'PASS'} for c in sorted(TASKS|OWNED)],
          'counts':{'PASS':7,'FAIL':0,'SKIP':0}}
    def check_receipt(self,receipt):return assess(receipt,self.candidate,'tasks_native_subset')[0]
    def test_exact_subset_fixture(self):
        self.assertTrue(self.check_receipt(self.receipt))
        self.assertTrue(assess(self.receipt,self.candidate,'owned_sheet_native_subset')[0])
    def test_no_modal_promotions(self):
        for claim in ['full_modules','full_tasks','full_office','LOK_parity','arithmetic_truth',
                      'crash_durability','all_API_versions','physical_timing','sealed_from_unlink',
                      'downgrade_writer','all_inputs_exhaustive','whole_browser','retry_proves_repair',
                      'independent_expert','fuzz_coverage','complete_AX','legal_clearance','Kotlin_formal_proof']:
            self.assertFalse(assess(self.receipt,self.candidate,claim)[0],claim)
    def test_patch_does_not_inherit_trust(self):
        r=copy.deepcopy(self.receipt);r['candidate']='b'*40;self.assertFalse(self.check_receipt(r))
    def test_build_or_queue_cannot_replace_execution(self):
        for field in ['conclusion','device_conclusion']:
            for value in ['',None,'failure','skipped','cancelled']:
                r=copy.deepcopy(self.receipt);r[field]=value;self.assertFalse(self.check_receipt(r))
        r=copy.deepcopy(self.receipt);r['cases']=[];r['counts']={'PASS':0,'FAIL':0,'SKIP':0};self.assertFalse(self.check_receipt(r))
    def test_missing_required_case(self):
        r=copy.deepcopy(self.receipt);r['cases']=[x for x in r['cases'] if x['case'] not in TASKS];r['counts']['PASS']=2
        self.assertFalse(self.check_receipt(r))
    def test_duplicate_or_lied_counts(self):
        r=copy.deepcopy(self.receipt);r['cases'].append(r['cases'][0]);r['counts']['PASS']=8;self.assertFalse(self.check_receipt(r))
        for value in [8,-1,True,'7']:
            r=copy.deepcopy(self.receipt);r['counts']['PASS']=value;self.assertFalse(self.check_receipt(r))
    def test_failure_or_skip_not_green(self):
        for result in ['FAIL','SKIP']:
            r=copy.deepcopy(self.receipt);r['cases'][0]['result']=result;r['counts']['PASS']=6;r['counts'][result]=1
            self.assertFalse(self.check_receipt(r))
    def test_run_locator_consistency_not_authentication(self):
        for value in [0,True,None,'1']:
            r=copy.deepcopy(self.receipt);r['run']=value;self.assertFalse(self.check_receipt(r))
        r=copy.deepcopy(self.receipt);r['url']='https://example.org';self.assertFalse(self.check_receipt(r))
