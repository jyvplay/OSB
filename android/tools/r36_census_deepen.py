#!/usr/bin/env python3
"""One-shot R36 census deepening: append R36 kernel evidence to 12 rows of docs/module-testability.json.

Regenerates docs/MODULE-TESTABILITY.md via module_testability.render. Fails
loudly if any non-R36 pairing is stale (those must be repaired by hand, not
silently re-pinned). R36 edits no existing source file, so the allowed-stale
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
R36_CHECKS = ['testing/src/r36/kotlin/dev/fusion/testing/R36Checks.kt',
              'tools/test_r36_tools.py', 'tools/check_r36_reference.py']
CMD = 'python3 tools/run_r36_contracts.py (host, needs KOTLINC/JAVA_HOME)'

DEEPEN = {
    ':feature:calc': ('feature/calc/src/main/kotlin/dev/fusion/calc/RpnEval.kt',
        'R36 deepens again with RpnEval: RPN stack evaluation (+ - * / neg sqrt over finite doubles; underflow/leftover refused) executed on host JVM.'),
    ':feature:files': ('feature/files/src/main/kotlin/dev/fusion/filesui/NameClash.kt',
        'R36 deepens again with NameClash: copy-name disambiguation with existing-suffix reuse and lowest-free gap fill executed on host JVM.'),
    ':feature:editor': ('feature/editor/src/main/java/dev/fusion/editor/WrapCol.kt',
        'R36 deepens again with WrapCol: greedy word wrap with space collapsing (overlong words and control chars refused) executed on host JVM.'),
    ':core:commands': ('core/commands/src/main/kotlin/dev/fusion/commands/RedirPlan.kt',
        'R36 deepens again with RedirPlan: shell redirect planning (< > >> 2>, one per stream, source order kept) executed on host JVM.'),
    ':cast:castv2': ('cast/castv2/src/main/kotlin/dev/fusion/castv2/CastState.kt',
        'R36 deepens again with CastState: CastV2 player-state machine (4 states, 11 legal transitions, anything else refused) executed on host JVM.'),
    ':feature:maps': ('feature/maps/src/main/kotlin/dev/fusion/maps/ScaleBar.kt',
        'R36 deepens again with ScaleBar: Web-Mercator ground resolution plus largest-fitting 1/2/5 scale bar executed on host JVM.'),
    ':feature:media': ('feature/media/src/main/kotlin/dev/fusion/media/WavHead.kt',
        'R36 deepens again with WavHead: canonical WAV header reader (PCM/float, geometry cross-checked) executed on host JVM against a real fixture.'),
    ':format:image': ('format/image/src/main/kotlin/dev/fusion/format/image/TiffHead.kt',
        'R36 deepens again with TiffHead: TIFF header reader (either byte order, magic 42, even IFD offset) executed on host JVM against a real fixture.'),
    ':privacy': ('privacy/src/main/java/dev/fusion/privacy/KAnon.kt',
        'R36 deepens again with KAnon: k-anonymity auditing (equivalence groups, insertion-ordered violations) executed on host JVM.'),
    ':core:security': ('core/security/src/main/kotlin/dev/fusion/security/AttemptWindow.kt',
        'R36 deepens again with AttemptWindow: sliding-window attempt limiting (boundary expired, future ignored) executed on host JVM.'),
    ':feature:lens': ('feature/lens/src/main/kotlin/dev/fusion/lens/DofNear.kt',
        'R36 deepens again with DofNear: thin-lens depth-of-field limits via the hyperfocal distance (far infinity as null) executed on host JVM.'),
    ':feature:vault': ('feature/vault/src/main/kotlin/dev/fusion/vault/ShannonBits.kt',
        'R36 deepens again with ShannonBits: Shannon entropy in bits per symbol over raw bytes executed on host JVM.'),
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
        raise SystemExit(f'non-R36 stale pairings (repair by hand): {stale}')
    for r in data['rows']:
        if r['module'] not in DEEPEN:
            continue
        prod, sentence = DEEPEN[r['module']]
        assert prod not in r['production'], prod
        r['behavior'] = r['behavior'].rstrip() + ' ' + sentence
        r['production'].append(prod)
        for c in R36_CHECKS:
            if c not in r['checks']:
                r['checks'].append(c)
        if CMD not in r['command']:
            r['command'] = (r['command'] + '; ' + CMD) if r['command'] else CMD
        r['sha256'][prod] = sha(prod)
        for c in R36_CHECKS:
            r['sha256'][c] = sha(c)
        for p in r['production'] + r['checks']:
            r['sha256'][p] = sha(p)
    census.validate(data)
    jp.write_text(json.dumps(data, indent=2) + '\n')
    (ROOT / 'docs/MODULE-TESTABILITY.md').write_text(census.render(data))
    census.validate(json.loads(jp.read_text()))
    assert census.render(json.loads(jp.read_text())) == (ROOT / 'docs/MODULE-TESTABILITY.md').read_text()
    assert (ROOT / 'docs/MODULE-TESTABILITY.md').read_text().endswith('\n')
    print('R36 census deepen OK:', json.dumps(census.counts(json.loads(jp.read_text()))))

if __name__ == '__main__':
    main()
