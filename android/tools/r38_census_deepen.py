#!/usr/bin/env python3
"""One-shot R38 census deepening: append R38 kernel evidence to 15 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R38 pairing is stale (those must be repaired by hand, not
silently re-pinned). R38 edits NO existing production sources — all 17 kernels
are new files — so the allowed-stale set is exactly the R38 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R38_CHECKS = ['testing/src/r38/kotlin/dev/fusion/testing/R38Checks.kt',
              'tools/test_r38_tools.py', 'tools/check_r38_reference.py']
CMD = 'python3 tools/run_r38_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R38 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':feature:beam': [
        ('feature/beam/src/main/kotlin/dev/fusion/beam/MagnetParse.kt',
         'R38 deepens again with MagnetParse: BEP 9 magnet links (urn:btih 40-hex/32-base32 both normalized, urn:btmh sha2-256 multihash, repeatable tr/x.pe, strict percent-decode) executed on host JVM.'),
        ('feature/beam/src/main/kotlin/dev/fusion/beam/Bencode.kt',
         'R38 also adds Bencode: a strict BEP 3 decoder (i-0e and leading zeros refused, raw-sorted unique dict keys, full-consumption, declared Long/depth/size bounds) executed on host JVM.'),
    ],
    ':feature:media': [
        ('feature/media/src/main/kotlin/dev/fusion/media/FlacHead.kt',
         'R38 deepens again with FlacHead: RFC 9639 fLaC marker plus 34-byte streaminfo bit unpacking (20-bit sample rate, 36-bit total samples, block-size floor 16) executed on host JVM against a real fixture.'),
    ],
    ':format:image': [
        ('format/image/src/main/kotlin/dev/fusion/format/image/WebpHead.kt',
         'R38 deepens again with WebpHead: RFC 9649 RIFF/WEBP envelope and VP8X canvas/flag decoding (minus-one 24-bit dims, 2^32-1 pixel budget) executed on host JVM against a real fixture.'),
    ],
    ':core:model': [
        ('core/model/src/main/kotlin/dev/fusion/model/SemVer.kt',
         'R38 deepens again with SemVer: Semantic Versioning 2.0.0 parsing and the full item-11 precedence chain (numeric vs alphanumeric identifiers, build ignored) executed on host JVM.'),
    ],
    ':core:storage': [
        ('core/storage/src/main/kotlin/dev/fusion/storage/UuidInfo.kt',
         'R38 deepens again with UuidInfo: RFC 9562 variant/version classification plus UUIDv7 48-bit millisecond timestamp extraction against the RFC test vector, executed on host JVM.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/HttpRange.kt',
         'R38 deepens again with HttpRange: RFC 9110 section 14 byte-range resolution (suffix ranges, clamping, 416 refusal when nothing satisfies) executed on host JVM.'),
    ],
    ':feature:messages': [
        ('feature/messages/src/main/kotlin/dev/fusion/messages/QuotedPrint.kt',
         'R38 deepens again with QuotedPrint: strict RFC 2045 section 6.7 quoted-printable decode/encode (uppercase-only escapes, soft breaks, 76-char line cap) executed on host JVM.'),
    ],
    ':core:security': [
        ('core/security/src/main/kotlin/dev/fusion/security/PemBound.kt',
         'R38 deepens again with PemBound: strict single-block RFC 7468 PEM reading (matching labels, 64-char interior lines, canonical base64 trailing bits) executed on host JVM.'),
        ('core/security/src/main/kotlin/dev/fusion/security/KeyCadence.kt',
         'R38 also adds KeyCadence: an explicitly evadable in-app inter-keystroke timing classifier with published 2026 thresholds and a scope string that admits hardware-keylogger blindness, executed on host JVM.'),
    ],
    ':feature:automation': [
        ('feature/automation/src/main/kotlin/dev/fusion/automation/IsoWeek.kt',
         'R38 deepens again with IsoWeek: ISO 8601 week-date math (days-from-civil, long-year rule, year-boundary weeks) cross-checked against Python datetime.isocalendar, executed on host JVM.'),
    ],
    ':feature:accessibility': [
        ('feature/accessibility/src/main/kotlin/dev/fusion/a11y/HslColor.kt',
         'R38 deepens again with HslColor: the CSS Color 4 hsl-to-rgb algorithm cross-checked against Python colorsys, executed on host JVM.'),
    ],
    ':core:hw': [
        ('core/hw/src/main/kotlin/dev/fusion/hw/EdidHead.kt',
         'R38 deepens again with EdidHead: VESA EDID base-block parsing (fixed header, 5-bit PnP letters, mod-256 checksum, model-year flag) executed on host JVM against a real fixture.'),
    ],
    ':feature:capture': [
        ('feature/capture/src/main/kotlin/dev/fusion/capture/VttTime.kt',
         'R38 deepens again with VttTime: W3C WebVTT timestamp and cue-timings parsing (2+ digit hours, strict dot-milliseconds, end-after-start) executed on host JVM, distinct from the SMPTE Timecode kernel.'),
    ],
    ':privacy': [
        ('privacy/src/main/java/dev/fusion/privacy/LuhnCheck.kt',
         'R38 deepens again with LuhnCheck: ISO/IEC 7812 Luhn validation plus PCI DSS first6+last4 PAN masking and a free-text scrubber that leaves non-PAN digit runs alone, executed on host JVM.'),
    ],
    ':core:index': [
        ('core/index/src/main/kotlin/dev/fusion/index/Soundex.kt',
         'R38 deepens again with Soundex: NARA American Soundex (H/W transparency, vowel recoding, first-letter side-by-side rule) over the published worked examples, executed on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/PdfName.kt',
         'R38 deepens again with PdfName: ISO 32000-1 section 7.3.5 name-object #-escape decode/encode with the #00 prohibition, executed on host JVM over the Table 4 examples.'),
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
    owned = set(R38_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R38 edit set: {unexpected}')
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
                    raise SystemExit(f"{r['module']} lists {prod} without the R38 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R38_CHECKS:
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
    print('R38 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
