#!/usr/bin/env python3
"""One-shot R35 census deepening: append R35 kernel evidence to 10 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R35 pairing is stale (those must be repaired by hand, not
silently re-pinned). R35 edits no existing source file, so the allowed-stale
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
R35_CHECKS = ['testing/src/r35/kotlin/dev/fusion/testing/R35Checks.kt',
              'tools/test_r35_tools.py', 'tools/check_r35_reference.py']
CMD = 'python3 tools/run_r35_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':feature:lens': ('feature/lens/src/main/kotlin/dev/fusion/lens/EvShift.kt',
        'R35 deepens again with EvShift: exposure-value arithmetic (EV equation plus aperture/shutter solvers and equivalent shifts; ISO outside) executed on host JVM.'),
    ':feature:vault': ('feature/vault/src/main/kotlin/dev/fusion/vault/XorSplit.kt',
        'R35 deepens again with XorSplit: 2-of-2 XOR secret splitting (caller mask, mismatches refused) executed on host JVM.'),
    ':core:documents': ('core/documents/src/main/kotlin/dev/fusion/documents/WordDiff.kt',
        'R35 deepens again with WordDiff: word-level LCS diff over caller tokens (line-diff walk order, capped matrix) executed on host JVM.'),
    ':feature:files': ('feature/files/src/main/kotlin/dev/fusion/filesui/DupGroup.kt',
        'R35 deepens again with DupGroup: duplicate grouping by size+digest with deterministic order (singletons excluded) executed on host JVM.'),
    ':core:index': ('core/index/src/main/kotlin/dev/fusion/index/SnippetCut.kt',
        'R35 deepens again with SnippetCut: match-window snippet cutting with rebased highlight offsets executed on host JVM.'),
    ':feature:media': ('feature/media/src/main/kotlin/dev/fusion/media/Id3Head.kt',
        'R35 deepens again with Id3Head: ID3v2 header reader (per-version flags, syncsafe size, frames outside) executed on host JVM against a real fixture.'),
    ':feature:maps': ('feature/maps/src/main/kotlin/dev/fusion/maps/TileCover.kt',
        'R35 deepens again with TileCover: bbox tile coverage over TileMath (row-major, capped, antimeridian refused) executed on host JVM.'),
    ':feature:continuity': ('feature/continuity/src/main/kotlin/dev/fusion/continuity/VCardParse.kt',
        'R35 deepens again with VCardParse: single-vCard subset parser (VERSION/FN/N/TEL/EMAIL, TYPE only, unknown refused) executed on host JVM.'),
    ':privacy': ('privacy/src/main/java/dev/fusion/privacy/FieldMask.kt',
        'R35 deepens again with FieldMask: allowlist/denylist field projection over flat records executed on host JVM.'),
    ':feature:editor': ('feature/editor/src/main/java/dev/fusion/editor/IndentDetect.kt',
        'R35 deepens again with IndentDetect: tabs-vs-spaces detection with GCD space width (mixed/noise yields null) executed on host JVM.'),
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
        raise SystemExit(f'non-R35 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R35_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R35_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R35 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
