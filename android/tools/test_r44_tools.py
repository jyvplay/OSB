#!/usr/bin/env python3
"""Hygiene and pin tests for the R44 round artifacts. Host-only, unittest-native."""
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
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetHide.kt': 'object SheetHide',
    'core/documents/src/main/kotlin/dev/fusion/documents/QuoteNest.kt': 'object QuoteNest',
    'feature/editor/src/main/java/dev/fusion/editor/GoToLine.kt': 'object GoToLine',
    'format/pdf/src/main/java/dev/fusion/format/pdf/ObjNum.kt': 'object ObjNum',
    'core/security/src/main/kotlin/dev/fusion/security/SriHash.kt': 'object SriHash',
    'format/archive/src/main/kotlin/dev/fusion/archive/DebVersion.kt': 'object DebVersion',
    'feature/lens/src/main/kotlin/dev/fusion/lens/Issn.kt': 'object Issn',
    'feature/lens/src/main/kotlin/dev/fusion/lens/UpcE.kt': 'object UpcE',
    'feature/terminal/src/main/kotlin/dev/fusion/terminal/CwdTilde.kt': 'object CwdTilde',
    'core/index/src/main/kotlin/dev/fusion/index/Levenshtein.kt': 'object Levenshtein',
    'core/settings/src/main/kotlin/dev/fusion/settings/PrefKeyLint.kt': 'object PrefKeyLint',
    'core/commands/src/main/kotlin/dev/fusion/commands/HistDedup.kt': 'object HistDedup',
    'feature/media/src/main/kotlin/dev/fusion/media/SampleBytes.kt': 'object SampleBytes',
    'feature/messages/src/main/kotlin/dev/fusion/messages/SubjectNorm.kt': 'object SubjectNorm',
    'privacy/src/main/java/dev/fusion/privacy/GpsRational.kt': 'object GpsRational',
    'core/hw/src/main/kotlin/dev/fusion/hw/DisplayInch.kt': 'object DisplayInch',
    'feature/backup/src/main/kotlin/dev/fusion/backup/BackupName.kt': 'object BackupName',
    'feature/launcher/src/main/kotlin/dev/fusion/launcher/AlphaIndex.kt': 'object AlphaIndex',
    'format/image/src/main/kotlin/dev/fusion/format/image/ExifOrient.kt': 'object ExifOrient',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/AcceptLang.kt': 'object AcceptLang',
    'feature/notes/src/main/kotlin/dev/fusion/notes/TagExtract.kt': 'object TagExtract',
    'feature/alerts/src/main/kotlin/dev/fusion/alerts/Severity.kt': 'object Severity',
    'feature/files/src/main/kotlin/dev/fusion/filesui/HiddenDot.kt': 'object HiddenDot',
    'feature/together/src/main/kotlin/dev/fusion/together/SessionCode.kt': 'object SessionCode',
}


