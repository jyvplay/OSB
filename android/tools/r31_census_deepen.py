#!/usr/bin/env python3
"""One-shot R31 census deepening: append R31 kernel evidence to 10 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R31 pairing is stale (those must be repaired by hand, not
silently re-pinned). R31 edits no existing source file, so the allowed-stale
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
R31_CHECKS = ['testing/src/r31/kotlin/dev/fusion/testing/R31Checks.kt',
              'tools/test_r31_tools.py', 'tools/check_r31_reference.py']
CMD = 'python3 tools/run_r31_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':privacy': ('privacy/src/main/java/dev/fusion/privacy/LogScrubber.kt',
        'R31 deepens with LogScrubber: deterministic PII redaction (emails, phone runs, bearer/token secrets, device paths) with idempotence executed on host JVM against a real fixture.'),
    ':core:capabilities': ('core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityGate.kt',
        'R31 deepens with CapabilityGate: pure feature-enablement decisions over caller-supplied facts with deny-on-unknown and stable reasons executed on host JVM.'),
    ':core:index': ('core/index/src/main/kotlin/dev/fusion/index/SearchQuery.kt',
        'R31 deepens with SearchQuery: boolean query language (terms, phrases, NOT, OR, parens) with self-contained evaluation executed on host JVM.'),
    ':feature:editor': ('feature/editor/src/main/java/dev/fusion/editor/TextOffsets.kt',
        'R31 deepens with TextOffsets: line starts, offset to line/column, and word bounds over plain text executed on host JVM.'),
    ':feature:vault': ('feature/vault/src/main/kotlin/dev/fusion/vault/VaultManifest.kt',
        'R31 deepens with VaultManifest: validated item manifest (separator-free names, sha digests, canonical order-independent digest) executed on host JVM; encryption stays with VaultEnvelope.'),
    ':format:pdf': ('format/pdf/src/main/java/dev/fusion/format/pdf/PdfTrailer.kt',
        'R31 deepens with PdfTrailer: minimal PDF trailer/xref reader (version, Size/Root, startxref, table entries) executed on host JVM against a real fixture.'),
    ':format:psd': ('format/psd/src/main/kotlin/dev/fusion/psd/PsdResources.kt',
        'R31 deepens with PsdResources: walk of the image-resource section the composite engine skips (8BIM blocks, Pascal names, sizes) executed on host JVM against a real fixture.'),
    ':core:hw': ('core/hw/src/main/java/dev/fusion/hw/CodecPreference.kt',
        'R31 deepens with CodecPreference: pure encoder ranking over HwProbe lists (hardware-first, admitted software fallback, reasons) executed on host JVM.'),
    ':core:perf': ('core/perf/src/main/java/dev/fusion/perf/PhaseLedger.kt',
        'R31 deepens with PhaseLedger: named-phase duration ledger (caller clock, totals, shares, slowest) executed on host JVM.'),
    ':feature:browser': ('feature/browser/src/main/kotlin/dev/fusion/browser/model/RobotsRules.kt',
        'R31 deepens again with RobotsRules: robots.txt subset parser (groups, longest-prefix match, tie-goes-Allow) executed on host JVM against a real fixture.'),
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
        raise SystemExit(f'non-R31 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R31_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R31_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R31 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
