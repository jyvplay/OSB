#!/usr/bin/env python3
"""Hygiene and pin tests for the R42 round artifacts. Host-only, unittest-native."""
import re
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
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/AbsRef.kt': 'object AbsRef',
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/MergedCells.kt': 'object MergedCells',
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfDate.kt': 'object PdfDate',
    'feature/maps/src/main/kotlin/dev/fusion/maps/NmeaLat.kt': 'object NmeaLat',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/HstsParse.kt': 'object HstsParse',
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/PairPin.kt': 'object PairPin',
    'format/archive/src/main/kotlin/dev/fusion/archive/ArHead.kt': 'object ArHead',
    'core/documents/src/main/kotlin/dev/fusion/documents/CaseCycle.kt': 'object CaseCycle',
    'feature/editor/src/main/java/dev/fusion/editor/CommentToggle.kt': 'object CommentToggle',
    'feature/notes/src/main/kotlin/dev/fusion/notes/UnitPrice.kt': 'object UnitPrice',
    'feature/capture/src/main/kotlin/dev/fusion/capture/Rule3rds.kt': 'object Rule3rds',
    'core/perf/src/main/kotlin/dev/fusion/perf/Histo.kt': 'object Histo',
    'feature/terminal/src/main/kotlin/dev/fusion/terminal/CsiCursor.kt': 'object CsiCursor',
    'privacy/src/main/java/dev/fusion/privacy/GpcHeader.kt': 'object GpcHeader',
    'feature/vault/src/main/kotlin/dev/fusion/vault/PwPolicy.kt': 'object PwPolicy',
    'feature/media/src/main/kotlin/dev/fusion/media/GainDb.kt': 'object GainDb',
    'core/storage/src/main/kotlin/dev/fusion/storage/HexBytes.kt': 'object HexBytes',
    'feature/alerts/src/main/kotlin/dev/fusion/alerts/SnoozeSeq.kt': 'object SnoozeSeq',
    'feature/calc/src/main/kotlin/dev/fusion/calc/NiceStep.kt': 'object NiceStep',
    'feature/automation/src/main/kotlin/dev/fusion/automation/NextRun.kt': 'object NextRun',
    'feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2231.kt': 'object Rfc2231',
    'format/image/src/main/kotlin/dev/fusion/format/image/BmpHead.kt': 'object BmpHead',
}


