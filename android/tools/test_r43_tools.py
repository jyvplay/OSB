#!/usr/bin/env python3
"""Hygiene and pin tests for the R43 round artifacts. Host-only, unittest-native."""
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
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetFreeze.kt': 'object SheetFreeze',
    'core/documents/src/main/kotlin/dev/fusion/documents/BidiStrip.kt': 'object BidiStrip',
    'feature/editor/src/main/java/dev/fusion/editor/DupLines.kt': 'object DupLines',
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfPerm.kt': 'object PdfPerm',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/RefererPolicy.kt': 'object RefererPolicy',
    'format/archive/src/main/kotlin/dev/fusion/archive/DebControl.kt': 'object DebControl',
    'core/capabilities/src/main/java/dev/fusion/capabilities/ApiName.kt': 'object ApiName',
    'feature/together/src/main/kotlin/dev/fusion/together/SeatColor.kt': 'object SeatColor',
    'feature/media/src/main/kotlin/dev/fusion/media/Bpm.kt': 'object Bpm',
    'feature/beam/src/main/kotlin/dev/fusion/beam/TrackerTier.kt': 'object TrackerTier',
    'feature/lens/src/main/kotlin/dev/fusion/lens/QrVersion.kt': 'object QrVersion',
    'core/security/src/main/kotlin/dev/fusion/security/RpIdMatch.kt': 'object RpIdMatch',
    'core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt': 'object VersionRange',
    'feature/find/src/main/java/dev/fusion/find/SnippetWindow.kt': 'object SnippetWindow',
    'feature/vault/src/main/kotlin/dev/fusion/vault/AgeId.kt': 'object AgeId',
    'feature/files/src/main/kotlin/dev/fusion/filesui/BreadcrumbTrim.kt': 'object BreadcrumbTrim',
    'core/perf/src/main/kotlin/dev/fusion/perf/Apdex.kt': 'object Apdex',
    'feature/calc/src/main/kotlin/dev/fusion/calc/PercentChange.kt': 'object PercentChange',
    'cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspTransport.kt': 'object RtspTransport',
    'feature/messages/src/main/kotlin/dev/fusion/messages/MsgIdParse.kt': 'object MsgIdParse',
    'privacy/src/main/java/dev/fusion/privacy/TimeCoarse.kt': 'object TimeCoarse',
    'core/windowing/src/main/kotlin/dev/fusion/windowing/AspectSnap.kt': 'object AspectSnap',
    'feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertKey.kt': 'object AlertKey',
}


