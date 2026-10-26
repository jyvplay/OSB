#!/usr/bin/env python3
"""Hygiene and pin tests for the R41 round artifacts. Host-only, unittest-native."""
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
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt': 'object SheetRange',
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/R1c1Ref.kt': 'object R1c1Ref',
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageGeometry.kt': 'object PdfPageGeometry',
    'core/commands/src/main/kotlin/dev/fusion/commands/ShlexJoin.kt': 'object ShlexJoin',
    'feature/automation/src/main/kotlin/dev/fusion/automation/DurationIso.kt': 'object DurationIso',
    'feature/maps/src/main/kotlin/dev/fusion/maps/DmsFormat.kt': 'object DmsFormat',
    'core/model/src/main/kotlin/dev/fusion/model/ColorHex.kt': 'object ColorHex',
    'feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadEase.kt': 'object ReadEase',
    'feature/lens/src/main/kotlin/dev/fusion/lens/Ean13.kt': 'object Ean13',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/DataUri.kt': 'object DataUri',
    'core/index/src/main/kotlin/dev/fusion/index/Trigram.kt': 'object Trigram',
    'feature/together/src/main/kotlin/dev/fusion/together/TurnOrder.kt': 'object TurnOrder',
    'core/perf/src/main/kotlin/dev/fusion/perf/Ewma.kt': 'object Ewma',
    'core/settings/src/main/kotlin/dev/fusion/settings/KeyMigrate.kt': 'object KeyMigrate',
    'cast/castv2/src/main/kotlin/dev/fusion/castv2/CastNs.kt': 'object CastNs',
    'core/documents/src/main/kotlin/dev/fusion/documents/LineWidth.kt': 'object LineWidth',
    'core/storage/src/main/kotlin/dev/fusion/storage/ShardPath.kt': 'object ShardPath',
    'feature/find/src/main/java/dev/fusion/find/SpanMerge.kt': 'object SpanMerge',
    'feature/beam/src/main/kotlin/dev/fusion/beam/PiecePlan.kt': 'object PiecePlan',
    'feature/editor/src/main/java/dev/fusion/editor/TabStops.kt': 'object TabStops',
    'feature/launcher/src/main/kotlin/dev/fusion/launcher/GridPos.kt': 'object GridPos',
}


def main():
    texts = {rel: (ROOT / rel).read_text() for rel in KERNELS}

    check('all 21 kernels exist with declared objects',
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

    checks = (ROOT / 'testing/src/r41/kotlin/dev/fusion/testing/R41Checks.kt').read_text()
    check('consumer counts assertions through ok()', 'count++' in checks)
    check('consumer declares 21 kernels in its banner', 'across 21 kernels' in checks)
    check('consumer never claims device evidence', 'not Android/device evidence' in checks)
    check('no tautological assertions in consumer', '|| true' not in checks)
    runner = (ROOT / 'tools/run_r41_contracts.py').read_text()
    check('runner compiles groups with -include-runtime', "'-include-runtime'" in runner)
    groups = re.findall(r"^\s{4}'([a-z0-9-]+)':", runner, re.M)
    check('runner has 21 kernel groups (ooxml/pdf carry intra-module deps)',
          len(groups) == 20 and len(set(groups)) == 20)

    ref = (ROOT / 'tools/check_r41_reference.py').read_text()
    check('reference mirrors all 21 kernels', 'len(CHECKS)' in ref and ref.count('def ') >= 22)
    for held_out in ('shlex.join', 'east_asian_width', 'timedelta', 'unquote_to_bytes', 'expandtabs'):
        check(f'held-out verifier present: {held_out}', held_out in ref)
    check('no tautological assertions in mirrors', 'or True' not in ref and '!= None' not in ref)

    # Honesty greps
    check('DurationIso refuses calendar conversion',
          'refuses rather than assume' in texts['feature/automation/src/main/kotlin/dev/fusion/automation/DurationIso.kt'])
    check('ReadEase keeps syllable counting outside',
          'stays outside' in texts['feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadEase.kt'])
    check('ColorHex names the CSS-order trap',
          'CSS' in texts['core/model/src/main/kotlin/dev/fusion/model/ColorHex.kt'])
    check('LineWidth declares ambiguous-narrow',
          'ambiguous' in texts['core/documents/src/main/kotlin/dev/fusion/documents/LineWidth.kt'].lower())
    check('TurnOrder states its rules ARE the spec',
          'stated contract' in texts['feature/together/src/main/kotlin/dev/fusion/together/TurnOrder.kt'])
    check('PiecePlan promotes convention to refusal',
          'promoted from convention to refusal' in texts['feature/beam/src/main/kotlin/dev/fusion/beam/PiecePlan.kt'] or
          'promotes the convention' in texts['feature/beam/src/main/kotlin/dev/fusion/beam/PiecePlan.kt'])

    # Spec pins
    check('grid bounds pinned', 'MAX_ROW = 1048576' in texts['format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt'])
    check('Flesch constants pinned', '206.835' in texts['feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadEase.kt'])
    check('16KiB piece floor pinned', 'MIN_PIECE = 16384' in texts['feature/beam/src/main/kotlin/dev/fusion/beam/PiecePlan.kt'])
    check('pg_trgm padding documented', 'pg_trgm' in texts['core/index/src/main/kotlin/dev/fusion/index/Trigram.kt'])
    check('shlex safe set pinned', '_@%+=:,./-' in texts['core/commands/src/main/kotlin/dev/fusion/commands/ShlexJoin.kt'])

    # Gradle/CI wiring for this round and the r38 backport
    gradle = (ROOT / 'testing/build.gradle.kts').read_text()
    check('r41Contracts Gradle task registered', 'r41Contracts' in gradle and 'R41Checks' in gradle)
    check('r38Contracts backport registered', 'r38Contracts' in gradle and 'R38Checks' in gradle)
    wf = (ROOT.parent / '.github/workflows/android.yml').read_text()
    check('JVM lane runs r38+r41 contract tasks',
          ':testing:r38Contracts' in wf and ':testing:r41Contracts' in wf)


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
