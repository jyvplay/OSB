import tempfile
from pathlib import Path
import unittest
from report_device_results import collect,messages

# Parser fixtures test the reporting tool, never stand in for executed Android cases.
class DeviceReportChecks(unittest.TestCase):
    def test_missing_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as root:
            report=collect(root)
            self.assertEqual('NO_EXECUTION_EVIDENCE',report['status'])
            self.assertEqual({'PASS':0,'FAIL':0,'SKIP':0},report['counts'])

    def test_failure_error_skip_and_success_are_distinct(self):
        with tempfile.TemporaryDirectory() as root:
            Path(root,'TEST-result.xml').write_text('<testsuite><testcase classname="C" name="a"/><testcase classname="C" name="b"><failure message="actual diagnostic"/></testcase><testcase classname="C" name="c"><error/></testcase><testcase classname="C" name="d"><skipped/></testcase></testsuite>')
            report=collect(root)
            self.assertEqual({'PASS':1,'FAIL':2,'SKIP':1},report['counts'])
            self.assertIn('C.c=FAIL',''.join(messages(report)))
            self.assertIn('actual diagnostic',''.join(messages(report)))

    def test_conflicting_duplicate_is_refused(self):
        with tempfile.TemporaryDirectory() as root:
            Path(root,'TEST-result.xml').write_text('<testsuite><testcase classname="C" name="a"/><testcase classname="C" name="a"><failure/></testcase></testsuite>')
            with self.assertRaises(ValueError): collect(root)

    def test_workflow_command_escaping(self):
        report={'cases':[{'class':'C%0A','name':'a\n::error::injected','status':'PASS'}]}
        value=next(messages(report))
        self.assertNotIn('\n',value);self.assertIn('%250A',value)

    def test_long_failure_retains_own_frame_and_stays_bounded(self):
        with tempfile.TemporaryDirectory() as root:
            detail='head\n'+'framework frame\n'*500+'at dev.fusion.shell.Example.test(Example.kt:44)\n'+'tail\n'*500
            import xml.etree.ElementTree as ET
            tree=ET.Element('testsuite');case=ET.SubElement(tree,'testcase',classname='C',name='a');ET.SubElement(case,'failure').text=detail
            ET.ElementTree(tree).write(Path(root,'TEST-result.xml'))
            report=collect(root);value=report['cases'][0]['failure_detail']
            self.assertIn('Example.kt:44',value);self.assertLess(len(value.encode('utf-8')),2700)
