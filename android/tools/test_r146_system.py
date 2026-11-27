#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Actual JVM export workflow + independent Python ZIP/manifest validation.

Toolchain absence skips explicitly. No downloaded dependencies at test time.
The full differential is a separate gate: python3 tools/run_r146_checks.py.
"""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import zipfile
from bootstrap_host_tools import environment

ROOT = Path(__file__).resolve().parents[1]


class DiagnosticWorkflow(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cache = Path.home() / '.cache/fusion-host-r23'
        if not (cache / 'host-tools.json').is_file():
            raise unittest.SkipTest('pinned host compiler not installed; no JVM evidence')
        cls.env = environment(cache)
        cls.tmp = tempfile.TemporaryDirectory(prefix='r146-system-')
        cls.addClassCleanup(cls.tmp.cleanup)
        cls.work = Path(cls.tmp.name)
        cls.classes = cls.work / 'classes'
        sources = [
            "core/security/src/main/kotlin/dev/fusion/security/diagnostics/DiagnosticExportAllowlist.kt",
            "core/security/src/main/kotlin/dev/fusion/security/diagnostics/DiagnosticExportSession.kt",
            'privacy/src/main/java/dev/fusion/privacy/DiagnosticExportAllowlist.kt',
            'privacy/src/main/java/dev/fusion/privacy/DiagnosticExportSession.kt',
            'feature/together/src/main/kotlin/dev/fusion/together/EditLogCoalescer.kt',
            'tools/diagnostics/DiagnosticExportCli.kt',
            'testing/src/r146/kotlin/dev/fusion/testing/R146SystemChecks.kt',
        ]
        proc = subprocess.run([cls.env['KOTLINC'], '-jvm-target', '17', '-Werror',
                               '-d', str(cls.classes), *[str(ROOT / p) for p in sources]],
                              env=cls.env, capture_output=True, text=True, timeout=120)
        if proc.returncode:
            raise AssertionError(proc.stdout + proc.stderr)
        stdlib = Path(cls.env['KOTLINC']).parent.parent / 'lib/kotlin-stdlib.jar'
        cls.java = [cls.env['JAVA_HOME'] + '/bin/java', '-Djava.awt.headless=true',
                    '-cp', str(cls.classes) + os.pathsep + str(stdlib)]
        cls.artifacts = cls.work / 'artifacts'
        cls.system = subprocess.run(cls.java + ['dev.fusion.testing.R146SystemChecks', str(cls.artifacts)],
                                    env=cls.env, capture_output=True, text=True, timeout=30)
        if cls.system.returncode:
            raise AssertionError(cls.system.stdout + cls.system.stderr)

    def cli(self, *args):
        return subprocess.run(self.java + ['dev.fusion.tools.DiagnosticExportCli', *map(str, args)],
                              env=self.env, capture_output=True, text=True, timeout=15)

    def source(self, name, text):
        path = self.work / name
        path.write_text(text, encoding='utf-8')
        return path

    def test_kotlin_invariants_executed(self):
        self.assertRegex(self.system.stdout, r'R146_SYSTEM_OK assertions=\d+ real_zip=true stream_failure=true java2d=true')
        self.assertGreaterEqual(int(re.search(r'assertions=(\d+)', self.system.stdout)[1]), 35)

    def test_zip_independent_reader_and_digest(self):
        with zipfile.ZipFile(self.artifacts / 'diagnostics.zip') as z:
            self.assertEqual(z.namelist(), ['diagnostics.txt', 'manifest.txt'])
            self.assertIsNone(z.testzip())
            content = z.read('diagnostics.txt')
            manifest = dict(line.split('=', 1) for line in z.read('manifest.txt').decode().splitlines())
        self.assertEqual(content, (self.artifacts / 'review.txt').read_bytes())
        self.assertEqual(manifest['format'], 'FDE1')
        self.assertEqual(manifest['sha256'], hashlib.sha256(content).hexdigest())
        self.assertEqual(int(manifest['bytes']), len(content))
        self.assertNotIn(b'NEVER_EXPORT_THIS', content)
        self.assertNotIn(b'dev@example.org', content)
        self.assertIn('Not anonymous', manifest['warning'])

    def test_review_is_actual_png_not_android_claim(self):
        import struct
        png = (self.artifacts / 'diagnostic-review.png').read_bytes()
        self.assertEqual(png[:8], b'\x89PNG\r\n\x1a\n')
        self.assertEqual(struct.unpack('>II', png[16:24]), (1100, 650))

    def test_cli_review_then_real_archive(self):
        src = self.source('input.txt', 'version=2.1\nmail=dev@example.org\nprivate=DO_NOT_EXPORT\n')
        preview = self.cli('version,mail', src)
        self.assertEqual(preview.returncode, 0, preview.stderr)
        digest = re.search(r'SHA256=([0-9a-f]{64})', preview.stdout)[1]
        self.assertNotIn('DO_NOT_EXPORT', preview.stdout)
        target = self.work / 'approved.zip'
        exported = self.cli('version,mail', src, digest, target)
        self.assertEqual(exported.returncode, 0, exported.stderr)
        with zipfile.ZipFile(target) as z:
            self.assertEqual(z.read('diagnostics.txt'), b'mail=[email]\nversion=2.1')

    def test_cli_stale_approval_creates_no_file(self):
        src = self.source('stale.txt', 'version=1\n')
        digest = re.search(r'SHA256=([0-9a-f]{64})', self.cli('version', src).stdout)[1]
        src.write_text('version=2\n')
        target = self.work / 'stale.zip'
        result = self.cli('version', src, digest, target)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(target.exists())

    def test_cli_never_overwrites(self):
        src = self.source('overwrite.txt', 'version=1\n')
        digest = re.search(r'SHA256=([0-9a-f]{64})', self.cli('version', src).stdout)[1]
        target = self.source('existing.zip', 'KEEP_EXISTING')
        self.assertNotEqual(self.cli('version', src, digest, target).returncode, 0)
        self.assertEqual(target.read_text(), 'KEEP_EXISTING')

    def test_cli_refuses_symlink_destination(self):
        src = self.source('linkinput.txt', 'version=1\n')
        digest = re.search(r'SHA256=([0-9a-f]{64})', self.cli('version', src).stdout)[1]
        original = self.source('original.txt', 'KEEP')
        link = self.work / 'symlink.zip'
        link.symlink_to(original)
        self.assertNotEqual(self.cli('version', src, digest, link).returncode, 0)
        self.assertEqual(original.read_text(), 'KEEP')

    def test_cli_rejects_malformed_utf8(self):
        src = self.work / 'badutf8.txt'
        src.write_bytes(b'msg=\xff\n')
        self.assertNotEqual(self.cli('msg', src).returncode, 0)

    def test_cli_errors_do_not_echo_secrets(self):
        src = self.source('badrecord.txt', 'INVALID_SECRET_NAME=PRIVATE_VALUE\n')
        result = self.cli('version', src)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('PRIVATE', result.stdout + result.stderr)
        self.assertNotIn('SECRET', result.stdout + result.stderr)

    def test_cli_size_limit_before_parsing(self):
        src = self.source('oversized.txt', 'msg=' + 'x' * 1_100_000)
        result = self.cli('msg', src)
        self.assertNotEqual(result.returncode, 0)
        self.assertLess(len(result.stderr), 300)


if __name__ == '__main__':
    unittest.main()
