"""Real short-lived Python subprocesses, not fake ADB or Android results."""
import subprocess
import sys
import unittest
from bounded_process import capture


class BoundedProcessChecks(unittest.TestCase):
    def test_success_exact_byte_boundary_and_stderr(self):
        command=[sys.executable,'-c',"import sys;sys.stdout.buffer.write(b'x'*4096);sys.stderr.write('diagnostic')"]
        self.assertEqual(b'x'*4096,capture(command,max_output=4096))

    def test_overflow_both_pipes(self):
        for stream,limits in [('stdout',{'max_output':1024}),('stderr',{'max_error':1024})]:
            command=[sys.executable,'-c',f"import sys;sys.{stream}.buffer.write(b'x'*1048576)"]
            with self.assertRaisesRegex(RuntimeError,'budget'): capture(command,**limits)

    def test_nonzero_timeout_and_bad_limits(self):
        with self.assertRaisesRegex(RuntimeError,'failed'):
            capture([sys.executable,'-c',"import sys;sys.stderr.write('refusal');sys.exit(7)"])
        with self.assertRaises(subprocess.TimeoutExpired):
            capture([sys.executable,'-c','import time;time.sleep(10)'],timeout=0.1)
        with self.assertRaises(ValueError): capture([sys.executable],max_output=0)
