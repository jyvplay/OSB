"""Real refusal path only; do NOT fabricate an SDK jar/source.properties as a fake installation."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from record_sdk_platform import record, base_release37


class InstalledSdkTests(unittest.TestCase):
    def test_exact_semantically_equivalent_base_release_only(self):
        self.assertTrue(base_release37(['37']));self.assertTrue(base_release37(['37.0']))
        for bad in [[], ['37','37'], ['37.1'], ['37.2-beta1'], ['370'], ['37.00'], ['37.0canary'], ['36']]:
            self.assertFalse(base_release37(bad))

    def test_actual_absent_platform_refuses_without_manufacturing_sdk_or_report(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);report=root/'report.json'
            with self.assertRaises(FileNotFoundError): record(root)
            result=subprocess.run([sys.executable,str(Path(__file__).with_name('record_sdk_platform.py')),
                                   '--sdk',str(root),'--report',str(report)],capture_output=True,text=True,check=False)
            self.assertNotEqual(0,result.returncode)
            self.assertIn('REFUSED',result.stderr)
            self.assertFalse((root/'platforms').exists());self.assertFalse(report.exists())


if __name__=='__main__':unittest.main()