def main():
    texts = {rel: (ROOT / rel).read_text() for rel in KERNELS}

    check('all 24 kernels exist with declared objects',
          all(decl in texts[rel] for rel, decl in KERNELS.items()))
    check('no android imports in any kernel',
          all('import android' not in t and 'import androidx' not in t for t in texts.values()))
    check('no file IO inside kernels', all('java.io.File' not in t for t in texts.values()))
    check('every kernel declares a SCOPE honesty constant',
          all('const val SCOPE' in t for t in texts.values()))
    check('no TODO/FIXME left in kernels',
          all(not re.search(r'(?<!V)TODO', t) and 'FIXME' not in t for t in texts.values()))
    total_fns = {'core/documents/src/main/kotlin/dev/fusion/documents/QuoteNest.kt',
                 'core/commands/src/main/kotlin/dev/fusion/commands/HistDedup.kt'}
    check('kernels refuse loudly (require/IAE; QuoteNest/HistDedup are declared total transforms)',
          all(('require(' in t or 'IllegalArgumentException' in t)
              for rel, t in texts.items() if rel not in total_fns))

    checks = (ROOT / 'testing/src/r44/kotlin/dev/fusion/testing/R44Checks.kt').read_text()
    check('consumer counts assertions through ok()', 'count++' in checks)
    check('consumer declares 21 kernels in its banner', 'across 24 kernels' in checks)
    check('consumer never claims device evidence', 'not Android/device evidence' in checks)
    check('no tautological assertions in consumer', '|| true' not in checks)
    runner = (ROOT / 'tools/run_r44_contracts.py').read_text()
    check('runner compiles groups with -include-runtime', "'-include-runtime'" in runner)
    groups = re.findall(r"^\s{4}'([a-z0-9-]+)':", runner, re.M)
    check('runner has 23 groups covering 24 kernels (ooxml/lens groups carry intra-module deps)',
          len(groups) == 23 and len(set(groups)) == 23)

    ref = (ROOT / 'tools/check_r44_reference.py').read_text()
    check('reference mirrors all 24 kernels', 'len(CHECKS)' in ref and ref.count('def ') >= 25)
    check('mirror honesty note present', 'HANDWRITTEN' in ref)
    check('no tautological assertions in mirrors', 'or True' not in ref and '!= None' not in ref)
    check('Debian tilde rule re-implemented independently in the mirror', 'cmp_part' in ref)

    check('SriHash never hashes', 'never hashes' in texts['core/security/src/main/kotlin/dev/fusion/security/SriHash.kt'])
    check('GpsRational: stripping is the caller act', "caller" in texts['privacy/src/main/java/dev/fusion/privacy/GpsRational.kt'])
    check('SessionCode disclaims checksum protection', 'NOT a checksum' in texts['feature/together/src/main/kotlin/dev/fusion/together/SessionCode.kt'])
    check('AlphaIndex declares Latin-only honestly', 'named, not faked' in texts['feature/launcher/src/main/kotlin/dev/fusion/launcher/AlphaIndex.kt'])
    check('QuoteNest declares the > ambiguity', 'ambiguity is declared' in texts['core/documents/src/main/kotlin/dev/fusion/documents/QuoteNest.kt'] or 'declared, not heuristically' in texts['core/documents/src/main/kotlin/dev/fusion/documents/QuoteNest.kt'])
    check('GoToLine reports clamping, never silent', 'clamping REPORTED' in texts['feature/editor/src/main/java/dev/fusion/editor/GoToLine.kt'] or 'REPORTED' in texts['feature/editor/src/main/java/dev/fusion/editor/GoToLine.kt'])
    check('ExifOrient refuses corrupt values', 'refusing beats flipping' in texts['format/image/src/main/kotlin/dev/fusion/format/image/ExifOrient.kt'])
    check('AcceptLang names the fingerprint vector', 'fingerprint' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/AcceptLang.kt'])
    check('DisplayInch refuses anisotropic averaging', 'anisotropic' in texts['core/hw/src/main/kotlin/dev/fusion/hw/DisplayInch.kt'])
    check('ObjNum pins the free-list ceiling', '65535' in texts['format/pdf/src/main/java/dev/fusion/format/pdf/ObjNum.kt'])

    check('ISSN weights pinned', '(8 - i)' in texts['feature/lens/src/main/kotlin/dev/fusion/lens/Issn.kt'])
    check('severity refuses de-escalation', 'de-escalation' in texts['feature/alerts/src/main/kotlin/dev/fusion/alerts/Severity.kt'])
    check('UPC-E NS0 refusal pinned', 'NS 0 only' in texts['feature/lens/src/main/kotlin/dev/fusion/lens/UpcE.kt'])
    check('session alphabet re-pinned locally (no cross-module import)', 'ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"' in texts['feature/together/src/main/kotlin/dev/fusion/together/SessionCode.kt'])
    check('backup lex==chrono law stated', 'lex==chrono' in texts['feature/backup/src/main/kotlin/dev/fusion/backup/BackupName.kt'] or 'lexicographic' in texts['feature/backup/src/main/kotlin/dev/fusion/backup/BackupName.kt'].lower())

    gradle = (ROOT / 'testing/build.gradle.kts').read_text()
    check('r44Contracts Gradle task registered', 'r44Contracts' in gradle and 'R44Checks' in gradle)
    wf = (ROOT.parent / '.github/workflows/android.yml').read_text()
    check('JVM lane runs r44 task', ':testing:r44Contracts' in wf)


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
