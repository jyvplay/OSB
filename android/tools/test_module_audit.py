import copy
import json
import unittest
import module_audit

class ModuleAuditChecks(unittest.TestCase):
    def setUp(self):
        self.audit = json.loads((module_audit.ROOT/'docs/module-audit.json').read_text())
        self.included = module_audit.included_modules()
    def test_all_included_modules_accounted_for(self):
        module_audit.validate(self.audit,self.included)
        c=module_audit.counts(self.audit)
        self.assertEqual(c['total'],c['complete']+c['unfinished'])
        self.assertEqual(100,c['complete_percent']+c['unfinished_percent'])
    def test_missing_module_is_rejected(self):
        self.audit['modules'].pop()
        with self.assertRaises(AssertionError): module_audit.validate(self.audit,self.included)
    def test_duplicate_module_is_rejected(self):
        self.audit['modules'].append(copy.deepcopy(self.audit['modules'][0]))
        with self.assertRaises(AssertionError): module_audit.validate(self.audit,self.included)
    def test_source_presence_cannot_bypass_acceptance(self):
        self.audit['modules'][0]['status']='complete'
        with self.assertRaises(AssertionError): module_audit.validate(self.audit,self.included)
    def test_nonexistent_alias_is_rejected(self):
        self.audit['architecture_mapping'][0]['candidates']=[':feature:imaginary']
        with self.assertRaises(AssertionError): module_audit.validate(self.audit,self.included)
    def test_report_is_reproducible(self):
        self.assertEqual((module_audit.ROOT/'docs/MODULE-COMPLETION.md').read_text(),module_audit.render(self.audit))
