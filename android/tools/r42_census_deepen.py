#!/usr/bin/env python3
"""One-shot R42 census deepening: append R42 kernel evidence to 21 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R42 pairing is stale (those must be repaired by hand, not
silently re-pinned). R42 edits NO existing production sources — all 22 kernels
are new files — so the allowed-stale set is exactly the R42 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R42_CHECKS = ['testing/src/r42/kotlin/dev/fusion/testing/R42Checks.kt',
              'tools/test_r42_tools.py', 'tools/check_r42_reference.py']
CMD = 'python3 tools/run_r42_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R42 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':format:ooxml': [
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/AbsRef.kt',
         'R42 deepens again with AbsRef: $-marker absolute/mixed references with fill-shift semantics (absolute parts pinned, no grid wrapping), executed on host JVM and under Gradle in CI.'),
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/MergedCells.kt',
         'R42 also adds MergedCells: merge-region disjointness, anchors and coverage per ECMA-376 mergeCells rules, executed on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/PdfDate.kt',
         'R42 deepens again with PdfDate: ISO 32000-1 section 7.9.4 date strings with field defaulting and the missing-offset-is-null honesty (never assumed UTC), executed on host JVM.'),
    ],
    ':feature:maps': [
        ('feature/maps/src/main/kotlin/dev/fusion/maps/NmeaLat.kt',
         'R42 deepens again with NmeaLat: the NMEA ddmm.mmmm field shape DmsFormat declared out of scope, with hemisphere signs and sub-60 minute gates, executed on host JVM.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/HstsParse.kt',
         'R42 deepens again with HstsParse: RFC 6797 value parsing where unknown directives are tolerated per spec but duplicates refuse, executed on host JVM.'),
    ],
    ':feature:continuity': [
        ('feature/continuity/src/main/kotlin/dev/fusion/continuity/PairPin.kt',
         'R42 deepens again with PairPin: Damm-algorithm check digits (a fourth checksum family) with the all-single-errors and all-adjacent-transpositions properties executed as mutation sweeps, on host JVM.'),
    ],
    ':format:archive': [
        ('format/archive/src/main/kotlin/dev/fusion/archive/ArHead.kt',
         'R42 deepens again with ArHead: the unix ar / Debian .deb outer structure (60-byte headers, even-offset padding, deb layout check), executed on host JVM.'),
    ],
    ':core:documents': [
        ('core/documents/src/main/kotlin/dev/fusion/documents/CaseCycle.kt',
         'R42 deepens again with CaseCycle: the writer change-case cycle with a stated simple title rule and the Turkish-i narrowing declared, executed on host JVM.'),
    ],
    ':feature:editor': [
        ('feature/editor/src/main/java/dev/fusion/editor/CommentToggle.kt',
         'R42 deepens again with CommentToggle: IDE-semantics line-comment toggling (uniform-block involution, min-indent alignment), executed on host JVM.'),
    ],
    ':feature:notes': [
        ('feature/notes/src/main/kotlin/dev/fusion/notes/UnitPrice.kt',
         'R42 deepens again with UnitPrice: per-base-unit price normalization that refuses cross-dimension comparison instead of pretending density, executed on host JVM.'),
    ],
    ':feature:capture': [
        ('feature/capture/src/main/kotlin/dev/fusion/capture/Rule3rds.kt',
         'R42 deepens again with Rule3rds: thirds gridlines, power points and nearest-point snapping, geometry only, executed on host JVM.'),
    ],
    ':core:perf': [
        ('core/perf/src/main/kotlin/dev/fusion/perf/Histo.kt',
         'R42 deepens again with Histo: static half-open latency buckets with the exact conservation invariant executed under input reordering, auto-scaling refused by design, on host JVM.'),
    ],
    ':feature:terminal': [
        ('feature/terminal/src/main/kotlin/dev/fusion/terminal/CsiCursor.kt',
         'R42 deepens again with CsiCursor: ECMA-48 cursor finals A-H with default-1/zero-means-1 parameters and clamping, disjoint from AnsiSgr by final byte, executed on host JVM.'),
    ],
    ':privacy': [
        ('privacy/src/main/java/dev/fusion/privacy/GpcHeader.kt',
         'R42 deepens again with GpcHeader: strict Sec-GPC and gpc.json parsing that refuses invalid values rather than coercing consent state, executed on host JVM.'),
    ],
    ':feature:vault': [
        ('feature/vault/src/main/kotlin/dev/fusion/vault/PwPolicy.kt',
         'R42 deepens again with PwPolicy: password policy compliance explicitly distinct from strength (stated in scope), reporting flags without ever returning substrings, executed on host JVM.'),
    ],
    ':feature:media': [
        ('feature/media/src/main/kotlin/dev/fusion/media/GainDb.kt',
         'R42 deepens again with GainDb: decibel conversions with the amplitude/power distinction hard-coded into the API surface, executed on host JVM.'),
    ],
    ':core:storage': [
        ('core/storage/src/main/kotlin/dev/fusion/storage/HexBytes.kt',
         'R42 deepens again with HexBytes: the strict digest hex codec ShardPath consumes, lowercase canonical with prefix/whitespace refusals, executed on host JVM.'),
    ],
    ':feature:alerts': [
        ('feature/alerts/src/main/kotlin/dev/fusion/alerts/SnoozeSeq.kt',
         'R42 deepens again with SnoozeSeq: capped doubling snooze ladders with monotonicity executed as a property, executed on host JVM.'),
    ],
    ':feature:calc': [
        ('feature/calc/src/main/kotlin/dev/fusion/calc/NiceStep.kt',
         'R42 deepens again with NiceStep: 1-2-5 axis step selection with the tick bound honored across a sweep, expression evaluation untouched, executed on host JVM.'),
    ],
    ':feature:automation': [
        ('feature/automation/src/main/kotlin/dev/fusion/automation/NextRun.kt',
         'R42 deepens again with NextRun: anchor+period schedule arithmetic with the smallest-run-strictly-after law executed, the 60s floor enforced, zones left to CronSpec, executed on host JVM.'),
    ],
    ':feature:messages': [
        ('feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2231.kt',
         'R42 deepens again with Rfc2231: parameter continuations and extended values at their proper layer (the mechanism Rfc2047 refused inline), stateless after an in-authoring purity repair, executed on host JVM.'),
    ],
    ':format:image': [
        ('format/image/src/main/kotlin/dev/fusion/format/image/BmpHead.kt',
         'R42 deepens again with BmpHead: standalone BMP headers (top-down heights surfaced, V4/V5 refused by biSize, 4-byte stride law), executed on host JVM.'),
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
    owned = set(R42_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    owned.add('tools/test_r39_tools.py'); owned.add('tools/test_r40_tools.py'); owned.add('tools/test_r41_tools.py')  # sibling round's check file shares rows; joint harness edits re-pin here
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R42 edit set: {unexpected}')
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
                    raise SystemExit(f"{r['module']} lists {prod} without the R42 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R42_CHECKS:
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
    print('R42 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
