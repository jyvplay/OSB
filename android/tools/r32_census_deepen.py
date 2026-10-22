#!/usr/bin/env python3
"""One-shot R32 census deepening: append R32 kernel evidence to 10 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R32 pairing is stale (those must be repaired by hand, not
silently re-pinned). R32 edits no existing source file, so the allowed-stale
set is empty.
"""
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import module_audit
import module_testability as census

ROOT = Path(__file__).resolve().parents[1]
R32_CHECKS = ['testing/src/r32/kotlin/dev/fusion/testing/R32Checks.kt',
              'tools/test_r32_tools.py', 'tools/check_r32_reference.py']
CMD = 'python3 tools/run_r32_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':feature:calc': ('feature/calc/src/main/kotlin/dev/fusion/calc/FractionApprox.kt',
        'R32 deepens with FractionApprox: best rational approximation with bounded denominator via continued fractions (semiconvergent finish, exact-for-fitting) executed on host JVM and cross-checked against an independent implementation.'),
    ':format:lok': ('format/lok/src/main/java/dev/fusion/lok/OfficeCapability.kt',
        'R32 deepens with OfficeCapability: honest bounded Office-format capability matrix (view flags name existing surfaces, every edit flag false with the open gates quoted) executed on host JVM.'),
    ':cast:castv2': ('cast/castv2/src/main/kotlin/dev/fusion/castv2/CastMediaMeta.kt',
        'R32 deepens with CastMediaMeta: validated LOAD metadata (http/https URLs, parameter-free MIME, bounded JSON-safe titles) executed on host JVM; escaping stays the documented open interface.'),
    ':core:commands': ('core/commands/src/main/kotlin/dev/fusion/commands/ArgSplit.kt',
        'R32 deepens again with ArgSplit: shell-ish argv splitter (quotes, escapes, comments) feeding alias expansion, executed on host JVM.'),
    ':core:security': ('core/security/src/main/kotlin/dev/fusion/security/PinRules.kt',
        'R32 deepens again with PinRules: device-PIN strength policy (length, runs, repeats, blocklist) with every violation reported, executed on host JVM.'),
    ':privacy': ('privacy/src/main/java/dev/fusion/privacy/RetentionSweep.kt',
        'R32 deepens again with RetentionSweep: amnesia plumbing partitioning timestamped records into keep/purge by per-kind max ages (caller clock, unknown kinds refused), executed on host JVM.'),
    ':feature:continuity': ('feature/continuity/src/main/kotlin/dev/fusion/continuity/ICalDuration.kt',
        'R32 deepens again with ICalDuration: RFC 5545 durations to seconds (weeks-alone, calendrical months/years refused) executed on host JVM.'),
    ':feature:maps': ('feature/maps/src/main/kotlin/dev/fusion/maps/GeoUri.kt',
        'R32 deepens again with GeoUri: strict RFC 5870 geo-URI subset (wgs84-only, unknown params refused, round-trip format) executed on host JVM.'),
    ':feature:vault': ('feature/vault/src/main/kotlin/dev/fusion/vault/LockoutPolicy.kt',
        'R32 deepens again with LockoutPolicy: brute-force backoff over caller instants (3 free, doubling 30s, saturation) executed on host JVM.'),
    ':feature:media': ('feature/media/src/main/kotlin/dev/fusion/media/CueSheet.kt',
        'R32 deepens again with CueSheetParser: CUE sheet subset to absolute CD-frame cues (global tags tolerated, INDEX 01 increasing) executed on host JVM against a real fixture.'),
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
    if stale:
        raise SystemExit(f'non-R32 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R32_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R32_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R32 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