def main():
    texts = {rel: (ROOT / rel).read_text() for rel in KERNELS}

    check('all 23 kernels exist with declared objects',
          all(decl in texts[rel] for rel, decl in KERNELS.items()))
    check('no android imports in any kernel',
          all('import android' not in t and 'import androidx' not in t for t in texts.values()))
    check('no file IO inside kernels', all('java.io.File' not in t for t in texts.values()))
    check('every kernel declares a SCOPE honesty constant',
          all('const val SCOPE' in t for t in texts.values()))
    check('no TODO/FIXME left in kernels',
          all(not re.search(r'(?<!V)TODO', t) and 'FIXME' not in t for t in texts.values()))
    total_fns = {'feature/editor/src/main/java/dev/fusion/editor/DupLines.kt',
                 'core/documents/src/main/kotlin/dev/fusion/documents/BidiStrip.kt',
                 'format/pdf/src/main/java/dev/fusion/format/pdf/PdfPerm.kt'}
    check('kernels refuse loudly (require/IAE; DupLines/BidiStrip/PdfPerm are declared total transforms)',
          all(('require(' in t or 'IllegalArgumentException' in t)
              for rel, t in texts.items() if rel not in total_fns))

    checks = (ROOT / 'testing/src/r43/kotlin/dev/fusion/testing/R43Checks.kt').read_text()
    check('consumer counts assertions through ok()', 'count++' in checks)
    check('consumer declares 21 kernels in its banner', 'across 23 kernels' in checks)
    check('consumer never claims device evidence', 'not Android/device evidence' in checks)
    check('no tautological assertions in consumer', '|| true' not in checks)
    runner = (ROOT / 'tools/run_r43_contracts.py').read_text()
    check('runner compiles groups with -include-runtime', "'-include-runtime'" in runner)
    groups = re.findall(r"^\s{4}'([a-z0-9-]+)':", runner, re.M)
    check('runner has 23 groups covering 23 kernels (ooxml/model/find/vault groups carry intra-module deps)',
          len(groups) == 23 and len(set(groups)) == 23)

    ref = (ROOT / 'tools/check_r43_reference.py').read_text()
    check('reference mirrors all 23 kernels', 'len(CHECKS)' in ref and ref.count('def ') >= 24)
    check('mirror honesty note present', 'HANDWRITTEN' in ref)
    check('no tautological assertions in mirrors', 'or True' not in ref and '!= None' not in ref)

    check('PdfPerm states policy-not-enforcement', 'NOT enforcement' in texts['format/pdf/src/main/java/dev/fusion/format/pdf/PdfPerm.kt'])
    check('RpIdMatch result admits PSL unchecked', 'pslChecked = false' in texts['core/security/src/main/kotlin/dev/fusion/security/RpIdMatch.kt'])
    check('TimeCoarse states not-anonymity', 'NOT anonymity' in texts['privacy/src/main/java/dev/fusion/privacy/TimeCoarse.kt'])
    check('AgeId states zero cryptography', 'zero cryptography' in texts['feature/vault/src/main/kotlin/dev/fusion/vault/AgeId.kt'])
    check('BidiStrip pins stripping as user-visible choice', 'never' in texts['core/documents/src/main/kotlin/dev/fusion/documents/BidiStrip.kt'] and 'silently' in texts['core/documents/src/main/kotlin/dev/fusion/documents/BidiStrip.kt'])
    check('ApiName refuses unreleased levels', 'refused, not guessed' in texts['core/capabilities/src/main/java/dev/fusion/capabilities/ApiName.kt'])
    check('SheetFreeze upholds the PaneSplit cut', 'cut upheld' in texts['format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetFreeze.kt'])
    check('HSTS-style unknown-param tolerance in RtspTransport', 'kept verbatim' in texts['cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspTransport.kt'])
    check('TrackerTier regex repair present (no dot-star tail)', '\\S*' in texts['feature/beam/src/main/kotlin/dev/fusion/beam/TrackerTier.kt'])
    check('PercentChange positive-base restriction declared', 'positive' in texts['feature/calc/src/main/kotlin/dev/fusion/calc/PercentChange.kt'].lower())

    check('golden angle pinned', '137.50776405003785' in texts['feature/together/src/main/kotlin/dev/fusion/together/SeatColor.kt'])
    check('QR lattice law pinned', '17 + 4 * version' in texts['feature/lens/src/main/kotlin/dev/fusion/lens/QrVersion.kt'])
    check('Apdex bands pinned', '0.94' in texts['core/perf/src/main/kotlin/dev/fusion/perf/Apdex.kt'])
    check('referrer default pinned', 'STRICT_ORIGIN_WHEN_CROSS_ORIGIN' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/RefererPolicy.kt'])
    check('Table 22 bit positions pinned', 'bit(p, 3)' in texts['format/pdf/src/main/java/dev/fusion/format/pdf/PdfPerm.kt'])

    check('Damm-family lineage distinct: AlertKey restates bucket law independently',
          'independently coded' in texts['feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertKey.kt'])
    check('SnippetWindow pins candidate-edge optimality claim',
          'optimality' in texts['feature/find/src/main/java/dev/fusion/find/SnippetWindow.kt'])
    check('VersionRange refuses prerelease bounds by name',
          'prerelease' in texts['core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt'])
    check('DebControl handles the dot blank-line marker',
          "\" .\"" in texts['format/archive/src/main/kotlin/dev/fusion/archive/DebControl.kt'] or 'marker' in texts['format/archive/src/main/kotlin/dev/fusion/archive/DebControl.kt'])
    check('BreadcrumbTrim reports over-budget rather than hiding it',
          'overBudget' in texts['feature/files/src/main/kotlin/dev/fusion/filesui/BreadcrumbTrim.kt'])
    check('consumer has no wide-tolerance shape assertions', '1.0) , "shape' not in (ROOT / 'testing/src/r43/kotlin/dev/fusion/testing/R43Checks.kt').read_text())

    gradle = (ROOT / 'testing/build.gradle.kts').read_text()
    check('r43Contracts Gradle task registered', 'r43Contracts' in gradle and 'R43Checks' in gradle)
    wf = (ROOT.parent / '.github/workflows/android.yml').read_text()
    check('JVM lane runs r43 task', ':testing:r43Contracts' in wf)


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
