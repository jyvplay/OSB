#!/usr/bin/env python3
"""Hygiene and pin tests for the R39 round artifacts. Host-only."""
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PASS = 0


def check(name, cond):
    global PASS
    if not cond:
        raise SystemExit(f'FAIL: {name}')
    PASS += 1
    print(f'ok {PASS} {name}')


KERNELS = {
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SerialDate.kt': 'object SerialDate',
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt': 'object ColLetters',
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColWidth.kt': 'object ColWidth',
    'core/documents/src/main/kotlin/dev/fusion/documents/SmartQuote.kt': 'object SmartQuote',
    'core/documents/src/main/kotlin/dev/fusion/documents/HyphenSoft.kt': 'object HyphenSoft',
    'feature/editor/src/main/java/dev/fusion/editor/RomanNum.kt': 'object RomanNum',
    'format/pdf/src/main/java/dev/fusion/format/pdf/CropBox.kt': 'object CropBox',
    'format/image/src/main/kotlin/dev/fusion/format/image/IcoDir.kt': 'object IcoDir',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/Punycode.kt': 'object Punycode',
    'core/hw/src/main/kotlin/dev/fusion/hw/UsbDesc.kt': 'object UsbDesc',
    'feature/media/src/main/kotlin/dev/fusion/media/MidiNote.kt': 'object MidiNote',
    'core/storage/src/main/kotlin/dev/fusion/storage/Ulid.kt': 'object Ulid',
    'core/security/src/main/kotlin/dev/fusion/security/JwtSplit.kt': 'object JwtSplit',
    'feature/vault/src/main/kotlin/dev/fusion/vault/IbanCheck.kt': 'object IbanCheck',
    'feature/find/src/main/java/dev/fusion/find/GlobMatch.kt': 'object GlobMatch',
    'privacy/src/main/java/dev/fusion/privacy/MacAddr.kt': 'object MacAddr',
    'feature/backup/src/main/kotlin/dev/fusion/backup/Rfc3339.kt': 'object Rfc3339',
    'feature/alerts/src/main/kotlin/dev/fusion/alerts/QuietHours.kt': 'object QuietHours',
    'feature/capture/src/main/kotlin/dev/fusion/capture/ExposureEv.kt': 'object ExposureEv',
}

FIXTURES = {
    'tools/fixtures/r39/ico.bin': 'cb67e4ec08d393a6812137948b8b5fa376fff0a0d7f1664f493e071f941c7d09',
    'tools/fixtures/r39/usbdev.bin': '9798dc19d3b90d043f2d68178b43e86b925a192dad04735568a34df4c0f5060e',
}


def main():
    texts = {rel: (ROOT / rel).read_text() for rel in KERNELS}

    check('all 19 kernels exist with declared objects',
          all(decl in texts[rel] for rel, decl in KERNELS.items()))
    check('no android imports in any kernel',
          all('import android' not in t and 'import androidx' not in t for t in texts.values()))
    check('no JVM-only java.io/File access inside kernels',
          all('java.io.File' not in t for t in texts.values()))
    check('every kernel declares a SCOPE honesty constant',
          all('const val SCOPE' in t for t in texts.values()))
    check('no TODO/FIXME left in kernels',
          all('TODO' not in t and 'FIXME' not in t for t in texts.values()))
    total_functions = {'core/documents/src/main/kotlin/dev/fusion/documents/SmartQuote.kt'}
    check('kernels refuse loudly (require present; SmartQuote is a declared total transform)',
          all('require(' in t for rel, t in texts.items() if rel not in total_functions))

    for rel, sha in FIXTURES.items():
        data = (ROOT / rel).read_bytes()
        check(f'fixture pinned: {Path(rel).name}', hashlib.sha256(data).hexdigest() == sha)
    check('ico fixture is 111 bytes with 2 entries',
          len((ROOT / 'tools/fixtures/r39/ico.bin').read_bytes()) == 111)
    check('usb fixture is exactly one device descriptor',
          len((ROOT / 'tools/fixtures/r39/usbdev.bin').read_bytes()) == 18)

    checks = (ROOT / 'testing/src/r39/kotlin/dev/fusion/testing/R39Checks.kt').read_text()
    check('consumer counts assertions through ok()', 'count++' in checks)
    check('consumer declares 19 kernels in its banner', 'across 19 kernels' in checks)
    check('consumer never claims device evidence', 'not Android/device evidence' in checks)
    check('runner compiles kernels in isolation',
          "'-include-runtime'" in (ROOT / 'tools/run_r39_contracts.py').read_text())
    groups = re.findall(r"^\s{4}'([a-z0-9-]+)':", (ROOT / 'tools/run_r39_contracts.py').read_text(), re.M)
    check('runner has 19 single-kernel groups', len(groups) == 19 and len(set(groups)) == 19)

    ref = (ROOT / 'tools/check_r39_reference.py').read_text()
    check('reference mirrors all 19 kernels', ref.count('def ') >= 20 and 'len(CHECKS)' in ref)
    for held_out in ('fromisoformat', "encode('punycode')", 'fnmatch.fnmatchcase', 'urlsafe_b64decode', 'int(c, 36)'):
        check(f'held-out verifier present: {held_out}', held_out in ref)

    # Honesty greps: forbidden claims in new kernels
    for rel, t in texts.items():
        if 'security' in rel:
            check('JwtSplit never claims verification',
                  'NEVER VERIFIES' in t or 'never checked' in t)
    check('UsbDesc states keyboard-proof impossibility',
          'cannot identify keyboards by itself' in texts['core/hw/src/main/kotlin/dev/fusion/hw/UsbDesc.kt'])
    check('MacAddr states hint-not-proof',
          'NEVER proof' in texts['privacy/src/main/java/dev/fusion/privacy/MacAddr.kt'])
    check('SerialDate documents the Lotus phantom day',
          'phantom' in texts['format/ooxml/src/main/kotlin/dev/fusion/ooxml/SerialDate.kt'])
    check('Punycode declares IDNA out of scope',
          'OUT OF SCOPE' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/Punycode.kt'])

    # Spec pins
    check('ULID alphabet matches canonical spec',
          'ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"' in texts['core/storage/src/main/kotlin/dev/fusion/storage/Ulid.kt'])
    check('XFD grid bound pinned', 'MAX_COLUMN = 16384' in texts['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt'])
    check('RFC 3492 parameters pinned',
          all(f'= {v}' in texts['feature/browser/src/main/kotlin/dev/fusion/browser/model/Punycode.kt']
              for v in (36, 700, 72)))
    check('A4/Letter boxes pinned in points',
          '612.0' in texts['format/pdf/src/main/java/dev/fusion/format/pdf/CropBox.kt'] and
          '595.276' in texts['format/pdf/src/main/java/dev/fusion/format/pdf/CropBox.kt'])

    print(f'R39 tool tests: {PASS}/{PASS} OK (host-only hygiene/pins; not Android evidence).')


if __name__ == '__main__':
    main()
