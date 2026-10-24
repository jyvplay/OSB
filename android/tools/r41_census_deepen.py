#!/usr/bin/env python3
"""One-shot R41 census deepening: append R41 kernel evidence to 20 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R41 pairing is stale (those must be repaired by hand, not
silently re-pinned). R41 edits NO existing production sources — all 21 kernels
are new files — so the allowed-stale set is exactly the R41 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R41_CHECKS = ['testing/src/r41/kotlin/dev/fusion/testing/R41Checks.kt',
              'tools/test_r41_tools.py', 'tools/check_r41_reference.py']
CMD = 'python3 tools/run_r41_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R41 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':format:ooxml': [
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt',
         'R41 deepens again with SheetRange: A1 cells and rectangular ranges with corner normalization, containment, intersection and bounding boxes over the full 16384x1048576 grid, executed on host JVM and under Gradle in CI.'),
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/R1c1Ref.kt',
         'R41 also adds R1c1Ref: the R1C1 reference style ColLetters declared out of scope — absolute/relative/mixed forms resolving against an anchor with no grid wrapping, executed on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageGeometry.kt',
         'R41 deepens again with PdfPageGeometry: the CropBox+Rotate composition producing the actual display surface, with /UserUnit refused rather than scaled, executed on host JVM.'),
    ],
    ':core:commands': [
        ('core/commands/src/main/kotlin/dev/fusion/commands/ShlexJoin.kt',
         'R41 deepens again with ShlexJoin: POSIX display quoting (the inverse of ArgSplit) agreeing token-for-token with held-out shlex.quote/join, executed on host JVM.'),
    ],
    ':feature:automation': [
        ('feature/automation/src/main/kotlin/dev/fusion/automation/DurationIso.kt',
         'R41 deepens again with DurationIso: ISO 8601 durations where toSeconds refuses calendar-dependent years/months instead of assuming a calendar, cross-checked against timedelta, executed on host JVM.'),
    ],
    ':feature:maps': [
        ('feature/maps/src/main/kotlin/dev/fusion/maps/DmsFormat.kt',
         'R41 deepens again with DmsFormat: decimal<->DMS with carry-on-round (59.9999 rolls up, never prints 60) and hemisphere-signed parsing, executed on host JVM.'),
    ],
    ':core:model': [
        ('core/model/src/main/kotlin/dev/fusion/model/ColorHex.kt',
         'R41 deepens again with ColorHex: Android-order #(A)RGB hex literals with shorthand doubling and packed-int round-trips; the CSS #RRGGBBAA order is deliberately unparsed, executed on host JVM.'),
    ],
    ':feature:accessibility': [
        ('feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadEase.kt',
         'R41 deepens again with ReadEase: Flesch/Kincaid arithmetic over caller counts with out-of-range scores reported rather than clamped; syllable counting stays outside by declared honesty, executed on host JVM.'),
    ],
    ':feature:lens': [
        ('feature/lens/src/main/kotlin/dev/fusion/lens/Ean13.kt',
         'R41 deepens again with Ean13: GS1 1-3 check-digit algebra (a third checksum family after MRZ 7-3-1 and Luhn) with UPC-A bridging, executed on host JVM.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/DataUri.kt',
         'R41 deepens again with DataUri: RFC 2397 decode-and-describe with the RFC default mediatype surfaced and strict base64/percent paths, cross-checked against held-out base64/urllib, executed on host JVM.'),
    ],
    ':core:index': [
        ('core/index/src/main/kotlin/dev/fusion/index/Trigram.kt',
         'R41 deepens again with Trigram: pg_trgm-style padded trigrams with Jaccard similarity for index candidate retrieval, distinct from find ranking, executed on host JVM.'),
    ],
    ':feature:together': [
        ('feature/together/src/main/kotlin/dev/fusion/together/TurnOrder.kt',
         'R41 deepens again with TurnOrder: a stated-contract rotation kernel (skip sets, single round increment per wrap, all-skipped refusal) whose rules are the executed spec, on host JVM.'),
    ],
    ':core:perf': [
        ('core/perf/src/main/kotlin/dev/fusion/perf/Ewma.kt',
         'R41 deepens again with Ewma: bias-corrected EWMA where the constant-series-equals-constant property holds at every step and the cold-start bias equals (1-a)^t exactly, both executed, on host JVM.'),
    ],
    ':core:settings': [
        ('core/settings/src/main/kotlin/dev/fusion/settings/KeyMigrate.kt',
         'R41 deepens again with KeyMigrate: transitive settings-key renames with cycle refusal and a collision refusal that forbids silent last-writer-wins, idempotence executed, on host JVM.'),
    ],
    ':cast:castv2': [
        ('cast/castv2/src/main/kotlin/dev/fusion/castv2/CastNs.kt',
         'R41 deepens again with CastNs: urn:x-cast: namespace-form validation complementing CastV2Client constants, with well-known classification, executed on host JVM.'),
    ],
    ':core:documents': [
        ('core/documents/src/main/kotlin/dev/fusion/documents/LineWidth.kt',
         'R41 deepens again with LineWidth: a UAX 11 East-Asian-width subset (wide/zero/narrow, ambiguous-narrow declared, controls refused) cross-checked against held-out unicodedata, with fit() that never splits a wide character, executed on host JVM.'),
    ],
    ':core:storage': [
        ('core/storage/src/main/kotlin/dev/fusion/storage/ShardPath.kt',
         'R41 deepens again with ShardPath: git-style digest sharding with canonicality checks and exact inverses, no IO by contract, executed on host JVM.'),
    ],
    ':feature:find': [
        ('feature/find/src/main/java/dev/fusion/find/SpanMerge.kt',
         'R41 deepens again with SpanMerge: half-open highlight-span algebra (touching spans merge, double inversion is identity, cover+gaps=bounds) executed on host JVM.'),
    ],
    ':feature:beam': [
        ('feature/beam/src/main/kotlin/dev/fusion/beam/PiecePlan.kt',
         'R41 deepens again with PiecePlan: BEP 3 piece arithmetic with the power-of-two >=16KiB convention promoted to a refusal and the sizes-sum-to-total property executed, on host JVM.'),
    ],
    ':feature:editor': [
        ('feature/editor/src/main/java/dev/fusion/editor/TabStops.kt',
         'R41 deepens again with TabStops: fixed tab-stop expansion and width where expand() provably removes tabs while preserving column geometry, complementing IndentDetect, executed on host JVM.'),
    ],
    ':feature:launcher': [
        ('feature/launcher/src/main/kotlin/dev/fusion/launcher/GridPos.kt',
         'R41 deepens again with GridPos: row-major grid geometry with RTL visual mirroring and paging, the index<->position bijection executed over full pages in both directions, on host JVM.'),
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
    owned = set(R41_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    owned.add('tools/test_r39_tools.py'); owned.add('tools/test_r40_tools.py')  # sibling round's check file shares rows; joint harness edits re-pin here
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R41 edit set: {unexpected}')
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
                    raise SystemExit(f"{r['module']} lists {prod} without the R41 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R41_CHECKS:
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
    print('R41 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
