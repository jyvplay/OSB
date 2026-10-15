import unittest
from collect_native_evidence import parse_cases
class NativeReceiptChecks(unittest.TestCase):
    def test_cases_not_inferred_from_success(self):
        self.assertEqual([],parse_cases([{'message':'Build success; 49 tests planned'}]))
    def test_exact_statuses(self):
        cases=parse_cases([{'message':'Android test results: dev.fusion.A.a=PASS; dev.fusion.B.b=FAIL; dev.fusion.C.c=SKIP'}])
        self.assertEqual(['PASS','FAIL','SKIP'],[c['result'] for c in cases])
    def test_duplicate_refused_across_annotations(self):
        row={'message':'Android test results: dev.fusion.A.a=PASS'}
        with self.assertRaises(ValueError):parse_cases([row,row])
    def test_unknown_or_truncated_status_refused(self):
        for s in ['PAS','PASS; ','UNKNOWN','PASS unrelated text']:
            with self.assertRaises(ValueError):parse_cases([{'message':'Android test results: dev.fusion.A.a='+s}])
    def test_explicit_no_execution_is_zero_not_pass(self):
        self.assertEqual([],parse_cases([{'message':'Android test results: No executed device test cases found. No pass inferred.'}]))
    def test_conflicting_execution_claims_refused(self):
        with self.assertRaises(ValueError):parse_cases([
            {'message':'Android test results: No executed device test cases found. No pass inferred.'},
            {'message':'Android test results: dev.fusion.A.a=PASS'}])
