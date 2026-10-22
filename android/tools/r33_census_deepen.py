#!/usr/bin/env python3
"""One-shot R33 census deepening: append R33 kernel evidence to 10 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R33 pairing is stale (those must be repaired by hand, not
silently re-pinned). R33 edits no existing source file, so the allowed-stale
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
R33_CHECKS = ['testing/src/r33/kotlin/dev/fusion/testing/R33Checks.kt',
              'tools/test_r33_tools.py', 'tools/check_r33_reference.py']
CMD = 'python3 tools/run_r33_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':feature:messages': ('feature/messages/src/main/kotlin/dev/fusion/messages/E164Format.kt',
        'R33 deepens again with E164Format: grammar-only E.164 normalization (separators stripped, 00/trunk handling, no numbering-plan guessing) executed on host JVM.'),
    ':feature:terminal': ('feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiSgr.kt',
        'R33 deepens again with AnsiSgr: SGR styling subset (attributes, 8/16/256/truecolor, unknown codes refused) plus SGR-only stripping executed on host JVM.'),
    ':feature:beam': ('feature/beam/src/main/kotlin/dev/fusion/beam/ChunkPlan.kt',
        'R33 deepens again with ChunkPlan: fixed-size byte-range planning with validated resume subtraction executed on host JVM.'),
    ':feature:files': ('feature/files/src/main/kotlin/dev/fusion/filesui/GlobMatch.kt',
        'R33 deepens again with GlobMatch: glob subset over file names (stars, classes, escapes; linear DP, no backtracking blowup) executed on host JVM.'),
    ':feature:find': ('feature/find/src/main/kotlin/dev/fusion/find/FuzzyScore.kt',
        'R33 deepens again with FuzzyScore: greedy subsequence fuzzy scoring (word-start/camel/adjacency bonuses, gap penalties) executed on host JVM.'),
    ':feature:together': ('feature/together/src/main/kotlin/dev/fusion/together/VectorClock.kt',
        'R33 deepens again with VectorClocks: increment/merge/compare over caller counter maps (overflow refused, canonical outputs) executed on host JVM.'),
    ':core:documents': ('core/documents/src/main/kotlin/dev/fusion/documents/DiffHunks.kt',
        'R33 deepens again with DiffHunks: unified-hunk rendering over LineDiff output (context merge, unified headers) executed on host JVM.'),
    ':format:image': ('format/image/src/main/kotlin/dev/fusion/format/image/BmpInfo.kt',
        'R33 deepens again with BmpInspect: BMP header reader (BITMAPINFOHEADER, top-down orientation, RLE refused, no pixels decoded) executed on host JVM against a real fixture.'),
    ':format:archive': ('format/archive/src/main/kotlin/dev/fusion/archive/GzipHead.kt',
        'R33 deepens again with GzipInspect: gzip member-header reader (flag walk, FNAME capture, FHCRC verification) executed on host JVM against real fixtures.'),
    ':feature:vault': ('feature/vault/src/main/kotlin/dev/fusion/vault/TotpCode.kt',
        'R33 deepens again with TotpCode: RFC 6238 TOTP over caller instants (base32 secrets, ±window verify, all six RFC vectors) executed on host JVM.'),
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
        raise SystemExit(f'non-R33 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R33_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R33_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R33 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
