#!/usr/bin/env python3
"""R122 census deepen: register `PHI` / `DELTA` / `GESTEP` and their oracle corpus.

R122 changes exactly two product files. `FormulaEval.kt` gains dispatch branches for
`PHI`, `DELTA` and `GESTEP`; `SpillSheetEngine.kt` gains all three names in its
OOXML export prefix list, because Excel stores future-function names with the `_xlfn.`
prefix. It changes no build file, no Android source and no existing function.

The new evidence is a 109-row corpus whose expected column comes from the documented
definitions (standard normal PDF for PHI, equality test for DELTA, threshold test for
GESTEP), executed by `:format:ooxml:test` in CI via a Kotlin JUnit test in the module's
own `src/test`, and bounded by the gate and controls registered here.

As with every earlier deepen script, any pinned file outside this list that has drifted
aborts the run.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TOUCHED: dict[str, tuple[str, str]] = {
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt': (
        ':format:ooxml',
        'R122 exposes `PHI`, `DELTA` and `GESTEP`. `PHI(x)` returns the standard normal '
        'probability density exp(-x^2/2)/sqrt(2*pi); `DELTA(a, b)` returns 1 if a == b '
        'else 0; `GESTEP(x, [step])` returns 1 if x >= step else 0, with step defaulting '
        'to 0. Before this round all three names returned #NAME? from a formula; now each '
        'evaluates to the documented value or a defined refusal (#VALUE! for wrong arity '
        'or non-numeric arguments).',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R122 exports `PHI`, `DELTA` and `GESTEP` with the _xlfn. prefix, the way Excel '
        'stores future-function names in OOXML, so formulas written by the engine '
        'round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r122_phi.py',
    'tools/gen_r122_phi_corpus.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R122PhiTest.kt',
    'format/ooxml/src/test/resources/r122/phi_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r122_phi_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r122_phi.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R122 file: {path}'
    census = ROOT / 'docs/module-testability.json'
    data = json.loads(census.read_text(encoding='utf-8'))

    owned = set(TOUCHED) | set(CHECKS)
    stale = sorted(
        (row['module'], path)
        for row in data['rows']
        for path, pinned in row['sha256'].items()
        if path not in owned and sha(path) != pinned
    )
    if stale:
        raise SystemExit(f'refusing to re-pin files outside R122: {stale}')

    for row in data['rows']:
        module = row['module']
        for path, (owner, note) in TOUCHED.items():
            if module != owner:
                continue
            if path not in row['production']:
                row['production'].append(path)
            if note not in row['behavior']:
                row['behavior'] = row['behavior'].rstrip() + ' ' + note
            row['sha256'][path] = sha(path)
        if module not in COMMANDS:
            continue
        for check in CHECKS:
            if check not in row['checks']:
                row['checks'].append(check)
            row['sha256'][check] = sha(check)
        for command in COMMANDS[module]:
            if command not in row['command']:
                row['command'] = row['command'].rstrip() + '; ' + command

    missing = sorted(path for path in CHECKS if not any(path in row['sha256'] for row in data['rows']))
    if missing:
        raise SystemExit(f'R122 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r122 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
