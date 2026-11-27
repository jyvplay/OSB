#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Actual Kotlin/JVM protocol, file artifacts, independent held-out math/ZIP tests.

No Android runtime is fabricated. Optional platform API compile is a separate
command (run_r176_checks.py --android-jar), not a device execution claim.
"""
from decimal import Decimal, Context, localcontext, ROUND_HALF_EVEN
import hashlib
import itertools
import json
import os
from pathlib import Path
import random
import re
import subprocess
import tempfile
import unittest
import zipfile

from bootstrap_host_tools import environment

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    'core/security/src/main/kotlin/dev/fusion/security/diagnostics/DiagnosticExportAllowlist.kt',
    'core/security/src/main/kotlin/dev/fusion/security/diagnostics/DiagnosticExportSession.kt',
    'core/security/src/main/kotlin/dev/fusion/security/diagnostics/DiagnosticSaveWorkflow.kt',
    'core/security/src/main/kotlin/dev/fusion/security/diagnostics/DiagnosticArchiveInspector.kt',
    'core/model/src/main/kotlin/dev/fusion/model/SemVer.kt',
    'core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt',
    'core/settings/src/main/kotlin/dev/fusion/settings/SettingsSnapshot.kt',
    'core/settings/src/main/kotlin/dev/fusion/settings/SettingsCatalog.kt',
    *['feature/calc/src/main/kotlin/dev/fusion/calc/' + n + '.kt' for n in
      ('Calculator', 'DecimalMath', 'Programmer', 'Units', 'UnitConversion')],
    'testing/src/r176/kotlin/dev/fusion/testing/R176Checks.kt',
]
WARNING = 'Heuristic redaction only. Review for secrets before sharing. Not anonymous.'


def manifest(content):
    return ('format=FDE1\nsha256=' + hashlib.sha256(content).hexdigest() +
            '\nbytes=' + str(len(content)) + '\nwarning=' + WARNING + '\n').encode()


def archive(path, content=b'app.version=2.1', metadata=None, extra=None, names=None):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('diagnostics.txt' if names is None else names[0], content)
        z.writestr('manifest.txt' if names is None else names[1], manifest(content) if metadata is None else metadata)
        if extra:
            z.writestr(extra, b'unexpected')


def expected_range(version, expr):
    match = re.fullmatch(r'(>=|<=|==|!=|>|<|\^|~)([0-9]+)\.([0-9]+)\.([0-9]+)', expr)
    if not match or len(expr) > 64:
        return 'REF'
    op, *parts = match.groups()
    if any(len(x) > 1 and x.startswith('0') for x in parts):
        return 'REF'
    base = tuple(map(int, parts))
    if any(x > 2**63 - 1 for x in base):
        return 'REF'
    if '-' in version:
        return 'false'
    v = tuple(map(int, version.split('+')[0].split('.')))
    if op in ('^', '~'):
        if op == '~':
            upper = (base[0], base[1] + 1, 0)
        elif base[0]:
            upper = (base[0] + 1, 0, 0)
        elif base[1]:
            upper = (0, base[1] + 1, 0)
        else:
            upper = (0, 0, base[2] + 1)
        if max(upper) > 2**63 - 1:
            return 'REF'
        result = base <= v < upper
    else:
        result = {'>=': v >= base, '<=': v <= base, '==': v == base,
                  '!=': v != base, '>': v > base, '<': v < base}[op]
    return str(result).lower()


class R176Execution(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        tools = Path.home() / '.cache/fusion-host-r23'
        if not (tools / 'host-tools.json').is_file():
            raise unittest.SkipTest('pinned JVM toolchain absent; install explicitly, no execution claimed')
        cls.env = environment(tools)
        cls.temp = tempfile.TemporaryDirectory(prefix='r176-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.dir = Path(cls.temp.name)
        cls.classes = cls.dir / 'classes'
        proc = subprocess.run([cls.env['KOTLINC'], '-jvm-target', '17', '-Werror', '-d', str(cls.classes),
                               *[str(ROOT / p) for p in SOURCES]], env=cls.env, capture_output=True, text=True, timeout=120)
        if proc.returncode:
            raise AssertionError(proc.stdout + proc.stderr)
        stdlib = tools / 'node_modules/kotlin-compiler/lib/kotlin-stdlib.jar'
        cls.java = [cls.env['JAVA_HOME'] + '/bin/java', '-cp', str(cls.classes) + os.pathsep + str(stdlib),
                    'dev.fusion.testing.R176Checks', str(cls.dir)]
        result = cls.run_java()
        cls.stdout = result.stdout
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    @classmethod
    def run_java(cls, *args):
        return subprocess.run(cls.java + list(args), env=cls.env, capture_output=True, text=True, timeout=30)

    def test_real_protocol_threads_and_affected_apis(self):
        self.assertRegex(self.stdout, r'R176_SYSTEM_OK assertions=84 real_threads=true real_files=true')

    def test_real_export_zip_independent_reader(self):
        with zipfile.ZipFile(self.dir / 'saved.zip') as z:
            self.assertEqual(z.namelist(), ['diagnostics.txt', 'manifest.txt'])
            self.assertIsNone(z.testzip())
            content = z.read('diagnostics.txt')
            self.assertEqual(z.read('manifest.txt'), manifest(content))
        self.assertEqual(content, b'app.version=2.1\noperator.note=[email]')
        self.assertEqual(hashlib.sha256(content).hexdigest(), (self.dir / 'export.sha256').read_text())
        self.assertNotIn(b'dev@example.org', content)

    def test_held_out_ranges_against_integer_tuple_reference(self):
        rng = random.Random(176)
        versions = ['%d.%d.%d' % (rng.randrange(4), rng.randrange(6), rng.randrange(12)) for _ in range(32)]
        bases = ['0.0.3', '0.2.3', '1.2.3', '2.0.0']
        cases = [(v, op + base) for v, op, base in itertools.product(versions, ['^', '~', '>=', '<=', '>', '<', '==', '!='], bases)]
        cases += [('1.2.3', expr) for expr in ['', '^', '~', '1.2.3', '^01.2.3', '^1.x.0',
                  ' ^1.2.3', '^1.2.3 ', '^1.2.3 || ^2.0.0', '^1.2.3-rc.1',
                  '^9223372036854775807.0.0', '~0.9223372036854775807.0']]
        cases += [('1.2.5-rc.1', '^1.2.3'), ('1.2.2', '^1.2.3'), ('0.0.4', '~0.0.3')]
        (self.dir / 'range-input.tsv').write_text(''.join(v + '\t' + expr + '\n' for v, expr in cases))
        result = self.run_java('ranges')
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = result.stdout.splitlines()
        self.assertEqual(len(lines), len(cases))
        for i, ((version, expr), line) in enumerate(zip(cases, lines)):
            with self.subTest(version=version, expr=expr):
                self.assertEqual(line, str(i) + '\t' + expected_range(version, expr))
        self.assertEqual(len(cases), 1039)

    def test_held_out_decimal_results_against_cpython_decimal(self):
        rng = random.Random(17609)
        cases = []
        for operation in ['add', 'subtract', 'multiply', 'divide']:
            for _ in range(40):
                a = str(Decimal(rng.randrange(-10**12, 10**12)).scaleb(-rng.randrange(8)))
                b = str(Decimal(rng.randrange(1, 10**10)).scaleb(-rng.randrange(6)))
                cases.append((operation, a, b))
        cases += [('add', '0.1', '0.2'), ('divide', '1', '3'), ('divide', '1', '0'),
                  ('add', '0e-2147483647', '1'), ('multiply', '1e1000000', '0'),
                  ('add', '1' * 513, '1'), ('add', 'NaN', '1'), ('add', '1e1000', '1'),
                  ('multiply', '1e1000', '10')]
        (self.dir / 'decimal-input.tsv').write_text(''.join('\t'.join(c) + '\n' for c in cases))
        result = self.run_java('decimals')
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = result.stdout.splitlines()
        self.assertEqual(len(lines), len(cases))
        with localcontext(Context(prec=34, rounding=ROUND_HALF_EVEN)):
            for i, ((operation, a, b), line) in enumerate(zip(cases, lines)):
                _, actual = line.split('\t')
                with self.subTest(operation=operation, a=a, b=b):
                    if i in (162, 163, 164, 165, 166, 168):
                        self.assertEqual(actual, 'REF')
                    else:
                        expected = getattr(Decimal(a), {'add': '__add__', 'subtract': '__sub__',
                            'multiply': '__mul__', 'divide': '__truediv__'}[operation])(Decimal(b))
                        self.assertNotEqual(actual, 'REF')
                        self.assertEqual(Decimal(actual), expected)
        self.assertEqual(len(cases), 169)

    def test_archive_inspector_independent_positive_and_hostile_archives(self):
        corpus = []
        def add(name, text=b'app.version=2.1', ok=False, **kwargs):
            path = self.dir / (name + '.zip')
            archive(path, text, **kwargs)
            corpus.append((path.name, ok))
            return path
        add('python-valid', ok=True)
        add('python-empty', text=b'', ok=True)
        add('python-utf8', text='note=hello 😀'.encode(), ok=True)
        add('python-two', text=b'app.version=1\nnote=hello', ok=True)
        add('wrong-hash', metadata=manifest(b'different'))
        add('no-manifest', names=('diagnostics.txt', 'elsewhere.txt'))
        add('traversal', names=('../diagnostics.txt', 'manifest.txt'))
        add('extra', extra='unexpected.txt')
        add('reordered-records', text=b'z=last\na=first')
        add('duplicate-records', text=b'note=a\nnote=b')
        add('invalid-key', text=b'BAD=secret')
        add('overlong-value', text=b'note=' + b'x' * 1025)
        add('over-many-records', text='\n'.join('k%03d=x' % i for i in range(257)).encode())
        add('over-expanded', text=b'note=' + b'x' * 1_100_000)
        add('invalid-utf8', text=b'note=\xff')
        for i, value in enumerate(['a\rb', 'a\x00b', 'a\x1bb', 'a\u0085b', 'a\u2028b', 'a\u202eb', 'a\u2066b']):
            add('control-%d' % i, text=('note=' + value).encode())
        appended = add('appended'); appended.write_bytes(appended.read_bytes() + b'junk')
        truncated = add('truncated'); truncated.write_bytes(truncated.read_bytes()[:-10])
        comment = add('comment')
        with zipfile.ZipFile(comment, 'a') as z:
            z.comment = b'not in contract'
        wrong_length = add('wrong-byte-count', metadata=manifest(b'app.version=2.1').replace(b'bytes=15', b'bytes=99'))
        add('unknown-version', metadata=manifest(b'app.version=2.1').replace(b'FDE1', b'FDE2'))
        (self.dir / 'archives.txt').write_text(''.join(name + '\n' for name, _ in corpus))
        result = self.run_java('inspect')
        self.assertEqual(result.returncode, 0, result.stderr)
        actual = dict(line.split('\t') for line in result.stdout.splitlines())
        self.assertEqual(len(actual), len(corpus))
        for name, ok in corpus:
            with self.subTest(archive=name):
                self.assertEqual(actual[name].startswith('OK:'), ok, actual[name])
        self.assertEqual(len(corpus), 27)


class R176SourceBoundaries(unittest.TestCase):
    """Static contracts only. Runtime UI/provider tests are separate and unrun here."""
    def test_internal_activity_no_new_permissions(self):
        import xml.etree.ElementTree as ET
        namespace = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        activity = next(x for x in manifest.findall('application/activity')
                        if x.get(namespace + 'name') == '.DiagnosticExportActivity')
        self.assertEqual(activity.get(namespace + 'exported'), 'false')
        self.assertIsNone(activity.find('intent-filter'))
        self.assertEqual({x.get(namespace + 'name') for x in manifest.findall('uses-permission')},
                         {'android.permission.INTERNET', 'android.permission.READ_MEDIA_IMAGES'})

    def test_old_unreviewed_share_path_is_not_used(self):
        main = (ROOT / 'app/src/main/java/dev/fusion/shell/MainActivity.kt').read_text()
        block = main.split('onShareReport = {', 1)[1].split('\n    @Composable', 1)[0]
        self.assertIn('DiagnosticExportActivity::class.java', block)
        self.assertNotIn('Intent.EXTRA_TEXT', block)
        self.assertNotIn('Intent.ACTION_SEND', block)

    def test_no_scratchpad_export_route_added(self):
        manifest = (ROOT / 'privacy/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('DiagnosticExportActivity', manifest)
        self.assertNotIn('android.permission.INTERNET"', manifest)
        activity = (ROOT / 'app/src/main/java/dev/fusion/shell/DiagnosticExportActivity.kt').read_text()
        self.assertNotIn('getSharedPreferences', activity)
        self.assertNotIn('getClip', activity)
        self.assertNotIn('Intent.EXTRA_TEXT', activity)
        self.assertIn('FLAG_SECURE', activity)
        self.assertIn('No matching live review. Destination not opened.', activity)

    def test_legacy_forecast_gate_distinguishes_helper_prefixes_and_duplicates(self):
        import check_r119_legacy_stats as gate
        source = (ROOT / gate.SOURCE).read_text()
        spill = (ROOT / gate.SPILL).read_text()
        self.assertEqual(gate.registered(source, spill), [])
        self.assertEqual(gate.registered(source + '\nprivate fun evalForecastExtra() {}', spill), [])
        duplicated = gate.registered(source + '\nprivate fun evalForecast() {}', spill)
        self.assertTrue(any('not defined exactly once' in text for text in duplicated))

    def test_diagnostic_manifest_scope_control_rejects_exposure(self):
        import check_r45_manifest_scope as gate
        with tempfile.TemporaryDirectory(prefix='r176-manifest-') as temp:
            root = Path(temp)
            for relative in ('app/src/main/AndroidManifest.xml', 'privacy/src/main/AndroidManifest.xml'):
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text((ROOT / relative).read_text())
            self.assertEqual(gate.check_manifests(root)['global_forbidden_violations'], 0)
            path = root / 'app/src/main/AndroidManifest.xml'
            original = path.read_text()
            for before, after in [('android:name=".DiagnosticExportActivity" android:exported="false"',
                                   'android:name=".DiagnosticExportActivity" android:exported="true"'),
                                  ('android:excludeFromRecents="true"', 'android:excludeFromRecents="false"')]:
                path.write_text(original.replace(before, after))
                with self.assertRaises(SystemExit):
                    gate.check_manifests(root)

    def test_registration_remains_rabbit_compatible(self):
        import check_r104_studio
        # This gate is configuration-only; do not equate it with an APK build.
        result = subprocess.run(['python3', str(ROOT / 'tools/check_r104_studio.py'), '--quiet'],
                                capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
