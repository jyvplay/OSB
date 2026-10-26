#!/usr/bin/env python3
"""One-shot R34 census deepening: append R34 kernel evidence to 10 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R34 pairing is stale (those must be repaired by hand, not
silently re-pinned). R34 edits no existing source file, so the allowed-stale
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
R34_CHECKS = ['testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt',
              'tools/test_r34_tools.py', 'tools/check_r34_reference.py']
CMD = 'python3 tools/run_r34_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':feature:calc': ('feature/calc/src/main/kotlin/dev/fusion/calc/StatSummary.kt',
        'R34 deepens again with StatSummary: descriptive statistics (Welford mean/variance, median, min/max; non-finite refused) executed on host JVM.'),
    ':core:files': ('core/files/src/main/java/dev/fusion/files/MimeGuess.kt',
        'R34 deepens again with MimeGuess: extension-to-MIME lookup (case-folded, last-extension rule, unknown returns null) executed on host JVM.'),
    ':feature:notes': ('feature/notes/src/main/java/dev/fusion/notes/OutlineParse.kt',
        'R34 deepens again with OutlineParse: ATX heading outline with fenced-code suppression (Setext out of scope) executed on host JVM.'),
    ':feature:editor': ('feature/editor/src/main/java/dev/fusion/editor/BracketMatch.kt',
        'R34 deepens again with BracketMatch: lexical bracket-mate lookup over parens/brackets/braces (unbalanced returns null) executed on host JVM.'),
    ':cast:dlna': ('cast/dlna/src/main/java/dev/fusion/cast/dlna/SoapFault.kt',
        'R34 deepens again with SoapFault: SOAP 1.1 fault parser (faultcode/string plus UPnPError code/description) executed on host JVM.'),
    ':core:commands': ('core/commands/src/main/kotlin/dev/fusion/commands/PipePlan.kt',
        'R34 deepens again with PipePlan: pipeline splitter honoring quotes and escapes (empty stages refused) executed on host JVM.'),
    ':format:image': ('format/image/src/main/kotlin/dev/fusion/format/image/JpegSeg.kt',
        'R34 deepens again with JpegSeg: JPEG marker walker with SOF0/1/2 dimensions (stops at SOS, entropy never interpreted) executed on host JVM against a real fixture.'),
    ':format:archive': ('format/archive/src/main/kotlin/dev/fusion/archive/ZlibHead.kt',
        'R34 deepens again with ZlibHead: RFC 1950 header reader (CMF/FLG, FCHECK enforced, preset dictionaries refused) executed on host JVM against a real fixture.'),
    ':feature:continuity': ('feature/continuity/src/main/kotlin/dev/fusion/continuity/VTodoParse.kt',
        'R34 deepens again with VTodoParse: single-VTODO parser (status/due/priority/percent, folding, unknown props refused) executed on host JVM.'),
    ':privacy': ('privacy/src/main/java/dev/fusion/privacy/Pseudonym.kt',
        'R34 deepens again with Pseudonym: HMAC-SHA256 pseudonyms with context domain separation (constant-time verify) executed on host JVM with JVM=hmac interop.'),
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
        raise SystemExit(f'non-R34 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R34_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R34_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R34 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
