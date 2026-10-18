"""Actual command refusal and environment handling, not a fake compiler/Android runtime."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from bootstrap_host_tools import doctor, environment

ROOT = Path(__file__).resolve().parents[1]


class R23LabTests(unittest.TestCase):
    def test_empty_cache_does_not_invent_runtime_or_compiler(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = doctor(Path(tmp))
            self.assertFalse(result['java']); self.assertFalse(result['javac']); self.assertFalse(result['kotlinc'])
            self.assertIn('not JDK', result['scope'])
            before = dict(os.environ)
            env = environment(Path(tmp) / 'path with spaces')
            self.assertIn('path with spaces', env['KOTLINC'])
            self.assertEqual(before, dict(os.environ))

    def test_host_cache_cannot_be_promoted_to_android_jdk(self):
        result = subprocess.run([sys.executable, str(ROOT / 'tools/r23_lab.py'), 'android', '--tools', str(ROOT)], text=True, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('JRE, not a JDK', result.stderr)

    def test_absent_sdk_refuses_without_attempting_a_fake_build(self):
        env = os.environ.copy()
        for key in ['ANDROID_HOME', 'ANDROID_SDK_ROOT']: env.pop(key, None)
        result = subprocess.run([sys.executable, str(ROOT / 'tools/r23_lab.py'), 'android'], env=env, text=True, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Real Android SDK required', result.stderr)

    def test_incomplete_cache_env_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = subprocess.run([sys.executable, str(ROOT / 'tools/bootstrap_host_tools.py'), 'env', '--directory', tmp], text=True, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Cache incomplete', result.stderr)


if __name__ == '__main__': unittest.main()
