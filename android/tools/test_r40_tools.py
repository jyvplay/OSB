#!/usr/bin/env python3
"""Hygiene and pin tests for the R40 round artifacts. Host-only."""
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PASS = 0


RESULTS = []


def check(name, cond):
    global PASS
    RESULTS.append((name, bool(cond)))
    if not cond:
        raise SystemExit(f'FAIL: {name}')
    PASS += 1


KERNELS = {
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/NumFmtSections.kt': 'object NumFmtSections',
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt': 'object CellErr',
    'core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt': 'object DashEducate',
    'core/documents/src/main/kotlin/dev/fusion/documents/ObjReplace.kt': 'object ObjReplace',
    'feature/editor/src/main/java/dev/fusion/editor/FootnoteMark.kt': 'object FootnoteMark',
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfRotate.kt': 'object PdfRotate',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/HtmlEntity.kt': 'object HtmlEntity',
    'feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2047.kt': 'object Rfc2047',
    'format/archive/src/main/kotlin/dev/fusion/archive/SevenZHead.kt': 'object SevenZHead',
    'core/index/src/main/kotlin/dev/fusion/index/Fnv1a.kt': 'object Fnv1a',
    'feature/lens/src/main/kotlin/dev/fusion/lens/Mrz.kt': 'object Mrz',
    'core/files/src/main/java/dev/fusion/files/SafeName.kt': 'object SafeName',
    'core/perf/src/main/kotlin/dev/fusion/perf/Percentile.kt': 'object Percentile',
    'feature/notes/src/main/kotlin/dev/fusion/notes/TaskCheckbox.kt': 'object TaskCheckbox',
    'core/windowing/src/main/kotlin/dev/fusion/windowing/SnapFraction.kt': 'object SnapFraction',
    'cast/airplay/src/main/kotlin/dev/fusion/airplay/PlistHead.kt': 'object PlistHead',
    'feature/launcher/src/main/kotlin/dev/fusion/launcher/Monogram.kt': 'object Monogram',
    'feature/vault/src/main/kotlin/dev/fusion/vault/Bech32.kt': 'object Bech32',
    'core/security/src/main/kotlin/dev/fusion/security/OtpauthUri.kt': 'object OtpauthUri',
    'privacy/src/main/java/dev/fusion/privacy/IpRedact.kt': 'object IpRedact',
}

FIXTURES = {
    'tools/fixtures/r40/sevenz.bin': 'da128e29c7fb1b8a0cad6a4fe922530aebe8422f20e4aca1c61ec1c90a3986ac',
    'tools/fixtures/r40/bplist.bin': '05aff162ffe644d97a4477b9ef0f34f80c7ed46e886126827c6abbd794413d2c',
}


