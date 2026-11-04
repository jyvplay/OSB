#!/usr/bin/env python3
"""Host-only adversaries for CI SDK preparation; never a real emulator pass."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from types import SimpleNamespace

import r62_install_managed_sdk as sdk


def create_manager(root):
    manager = root / 'cmdline-tools/latest/bin/sdkmanager'
    manager.parent.mkdir(parents=True)
    manager.write_text('#!/bin/sh\nexit 0\n', encoding='utf-8')
    manager.chmod(0o755)
    return manager


def complete(root):
    image = root / 'system-images/android-34/google_apis/x86_64'
    emulator = root / 'emulator'
    image.mkdir(parents=True, exist_ok=True)
    emulator.mkdir(parents=True, exist_ok=True)
    (image / 'package.xml').write_text(f'<sdk><localPackage path="{sdk.IMAGE}"/></sdk>', encoding='utf-8')
    (image / 'system.img').write_bytes(b'fixture, not Android')
    (emulator / 'package.xml').write_text('<sdk><localPackage path="emulator"/></sdk>', encoding='utf-8')
    (emulator / 'emulator').write_text('#!/bin/sh\nexit 0\n', encoding='utf-8')
    (emulator / 'emulator').chmod(0o755)


class ManagedSdkInstallTests(unittest.TestCase):
    def test_preinstalled_does_not_invoke_manager_or_sleep(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); complete(root)
            def forbidden(*args, **kwargs): self.fail('unexpected install or wait')
            self.assertEqual(sdk.ensure(root, forbidden, forbidden), 'already-installed-metadata-only')

    def test_failed_download_then_valid_install_calls_only_sdkmanager(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); manager = create_manager(root); calls = []; pauses = []
            def run(cmd, *, check, timeout):
                calls.append((cmd, check, timeout))
                if len(calls) == 2: complete(root)
                return SimpleNamespace(returncode=1 if len(calls) == 1 else 0)
            self.assertEqual(sdk.ensure(root, run, pauses.append), 'installed-metadata-only')
            self.assertEqual(len(calls), 2)
            self.assertEqual(calls[0], ([str(manager), '--install', 'emulator', sdk.IMAGE], False, 360))
            self.assertEqual(pauses, [10])

    def test_zero_exit_with_missing_image_never_becomes_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); create_manager(root); calls = []; pauses = []
            def run(*args, **kwargs): calls.append(args); return SimpleNamespace(returncode=0)
            with self.assertRaisesRegex(RuntimeError, 'remain incomplete'): sdk.ensure(root, run, pauses.append)
            self.assertEqual(len(calls), 2); self.assertEqual(pauses, [10])

    def test_failed_exit_with_files_does_not_pass_until_later_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); create_manager(root); calls = []
            def run(*args, **kwargs):
                calls.append(1); complete(root)
                return SimpleNamespace(returncode=1 if len(calls) == 1 else 0)
            self.assertEqual(sdk.ensure(root, run, lambda _: None), 'installed-metadata-only')
            self.assertEqual(len(calls), 2)

    def test_timeout_is_bounded_and_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); create_manager(root); calls = []
            def run(cmd, **kwargs): calls.append(cmd); raise subprocess.TimeoutExpired(cmd, 360)
            with self.assertRaisesRegex(RuntimeError, 'timed out.*not retried'): sdk.ensure(root, run, lambda _: None)
            self.assertEqual(len(calls), 1)

    def test_incorrect_manifest_or_missing_executable_is_not_ready(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); complete(root)
            image_manifest = root / 'system-images/android-34/google_apis/x86_64/package.xml'
            image_manifest.write_text('<sdk><localPackage path="wrong"/></sdk>', encoding='utf-8')
            self.assertFalse(sdk.installed(root))
            image_manifest.write_text('<broken', encoding='utf-8')
            self.assertFalse(sdk.installed(root))
            complete(root)
            (root / 'emulator/emulator').chmod(0o644)
            self.assertFalse(sdk.installed(root))

    def test_missing_manager_fails_without_running_anything(self):
        with tempfile.TemporaryDirectory() as tmp:
            def forbidden(*args, **kwargs): self.fail('unexpected process')
            with self.assertRaisesRegex(RuntimeError, 'SDK manager'): sdk.ensure(Path(tmp), forbidden, forbidden)

    def test_workflow_installs_matching_image_before_one_unretried_test(self):
        android = Path(__file__).resolve().parents[1]
        workflow = (android.parent / '.github/workflows/android-r25-media.yml').read_text(encoding='utf-8')
        build = (android / 'app/build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('apiLevel = 34', build)
        self.assertIn('systemImageSource = "google"', build)
        self.assertEqual(workflow.count('tools/r62_install_managed_sdk.py'), 1)
        self.assertLess(workflow.index('tools/r62_install_managed_sdk.py'),
                        workflow.index('tools/run_gradle_ci.sh device'))
        self.assertEqual(workflow.count('tools/run_gradle_ci.sh device'), 1)
        self.assertLess(workflow.index('libpulse0'), workflow.index('tools/run_gradle_ci.sh device'))
        self.assertEqual(workflow.count('tools/r61_emulator_observation.py'), 1)
        observer = workflow.split('- name: Failure-only host observations', 1)[1].split('- name: Actual named outcomes', 1)[0]
        self.assertIn('if: failure()', observer)
        self.assertNotIn('run_gradle_ci.sh', observer)
        self.assertNotIn('continue-on-error', workflow)

    def test_real_subprocess_executes_fake_sdkmanager_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); manager = create_manager(root)
            # The fake program completes local metadata/files; it never contacts a server.
            manager.write_text('#!' + sys.executable + '\n' +
                'from pathlib import Path\nimport os\n' +
                'r=Path(os.environ["ANDROID_HOME"])\n' +
                'i=r/"system-images/android-34/google_apis/x86_64"; i.mkdir(parents=True)\n' +
                '(i/"package.xml").write_text(\'<sdk><localPackage path="system-images;android-34;google_apis;x86_64"/></sdk>\')\n' +
                '(i/"system.img").write_bytes(b"fixture")\n' +
                'e=r/"emulator"; e.mkdir()\n' +
                '(e/"package.xml").write_text(\'<sdk><localPackage path="emulator"/></sdk>\')\n' +
                '(e/"emulator").write_text("#!/bin/sh\\nexit 0\\n"); (e/"emulator").chmod(0o755)\n', encoding='utf-8')
            env = os.environ.copy(); env['ANDROID_HOME'] = str(root)
            result = subprocess.run([sys.executable, str(Path(sdk.__file__))], env=env, text=True,
                                    capture_output=True, timeout=10, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('installed-metadata-only', result.stdout)
            self.assertTrue(sdk.installed(root))


if __name__ == '__main__': unittest.main()
