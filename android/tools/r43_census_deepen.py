#!/usr/bin/env python3
"""One-shot R43 census deepening: append R43 kernel evidence to 23 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R43 pairing is stale (those must be repaired by hand, not
silently re-pinned). R43 edits NO existing production sources — all 23 kernels
are new files — so the allowed-stale set is exactly the R43 kernel/consumer
set and nothing else. Idempotent: a second run reproduces the same document.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R43_CHECKS = ['testing/src/r43/kotlin/dev/fusion/testing/R43Checks.kt',
              'tools/test_r43_tools.py', 'tools/check_r43_reference.py']
CMD = 'python3 tools/run_r43_contracts.py (host, needs KOTLINC/JAVA_HOME)'

# R43 touches no existing production file; TOUCHED is intentionally empty.
TOUCHED = {}

DEEPEN = {
    ':format:ooxml': [
        ('format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetFreeze.kt',
         'R43 deepens again with SheetFreeze: frozen-pane cell classification and scroll-home geometry, with the cut twips formula explicitly upheld as cut, executed on host JVM and under Gradle in CI.'),
    ],
    ':core:documents': [
        ('core/documents/src/main/kotlin/dev/fusion/documents/BidiStrip.kt',
         'R43 deepens again with BidiStrip: Trojan-Source bidi-control detection, stripping and balance audit, with stripping pinned as a user-visible choice, executed on host JVM.'),
    ],
    ':feature:editor': [
        ('feature/editor/src/main/java/dev/fusion/editor/DupLines.kt',
         'R43 deepens again with DupLines: exact duplicate-line groups and keep-first removal with no silent normalization, executed on host JVM.'),
    ],
    ':format:pdf': [
        ('format/pdf/src/main/java/dev/fusion/format/pdf/PdfPerm.kt',
         'R43 deepens again with PdfPerm: Table 22 permission-bit decode/encode declared as viewer policy, never enforcement, executed on host JVM.'),
    ],
    ':feature:browser': [
        ('feature/browser/src/main/kotlin/dev/fusion/browser/model/RefererPolicy.kt',
         'R43 deepens again with RefererPolicy: the full eight-token referrer decision table incl. the downgrade rules, executed on host JVM.'),
    ],
    ':format:archive': [
        ('format/archive/src/main/kotlin/dev/fusion/archive/DebControl.kt',
         'R43 deepens again with DebControl: Debian control-paragraph fields with continuations and dot markers, complementing ArHead, executed on host JVM.'),
    ],
    ':core:capabilities': [
        ('core/capabilities/src/main/java/dev/fusion/capabilities/ApiName.kt',
         'R43 deepens again with ApiName: the pinned 21..36 API naming table that refuses unreleased levels rather than guessing, executed on host JVM.'),
    ],
    ':feature:together': [
        ('feature/together/src/main/kotlin/dev/fusion/together/SeatColor.kt',
         'R43 deepens again with SeatColor: golden-angle hue assignment with executed separation properties; RGB stays in HslColor, executed on host JVM.'),
    ],
    ':feature:media': [
        ('feature/media/src/main/kotlin/dev/fusion/media/Bpm.kt',
         'R43 deepens again with Bpm: tempo arithmetic and median tap estimation whose robustness-to-one-miss is the tested point, executed on host JVM.'),
    ],
    ':feature:beam': [
        ('feature/beam/src/main/kotlin/dev/fusion/beam/TrackerTier.kt',
         'R43 deepens again with TrackerTier: BEP 12 tier semantics with in-tier dedupe; the whitespace-URL hole found by first execution was repaired and the attack kept, executed on host JVM.'),
    ],
    ':feature:lens': [
        ('feature/lens/src/main/kotlin/dev/fusion/lens/QrVersion.kt',
         'R43 deepens again with QrVersion: the 17+4v lattice law with full 1..40 bijection executed; capacities/EC stay out of scope, on host JVM.'),
    ],
    ':core:security': [
        ('core/security/src/main/kotlin/dev/fusion/security/RpIdMatch.kt',
         'R43 deepens again with RpIdMatch: WebAuthn RP-ID shape and dot-boundary matching whose result type admits the PSL rule is NOT applied here, executed on host JVM.'),
    ],
    ':core:model': [
        ('core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt',
         'R43 deepens again with VersionRange: caret/tilde ranges over SemVer with exclusive upper bounds and prerelease bounds refused, executed on host JVM.'),
    ],
    ':feature:find': [
        ('feature/find/src/main/java/dev/fusion/find/SnippetWindow.kt',
         'R43 deepens again with SnippetWindow: max-coverage snippet selection with smallest-start ties and a brute-force agreement property, executed on host JVM.'),
    ],
    ':feature:vault': [
        ('feature/vault/src/main/kotlin/dev/fusion/vault/AgeId.kt',
         'R43 deepens again with AgeId: age recipient/identity recognition via Bech32 plus strict 5-to-8 regrouping, zero cryptography by design, executed on host JVM.'),
    ],
    ':feature:files': [
        ('feature/files/src/main/kotlin/dev/fusion/filesui/BreadcrumbTrim.kt',
         'R43 deepens again with BreadcrumbTrim: component-boundary breadcrumb trimming with a first+ellipsis+last floor and over-budget reported, executed on host JVM.'),
    ],
    ':core:perf': [
        ('core/perf/src/main/kotlin/dev/fusion/perf/Apdex.kt',
         'R43 deepens again with Apdex: the published score formula and rating bands with the 4T line fixed by spec, executed on host JVM.'),
    ],
    ':feature:calc': [
        ('feature/calc/src/main/kotlin/dev/fusion/calc/PercentChange.kt',
         'R43 deepens again with PercentChange: explicit percent/basis-point change restricted to positive bases (the restriction preserves the executed apply-inverse), executed on host JVM.'),
    ],
    ':cast:airplay': [
        ('cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspTransport.kt',
         'R43 deepens again with RtspTransport: the RTP/AVP transport-spec subset with unknown parameters kept verbatim for interop, executed on host JVM.'),
    ],
    ':feature:messages': [
        ('feature/messages/src/main/kotlin/dev/fusion/messages/MsgIdParse.kt',
         'R43 deepens again with MsgIdParse: dot-atom Message-ID shapes for threading, with the rare RFC forms refused by name, executed on host JVM.'),
    ],
    ':privacy': [
        ('privacy/src/main/java/dev/fusion/privacy/TimeCoarse.kt',
         'R43 deepens again with TimeCoarse: UTC timestamp coarsening whose scope states it reduces precision and is NOT anonymity, executed on host JVM.'),
    ],
    ':core:windowing': [
        ('core/windowing/src/main/kotlin/dev/fusion/windowing/AspectSnap.kt',
         'R43 deepens again with AspectSnap: symmetric log-distance ratio snapping over a pinned table, executed on host JVM.'),
    ],
    ':feature:alerts': [
        ('feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertKey.kt',
         'R43 deepens again with AlertKey: canonical dedupe keys whose pipe refusal keeps keys stable forever, executed on host JVM.'),
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
    owned = set(R43_CHECKS) | {prod for pairs in DEEPEN.values() for prod, _ in pairs} | set(TOUCHED)
    owned.add('tools/test_r39_tools.py'); owned.add('tools/test_r40_tools.py'); owned.add('tools/test_r41_tools.py'); owned.add('tools/test_r42_tools.py')  # sibling round's check file shares rows; joint harness edits re-pin here
    unexpected = [s for s in stale if s[1] not in owned]
    if unexpected:
        raise SystemExit(f'stale pairings outside the declared R43 edit set: {unexpected}')
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
                    raise SystemExit(f"{r['module']} lists {prod} without the R43 sentence")
            else:
                r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
                r['production'].append(prod)
        for c in R43_CHECKS:
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
    print('R43 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))


if __name__ == '__main__':
    main()
