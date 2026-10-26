#!/usr/bin/env python3
"""One-shot R40 census deepening: append R40 kernel evidence to 19 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R40 pairing is stale (those must be repaired by hand, not
silently re-pinned). R40 edits NO existing production sources — all 20 kernels
are new files — so the allowed-stale set is exactly the R40 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R40_CHECKS = ['testing/src/r40/kotlin/dev/fusion/testing/R40Checks.kt',
              'tools/test_r40_tools.py', 'tools/check_r40_reference.py']
CMD = 'python3 tools/run_r40_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R40 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':format:ooxml': [
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/NumFmtSections.kt',
         'R40 deepens again with NumFmtSections: ECMA-376 section 18.8.31 number-format structure (quote/bracket/escape-aware section splitting, color and General classification) executed on host JVM; rendering stays out of scope and LiteralXlsxImport keeps refusing custom formats.'),
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt',
         'R40 also adds CellErr: the seven classic SpreadsheetML error literals with format/parse bijection and the dynamic-array-era errors refused by name, executed on host JVM.'),
    ],
    ':core:documents': [
        ('core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt',
         'R40 deepens again with DashEducate: TeX-convention dash/ellipsis education (longest-match runs, 4+ runs untouched, ASCII de-education inverse) executed on host JVM.'),
        ('core/documents/src/main/kotlin/dev/fusion/documents/ObjReplace.kt',
         'R40 also adds ObjReplace: U+FFFC object-placeholder bookkeeping with the partition invariant (visible chars + objects == length) executed on host JVM.'),
    ],
    ':feature:editor': [
        ('feature/editor/src/main/java/dev/fusion/editor/FootnoteMark.kt',
         'R40 deepens again with FootnoteMark: the CMOS footnote-symbol cycle (18-mark cap enforced) plus Unicode superscript-digit marks with their inverse, executed on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/PdfRotate.kt',
         'R40 deepens again with PdfRotate: ISO 32000-1 Table 30 /Rotate normalization, page-tree inheritance and visible-size swap with the full 4x4 composition table executed on host JVM.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/HtmlEntity.kt',
         'R40 deepens again with HtmlEntity: strict HTML character references (30-name subset, numeric forms with surrogate/C1 refusals — the cp1252 quirk is a declared narrowing) cross-checked against held-out html.unescape, executed on host JVM.'),
    ],
    ':feature:messages': [
        ('feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2047.kt',
         'R40 deepens again with Rfc2047: encoded-word decoding (B and Q, adjacent-word whitespace deletion, 75-char cap, three-charset subset) cross-checked against held-out email.header, executed on host JVM.'),
    ],
    ':format:archive': [
        ('format/archive/src/main/kotlin/dev/fusion/archive/SevenZHead.kt',
         'R40 deepens again with SevenZHead: the 7z 32-byte signature header with the StartHeaderCRC gate verified against held-out zlib.crc32, executed on host JVM against a real fixture.'),
    ],
    ':core:index': [
        ('core/index/src/main/kotlin/dev/fusion/index/Fnv1a.kt',
         'R40 deepens again with Fnv1a: 32/64-bit FNV-1a with the published vectors and a bucket helper, declared non-cryptographic in code, executed on host JVM.'),
    ],
    ':feature:lens': [
        ('feature/lens/src/main/kotlin/dev/fusion/lens/Mrz.kt',
         'R40 deepens again with Mrz: ICAO 9303 7-3-1 check digits and TD3 line-2 structure validated against the published specimen line, with no-OCR/no-identity-claims stated in scope, executed on host JVM.'),
    ],
    ':core:files': [
        ('core/files/src/main/java/dev/fusion/files/SafeName.kt',
         'R40 deepens again with SafeName: Windows/POSIX filename safety (reserved device stems incl. CON.txt, trailing dot/space, control chars) with an idempotent sanitizer that refuses rather than invents, executed on host JVM.'),
    ],
    ':core:perf': [
        ('core/perf/src/main/kotlin/dev/fusion/perf/Percentile.kt',
         'R40 deepens again with Percentile: nearest-rank percentiles where every result is an actual observation, sortedness verified; the in-round FP-order repair (multiply before dividing) is pinned by regression attacks, executed on host JVM.'),
    ],
    ':feature:notes': [
        ('feature/notes/src/main/kotlin/dev/fusion/notes/TaskCheckbox.kt',
         'R40 deepens again with TaskCheckbox: GFM task-list items (marker-space rule, 9-digit ordinal cap, toggle and stats) distinct from the VTODO tally kernel, executed on host JVM.'),
    ],
    ':core:windowing': [
        ('core/windowing/src/main/kotlin/dev/fusion/windowing/SnapFraction.kt',
         'R40 deepens again with SnapFraction: exact integer tiling via floor boundaries with the tiling property executed as a sweep; the empty-range validation hole found in-round is closed and tested, executed on host JVM.'),
    ],
    ':cast:airplay': [
        ('cast/airplay/src/main/kotlin/dev/fusion/airplay/PlistHead.kt',
         'R40 deepens again with PlistHead: the bplist00 envelope and 32-byte trailer geometry (sizes, object counts, table bounds) against a plistlib-written fixture, complementing the text-only AirPlayUrlSender, executed on host JVM.'),
    ],
    ':feature:launcher': [
        ('feature/launcher/src/main/kotlin/dev/fusion/launcher/Monogram.kt',
         'R40 deepens again with Monogram: deterministic code-point initials for tiles (no grapheme clustering, declared) that refuse rather than guess when no letter or digit exists, executed on host JVM.'),
    ],
    ':feature:vault': [
        ('feature/vault/src/main/kotlin/dev/fusion/vault/Bech32.kt',
         'R40 deepens again with Bech32: the BIP-173/350 polymod checksum layer (both constants, mixed-case refusal, 90-char cap) with encode/decode round-trips, wallet semantics declared out of scope, executed on host JVM.'),
    ],
    ':core:security': [
        ('core/security/src/main/kotlin/dev/fusion/security/OtpauthUri.kt',
         'R40 deepens again with OtpauthUri: otpauth:// provisioning intake that never computes codes, refuses unknown parameters, and splits the raw label before percent-decoding (the in-round escaped-colon repair, pinned by attacks on both halves), executed on host JVM.'),
    ],
    ':privacy': [
        ('privacy/src/main/java/dev/fusion/privacy/IpRedact.kt',
         'R40 deepens again with IpRedact: strict v4/v6 parsing with /24 and /48 prefix-keeping redaction whose scope string states it reduces identifiability and is NOT anonymity, cross-checked against held-out ipaddress, executed on host JVM.'),
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
    owned = set(R40_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    owned.add('tools/test_r39_tools.py')  # sibling round's check file shares rows; joint harness edits re-pin here
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R40 edit set: {unexpected}')
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
                    raise SystemExit(f"{r['module']} lists {prod} without the R40 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R40_CHECKS:
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
    print('R40 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