def main():
    texts = {rel: (ROOT / rel).read_text() for rel in KERNELS}

    check('all 20 kernels exist with declared objects',
          all(decl in texts[rel] for rel, decl in KERNELS.items()))
    check('no android imports in any kernel',
          all('import android' not in t and 'import androidx' not in t for t in texts.values()))
    check('no file IO inside kernels',
          all('java.io.File' not in t for t in texts.values()))
    check('every kernel declares a SCOPE honesty constant',
          all('const val SCOPE' in t for t in texts.values()))
    check('no TODO/FIXME left in kernels (VTODO the iCalendar component is not a marker)',
          all(not re.search(r'(?<!V)TODO', t) and 'FIXME' not in t for t in texts.values()))
    total_fns = {'core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt'}
    check('kernels refuse loudly (require or explicit IAE; DashEducate is a declared total transform)',
          all(('require(' in t or 'IllegalArgumentException' in t)
              for rel, t in texts.items() if rel not in total_fns))

    for rel, sha in FIXTURES.items():
        data = (ROOT / rel).read_bytes()
        check(f'fixture pinned: {Path(rel).name}', hashlib.sha256(data).hexdigest() == sha)
    check('sevenz fixture is exactly the 32-byte signature header',
          len((ROOT / 'tools/fixtures/r40/sevenz.bin').read_bytes()) == 32)
    check('bplist fixture came from plistlib (header + 90 bytes)',
          (ROOT / 'tools/fixtures/r40/bplist.bin').read_bytes()[:8] == b'bplist00')

    checks = (ROOT / 'testing/src/r40/kotlin/dev/fusion/testing/R40Checks.kt').read_text()
    check('consumer counts assertions through ok()', 'count++' in checks)
    check('consumer declares 20 kernels in its banner', 'across 20 kernels' in checks)
    check('consumer never claims device evidence', 'not Android/device evidence' in checks)
    runner = (ROOT / 'tools/run_r40_contracts.py').read_text()
    check('runner compiles kernels in isolation', "'-include-runtime'" in runner)
    groups = re.findall(r"^\s{4}'([a-z0-9-]+)':", runner, re.M)
    check('runner has 20 single-kernel groups', len(groups) == 20 and len(set(groups)) == 20)

    ref = (ROOT / 'tools/check_r40_reference.py').read_text()
    check('reference mirrors all 20 kernels', 'len(CHECKS)' in ref and ref.count('def ') >= 21)
    for held_out in ('html.unescape', 'decode_header', 'zlib.crc32', 'plistlib.loads', 'ipaddress.ip_network', 'parse_qsl'):
        check(f'held-out verifier present: {held_out}', held_out in ref)
    check('no tautological assertions in mirrors', 'or True' not in ref)

    # Honesty greps
    check('Fnv1a states non-cryptographic', 'Non-cryptographic' in texts['core/index/src/main/kotlin/dev/fusion/index/Fnv1a.kt'])
    check('Mrz states no identity claims', 'no identity claims' in texts['feature/lens/src/main/kotlin/dev/fusion/lens/Mrz.kt'])
    check('OtpauthUri never computes codes', 'NEVER COMPUTES CODES' in texts['core/security/src/main/kotlin/dev/fusion/security/OtpauthUri.kt'])
    check('IpRedact states not-anonymity', 'NOT anonymity' in texts['privacy/src/main/java/dev/fusion/privacy/IpRedact.kt'])
    check('Bech32 states no wallet semantics', 'no wallet semantics' in texts['feature/vault/src/main/kotlin/dev/fusion/vault/Bech32.kt'])
    check('HtmlEntity declares the C1 narrowing', 'cp1252' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/HtmlEntity.kt'])
    check('Percentile repair comment survives (FP multiply-first)',
          'Multiply BEFORE dividing' in texts['core/perf/src/main/kotlin/dev/fusion/perf/Percentile.kt'])

    # Spec pins
    check('bech32 generator constants pinned', '0x2BC830A3' in texts['feature/vault/src/main/kotlin/dev/fusion/vault/Bech32.kt'])
    check('FNV primes pinned', '16777619' in texts['core/index/src/main/kotlin/dev/fusion/index/Fnv1a.kt'] and
          '1099511628211' in texts['core/index/src/main/kotlin/dev/fusion/index/Fnv1a.kt'])
    check('7z magic pinned', '0x37, 0x7A' in texts['format/archive/src/main/kotlin/dev/fusion/archive/SevenZHead.kt'])
    check('MRZ weights pinned', 'intArrayOf(7, 3, 1)' in texts['feature/lens/src/main/kotlin/dev/fusion/lens/Mrz.kt'])
    check('CMOS symbol order pinned', '\\u2020' in texts['feature/editor/src/main/java/dev/fusion/editor/FootnoteMark.kt'])

    # Gradle CI wiring
    gradle = (ROOT / 'testing/build.gradle.kts').read_text()
    check('r39Contracts Gradle task registered', 'r39Contracts' in gradle and 'R39Checks' in gradle)
    check('r40Contracts Gradle task registered', 'r40Contracts' in gradle and 'R40Checks' in gradle)
    wf = (ROOT.parent / '.github/workflows/android.yml').read_text()
    check('JVM lane runs both new contract tasks',
          ':testing:r39Contracts' in wf and ':testing:r40Contracts' in wf)

    pass


main()   # collect RESULTS at import so unittest sees one test per check

import unittest


class ToolChecks(unittest.TestCase):
    pass


for _i, (_name, _ok) in enumerate(RESULTS):
    def _m(self, _ok=_ok, _name=_name):
        self.assertTrue(_ok, _name)
    _safe = ''.join(c if c.isalnum() else '_' for c in _name)[:60]
    setattr(ToolChecks, '_'.join(['test', '%02d' % _i, _safe]), _m)


if __name__ == '__main__':
    unittest.main(verbosity=1)