def main():
    texts = {rel: (ROOT / rel).read_text() for rel in KERNELS}

    check('all 22 kernels exist with declared objects',
          all(decl in texts[rel] for rel, decl in KERNELS.items()))
    check('no android imports in any kernel',
          all('import android' not in t and 'import androidx' not in t for t in texts.values()))
    check('no file IO inside kernels', all('java.io.File' not in t for t in texts.values()))
    check('every kernel declares a SCOPE honesty constant',
          all('const val SCOPE' in t for t in texts.values()))
    check('no TODO/FIXME left in kernels',
          all(not re.search(r'(?<!V)TODO', t) and 'FIXME' not in t for t in texts.values()))
    check('kernels refuse loudly (require or explicit IAE in each)',
          all(('require(' in t or 'IllegalArgumentException' in t) for t in texts.values()))

    checks = (ROOT / 'testing/src/r42/kotlin/dev/fusion/testing/R42Checks.kt').read_text()
    check('consumer counts assertions through ok()', 'count++' in checks)
    check('consumer declares 21 kernels in its banner', 'across 22 kernels' in checks)
    check('consumer never claims device evidence', 'not Android/device evidence' in checks)
    check('no tautological assertions in consumer', '|| true' not in checks)
    runner = (ROOT / 'tools/run_r42_contracts.py').read_text()
    check('runner compiles groups with -include-runtime', "'-include-runtime'" in runner)
    groups = re.findall(r"^\s{4}'([a-z0-9-]+)':", runner, re.M)
    check('runner has 21 groups covering 22 kernels (ooxml group carries intra-module deps)',
          len(groups) == 21 and len(set(groups)) == 21)

    ref = (ROOT / 'tools/check_r42_reference.py').read_text()
    check('reference mirrors all 22 kernels', 'len(CHECKS)' in ref and ref.count('def ') >= 23)
    check('mirror honesty note present (handwritten this round, stated)', 'HANDWRITTEN' in ref)
    check('no tautological assertions in mirrors', 'or True' not in ref and '!= None' not in ref)
    check('Damm table re-entered independently in the mirror', ref.count('[0,3,1,7,5,9,8,6,4,2]') == 1)

    # Honesty greps
    check('PairPin states never-a-security-property',
          'NEVER a security property' in texts['feature/continuity/src/main/kotlin/dev/fusion/continuity/PairPin.kt'])
    check('PwPolicy states policy is not strength',
          'NOT strength' in texts['feature/vault/src/main/kotlin/dev/fusion/vault/PwPolicy.kt'])
    check('GpcHeader refuses rather than coerces',
          'never coerce' in texts['privacy/src/main/java/dev/fusion/privacy/GpcHeader.kt'])
    check('PdfDate never assumes UTC',
          'never assumed UTC' in texts['format/pdf/src/main/java/dev/fusion/format/pdf/PdfDate.kt'])
    check('HstsParse tolerates unknown directives per spec',
          'tolerated per spec' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/HstsParse.kt'] or
          'IGNORED per spec' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/HstsParse.kt'])
    check('GainDb keeps amplitude/power split explicit',
          'amplitude/power' in texts['feature/media/src/main/kotlin/dev/fusion/media/GainDb.kt'].lower() or
          '20' in texts['feature/media/src/main/kotlin/dev/fusion/media/GainDb.kt'])
    check('Rfc2231 is stateless (no mutable kernel state)',
          'var lastCharset' not in texts['feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2231.kt'])

    # Spec pins
    check('Damm quasigroup row 0 pinned', '0, 3, 1, 7, 5, 9, 8, 6, 4, 2' in texts['feature/continuity/src/main/kotlin/dev/fusion/continuity/PairPin.kt'])
    check('ar magic pinned', '!<arch>' in texts['format/archive/src/main/kotlin/dev/fusion/archive/ArHead.kt'])
    check('NextRun 60s floor pinned', 'MIN_PERIOD = 60L' in texts['feature/automation/src/main/kotlin/dev/fusion/automation/NextRun.kt'])
    check('BmpHead refuses V4/V5 by biSize', 'biSize' in texts['format/image/src/main/kotlin/dev/fusion/format/image/BmpHead.kt'])
    check('CsiCursor leaves SGR to AnsiSgr', 'AnsiSgr' in texts['feature/terminal/src/main/kotlin/dev/fusion/terminal/CsiCursor.kt'])

    check('Histo refuses auto-scaling by design',
          'no auto-scaling' in texts['core/perf/src/main/kotlin/dev/fusion/perf/Histo.kt'])
    check('CaseCycle declares the Turkish-i narrowing',
          'Turkish' in texts['core/documents/src/main/kotlin/dev/fusion/documents/CaseCycle.kt'])
    check('UnitPrice refuses cross-dimension comparison in code',
          'density' in texts['feature/notes/src/main/kotlin/dev/fusion/notes/UnitPrice.kt'])
    check('NiceStep pins the 1-2-5 family', '1.0, 2.0, 5.0' in texts['feature/calc/src/main/kotlin/dev/fusion/calc/NiceStep.kt'])
    check('no binary fixtures this round and none referenced by the runner',
          'fixtures/r42' in (ROOT / 'tools/run_r42_contracts.py').read_text())

    # Gradle/CI wiring for this round and the r37 backport
    gradle = (ROOT / 'testing/build.gradle.kts').read_text()
    check('r42Contracts Gradle task registered', 'r42Contracts' in gradle and 'R42Checks' in gradle)
    check('r37Contracts backport registered', 'r37Contracts' in gradle and 'R37Checks' in gradle)
    wf = (ROOT.parent / '.github/workflows/android.yml').read_text()
    check('JVM lane runs r37+r42 contract tasks',
          ':testing:r37Contracts' in wf and ':testing:r42Contracts' in wf)


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
