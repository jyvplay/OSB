"""Explicit parser fixtures, never claimed as device outcomes."""
import base64,contextlib,gzip,hashlib,io,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import xml.etree.ElementTree as ET
from collect_r16_measurements import decode
import export_r16_measurements as exporter
from focus_outcome import classify
class EvidenceTests(unittest.TestCase):
    def annotations(self,data):
        with tempfile.TemporaryDirectory() as d,patch.object(exporter,'ROOT',Path(d)),contextlib.redirect_stdout(io.StringIO()) as out:
            exporter.emit(data)
        return [{'message':s.split('::',2)[2]} for s in out.getvalue().splitlines()]
    def test_api_limit_and_roundtrip(self):
        data={'fixture':[hashlib.sha256(str(i).encode()).hexdigest() for i in range(1000)]}
        a=self.annotations(data);self.assertGreater(len(a),1)
        self.assertTrue(all(len(x['message'])<=4096 for x in a))
        self.assertEqual(data,decode(a[::-1]))
    def test_truncation_refused(self):
        a=self.annotations({'fixture':'abcdef'*1000});a[0]['message']=a[0]['message'][:-12]
        with self.assertRaises(Exception):decode(a)
    def test_duplicate_refused(self):
        a=self.annotations({'fixture':True})
        with self.assertRaises(ValueError):decode(a+a)
    def test_missing_refused(self):
        with self.assertRaises(ValueError):decode([])
    def test_hash_refused(self):
        a=self.annotations({'fixture':True});a[0]['message']=a[0]['message'].replace(a[0]['message'].split()[2],'0'*64)
        with self.assertRaises(ValueError):decode(a)
    def test_profile_hash_refused(self):
        a=self.annotations({'profile':'fixture','profile-generation.json':{'sha256':'0'*64}})
        with self.assertRaises(ValueError):decode(a)
    def test_actual_historical_classifications(self):
        fail=ET.fromstring('<testcase><failure message="Expected Focused"/></testcase>')
        passed=ET.fromstring('<testcase/>');skip=ET.fromstring('<testcase><skipped/></testcase>')
        self.assertEqual('REPRODUCED',classify(1,[fail])[0])
        self.assertEqual('NOT_REPRODUCED',classify(0,[passed])[0])
        for exit_code,cases in [(1,[]),(0,[]),(1,[passed]),(0,[skip]),(0,[passed,passed]),(0,[fail])]:
            self.assertEqual('INVALID_EXPERIMENT',classify(exit_code,cases)[0])
