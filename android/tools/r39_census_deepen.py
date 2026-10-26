#!/usr/bin/env python3
"""One-shot R39 census deepening: append R39 kernel evidence to 16 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R39 pairing is stale (those must be repaired by hand, not
silently re-pinned). R39 edits NO existing production sources — all 19 kernels
are new files — so the allowed-stale set is exactly the R39 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R39_CHECKS = ['testing/src/r39/kotlin/dev/fusion/testing/R39Checks.kt',
              'tools/test_r39_tools.py', 'tools/check_r39_reference.py']
CMD = 'python3 tools/run_r39_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R39 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':format:ooxml': [
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/SerialDate.kt',
         'R39 deepens again with SerialDate: Excel 1900/1904 serial-date arithmetic keeping the Lotus phantom 1900-02-29 as serial 60 per ECMA-376, cross-checked against Python datetime, executed on host JVM.'),
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt',
         'R39 also adds ColLetters: the bijective base-26 A..XFD column alphabet with the full 1..16384 bijection executed as one property, on host JVM.'),
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColWidth.kt',
         'R39 also adds ColWidth: the ECMA-376 section 18.3.1.13 character/pixel/file-width conversions (MDW model, Excel default 8.43 chars = 64 px pinned) executed on host JVM.'),
    ],
    ':core:documents': [
        ('core/documents/src/main/kotlin/dev/fusion/documents/SmartQuote.kt',
         'R39 deepens again with SmartQuote: a deterministic typographic-quote educator with the elision narrowing declared rather than hidden, executed on host JVM.'),
        ('core/documents/src/main/kotlin/dev/fusion/documents/HyphenSoft.kt',
         'R39 also adds HyphenSoft: UAX 14 soft-hyphen stripping, break candidates in stripped coordinates and split rendering, executed on host JVM.'),
    ],
    ':feature:editor': [
        ('feature/editor/src/main/java/dev/fusion/editor/RomanNum.kt',
         'R39 deepens again with RomanNum: canonical subtractive Roman numerals for upperRoman/lowerRoman list numbering with the full 1..3999 bijection executed as one property, on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/CropBox.kt',
         'R39 deepens again with CropBox: ISO 32000-1 rectangle normalization plus the CropBox-defaults-to-MediaBox and intersection rules with disjoint crops refused, executed on host JVM.'),
    ],
    ':format:image': [
        ('format/image/src/main/kotlin/dev/fusion/format/image/IcoDir.kt',
         'R39 deepens again with IcoDir: ICONDIR/ICONDIRENTRY parsing with 0-means-256 dimensions, payload-kind detection and PNG-IHDR dimension agreement enforced, executed on host JVM against a real fixture.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/Punycode.kt',
         'R39 deepens again with Punycode: the full RFC 3492 encoder/decoder with overflow refusal and xn-- bracketing, cross-checked against the held-out Python punycode codec, executed on host JVM; IDNA mapping is declared out of scope.'),
    ],
    ':core:hw': [
        ('core/hw/src/main/kotlin/dev/fusion/hw/UsbDesc.kt',
         'R39 deepens again with UsbDesc: USB 2.0 section 9.6.1 device-descriptor parsing (BCD versions, EP0 size law per bus generation) whose scope string states a descriptor alone can never prove a keyboard, executed on host JVM against a real fixture.'),
    ],
    ':feature:media': [
        ('feature/media/src/main/kotlin/dev/fusion/media/MidiNote.kt',
         'R39 deepens again with MidiNote: MIDI 0..127 naming under the C4=60 convention and 12-TET frequencies at A4=440 with cents deviation, executed on host JVM.'),
    ],
    ':core:storage': [
        ('core/storage/src/main/kotlin/dev/fusion/storage/Ulid.kt',
         'R39 deepens again with Ulid: the canonical ULID codec (Crockford alphabet, 2^48-1 ceiling, case-insensitive decode, monotonic successor that fails on 80-bit overflow) executed on host JVM against the spec vectors.'),
    ],
    ':core:security': [
        ('core/security/src/main/kotlin/dev/fusion/security/JwtSplit.kt',
         'R39 deepens again with JwtSplit: RFC 7515 compact-serialization splitting whose result carries signatureChecked=false constitutionally, surfaces alg:none for refusal, and refuses escaped headers rather than mis-parsing, executed on host JVM.'),
    ],
    ':feature:vault': [
        ('feature/vault/src/main/kotlin/dev/fusion/vault/IbanCheck.kt',
         'R39 deepens again with IbanCheck: ISO 13616 mod-97 validation with check-digit generation over a declared 12-country registry subset that refuses unknown countries instead of guessing, executed on host JVM.'),
    ],
    ':feature:find': [
        ('feature/find/src/main/java/dev/fusion/find/GlobMatch.kt',
         'R39 deepens again with GlobMatch: anchored fnmatch-semantics glob matching (sets, ranges, literal-bracket edge cases) cross-checked vector-for-vector against the held-out Python fnmatch module, executed on host JVM.'),
    ],
    ':privacy': [
        ('privacy/src/main/java/dev/fusion/privacy/MacAddr.kt',
         'R39 deepens again with MacAddr: IEEE 802 U/L and I/G bit analysis across three address shapes with OUI suppressed for local addresses and redaction that keeps only the administration half; local-unicast is documented as a hint, never proof of randomization; executed on host JVM.'),
    ],
    ':feature:backup': [
        ('feature/backup/src/main/kotlin/dev/fusion/backup/Rfc3339.kt',
         'R39 deepens again with Rfc3339: RFC 3339 date-times with mandatory offsets, the -00:00 unknown-offset flag and epoch conversion cross-checked against the held-out datetime.fromisoformat, leap seconds refused by declared policy, executed on host JVM.'),
    ],
    ':feature:alerts': [
        ('feature/alerts/src/main/kotlin/dev/fusion/alerts/QuietHours.kt',
         'R39 deepens again with QuietHours: half-open minute-of-day windows with midnight wrap, boundary countdowns and brute-force-verified overlaps; the degenerate start==end window is refused as ambiguous; executed on host JVM.'),
    ],
    ':feature:capture': [
        ('feature/capture/src/main/kotlin/dev/fusion/capture/ExposureEv.kt',
         'R39 deepens again with ExposureEv: APEX exposure-value arithmetic (sunny-16 pinned at EV15, ISO rescaling, reciprocity solvers) with metering and luminance calibration declared out of scope, executed on host JVM.'),
    ],
}


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def main():
    jp = ROOT / 'docs/module-testability.json'
    data = json.loads(jp.read_text())
    stale = []
    for r in data['rows']:
        for p in r['production'] + r['checks']:
            if r.get('sha256', {}).get(p) != sha(p):
                stale.append((r['module'], p))
    owned = set(R39_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    owned.add('tools/test_r40_tools.py')  # sibling round's check file shares rows; joint harness edits re-pin here
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R39 edit set: {unexpected}')
    for module, path in stale:
        for r in data['rows']:
            if r['module'] == module:
                r['sha256'][path] = sha(path)
    seen = set()
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        for prod, sentence in DEEPEN[r['module']]:
            if prod in r['production']:
                if sentence not in r['behavior']:
                    raise SystemExit(f"{r['module']} lists {prod} without the R39 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R39_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
        seen.add(r['module'])
    missing = set(DEEPEN) - seen
    if missing:
        raise SystemExit(f'modules not found in census: {sorted(missing)}')
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R39 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
