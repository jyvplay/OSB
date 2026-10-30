"""Synthetic orchestration tests only; these do not install or execute Android SDK software."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'tools/install_r54_sdk.sh'

# This stub lets the tests control missing files, exit codes and an emulator launch failure.
# It is never used by the Android workflow and is not evidence that a real SDK works.
SDK_STUB = r'''#!/usr/bin/env python3
import os
from pathlib import Path
import sys
root = Path(os.environ['ANDROID_HOME'])
mode = os.environ.get('R54_FAKE_MODE', 'ok')
args = sys.argv[1:]
group = 'platform' if 'platform-tools' in args else ('emulator' if 'emulator' in args else 'image')
calls = root / (group + '.calls')
n = int(calls.read_text()) + 1 if calls.exists() else 1
calls.write_text(str(n))
def put(path, content='', executable=False):
    p = root / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
    if executable: p.chmod(0o755)
if group == 'platform':
    put('platforms/android-37.0/android.jar')
    put('build-tools/36.0.0/aapt2', executable=True)
    put('platform-tools/adb', executable=True)
elif group == 'emulator' and not (mode == 'flaky-emulator' and n == 1):
    exit_code = 127 if mode == 'broken-executable' else 0
    put('emulator/emulator', '#!/usr/bin/env bash\necho synthetic-emulator-version-or-error\nexit ' + str(exit_code) + '\n', True)
elif group == 'image' and mode != 'missing-image':
    put('system-images/android-34/google_apis/x86_64/system.img')
if mode == 'nonzero-platform' and group == 'platform':
    print('e: synthetic error %0A\n::notice::not-a-command')
    sys.exit(9)
print('synthetic sdkmanager ' + group)
'''


class SdkInstallationOrchestration(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'sdk with spaces'
        sdk = self.root / 'cmdline-tools/latest/bin/sdkmanager'
        sdk.parent.mkdir(parents=True)
        sdk.write_text(SDK_STUB)
        sdk.chmod(0o755)
        self.log = Path(self.temp.name) / 'setup.log'
        self.env = dict(os.environ, ANDROID_HOME=str(self.root), R54_SDK_LOG=str(self.log),
                        R54_SDK_ATTEMPTS='2', R54_SDK_RETRY_DELAY='0')

    def run_setup(self, mode='ok'):
        return subprocess.run(['bash', str(SCRIPT)], env=dict(self.env, R54_FAKE_MODE=mode),
                              text=True, capture_output=True, timeout=30)

    def calls(self, name):
        p = self.root / (name + '.calls')
        return int(p.read_text()) if p.exists() else 0

    def test_success_checks_three_groups_but_claims_no_boot(self):
        r = self.run_setup()
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual([self.calls(x) for x in ('platform', 'emulator', 'image')], [1, 1, 1])
        self.assertIn('emulator boot and device tests remain separate gates', r.stdout)
        self.assertIn('synthetic-emulator-version-or-error', self.log.read_text())

    def test_sdk_zero_with_missing_emulator_retries_then_checks(self):
        r = self.run_setup('flaky-emulator')
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.calls('emulator'), 2)
        self.assertIn('emulator executable is missing', self.log.read_text())
        self.assertIn('Emulator packages passed', self.log.read_text())

    def test_sdk_zero_with_missing_image_fails_closed(self):
        r = self.run_setup('missing-image')
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(self.calls('image'), 2)
        self.assertIn('API 34 system image components were not verified', r.stdout)
        self.assertNotIn('device tests remain separate gates', r.stdout)

    def test_failed_emulator_version_is_recorded_and_never_claimed_as_success(self):
        r = self.run_setup('broken-executable')
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(self.calls('emulator'), 2)
        self.assertEqual(self.calls('image'), 0)
        self.assertIn('emulator -version exited 127', self.log.read_text())
        self.assertIn('emulator -version exited 127', r.stdout)
        self.assertIn('synthetic-emulator-version-or-error', self.log.read_text())

    def test_nonzero_sdk_exit_refused_even_if_artifacts_exist(self):
        r = self.run_setup('nonzero-platform')
        self.assertNotEqual(r.returncode, 0)
        self.assertEqual(self.calls('platform'), 2)
        self.assertEqual(self.calls('emulator'), 0)
        self.assertIn('sdkmanager exit: 9', self.log.read_text())
        self.assertIn('%250A', r.stdout)  # untrusted annotation data is escaped
        self.assertFalse(any(line.startswith('::notice::not-a-command') for line in r.stdout.splitlines()))

    def test_missing_sdkmanager_is_a_failure(self):
        (self.root / 'cmdline-tools/latest/bin/sdkmanager').unlink()
        r = self.run_setup()
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('sdkmanager is missing or not executable', r.stdout)
        self.assertEqual(self.calls('platform'), 0)

    def test_bad_retry_configuration_fails_without_running_sdkmanager(self):
        self.env['R54_SDK_ATTEMPTS'] = '999'
        r = self.run_setup()
        self.assertEqual(r.returncode, 2)
        self.assertEqual(self.calls('platform'), 0)

    def test_missing_sdk_root_fails_before_running_sdkmanager(self):
        self.env.pop('ANDROID_HOME')
        r = self.run_setup()
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('ANDROID_HOME', r.stderr)
        self.assertEqual(self.calls('platform'), 0)


if __name__ == '__main__':
    unittest.main()
