#!/usr/bin/env python3
"""R124 census deepen: register `NORM.S.DIST` / `NORM.S.INV` / `BINOM.DIST` / `POISSON.DIST` /
`EXPON.DIST` / `CONFIDENCE.NORM` and their oracle corpus.

R124 changes exactly two product files. `FormulaEval.kt` gains dispatch branches for
`NORM.S.DIST`, `NORM.S.INV`, `BINOM.DIST`, `POISSON.DIST`, `EXPON.DIST` and `CONFIDENCE.NORM`
along with six helpers (`normSDist`, `normSInv`, `binomDist`, `poissonDist`, `exponDist`,
`confidenceNorm`) and a `lgamma` Lanczos kernel. `SpillSheetEngine.kt` gains all six names
in its OOXML export prefix list. It changes no build file, no Android source and no existing
function.

The new evidence is a 73-row corpus whose expected column comes from the documented definitions
(standard normal CDF/PDF, inverse normal CDF via Acklam, binomial via lgamma sum, Poisson via
regularized incomplete gamma, exponential via simple exp, confidence interval via inverse normal),
executed by `:format:ooxml:test` in CI via a Kotlin JUnit test in the module's own `src/test`,
and bounded by the gate and controls registered here.

As with every earlier deepen script, any pinned file outside this list that has drifted aborts
the run.
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
        'R124 exposes `NORM.S.DIST`, `NORM.S.INV`, `BINOM.DIST`, `POISSON.DIST`, `EXPON.DIST` '
        'and `CONFIDENCE.NORM`, distribution functions. `NORM.S.DIST(x, cumulative)` returns '
        'the standard normal CDF or PDF; `NORM.S.INV(p)` returns the inverse standard normal '
        'CDF (Acklam); `BINOM.DIST(k, n, p, cumulative)` returns the binomial PMF or CDF; '
        '`POISSON.DIST(x, mean, cumulative)` returns the Poisson PMF or CDF; `EXPON.DIST(x, '
        'lambda, cumulative)` returns the exponential PDF or CDF; `CONFIDENCE.NORM(alpha, '
        'std_dev, size)` returns the confidence interval for a population mean. Before this '
        'round all six names returned #NAME? from a formula; now each evaluates to the '
        'documented value or a defined refusal.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R124 exports `NORM.S.DIST`, `NORM.S.INV`, `BINOM.DIST`, `POISSON.DIST`, '
        '`EXPON.DIST` and `CONFIDENCE.NORM` with the _xlfn. prefix, the way Excel stores '
        'future-function names in OOXML, so formulas written by the engine round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r124_dist.py',
    'tools/gen_r124_dist_corpus.py',
    'tools/r124_census_deepen.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R124DistTest.kt',
    'format/ooxml/src/test/resources/r124/stat_dist_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r124_dist_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r124_dist.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R124 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R124: {stale}')

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
        raise SystemExit(f'R124 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r124 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
