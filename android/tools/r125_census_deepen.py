#!/usr/bin/env python3
"""R125 census deepen: register `XNPV` / `XIRR` / `ACCRINT` / `ACCRINTM` and their oracle corpus.

R125 changes exactly two product files. `FormulaEval.kt` gains dispatch branches for
`XNPV`, `XIRR`, `ACCRINT` and `ACCRINTM` along with four helpers (`xnpv`, `xirr`, `accrint`,
`accrintm`) and a `daysBetween` day-count kernel. `SpillSheetEngine.kt` gains all four names
in its OOXML export prefix list. It changes no build file, no Android source and no existing
function.

The new evidence is a 21-row corpus whose expected column comes from the documented definitions
(net present value for irregular cash flows, internal rate of return via Newton-Raphson,
accrued interest for periodic and at-maturity securities), executed by `:format:ooxml:test`
in CI via a Kotlin JUnit test in the module's own `src/test`, and bounded by the gate and
controls registered here.

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
        'R125 exposes `XNPV`, `XIRR`, `ACCRINT` and `ACCRINTM`, financial functions for '
        'irregular cash flows and accrued interest. `XNPV(rate, values, dates)` returns the '
        'net present value for irregular cash flows; `XIRR(values, dates, [guess])` returns '
        'the internal rate of return; `ACCRINT(issue, first_interest, settlement, rate, par, '
        'frequency, [basis], [calc_method])` returns accrued interest for periodic payments; '
        '`ACCRINTM(issue, settlement, rate, par, [basis])` returns accrued interest at '
        'maturity. Before this round all four names returned #NAME? from a formula; now each '
        'evaluates to the documented value or a defined refusal.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R125 exports `XNPV`, `XIRR`, `ACCRINT` and `ACCRINTM` with the _xlfn. prefix, the '
        'way Excel stores future-function names in OOXML, so formulas written by the engine '
        'round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r125_financial.py',
    'tools/gen_r125_financial_corpus.py',
    'tools/r125_census_deepen.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R125FinancialTest.kt',
    'format/ooxml/src/test/resources/r125/stat_financial_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r125_financial_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r125_financial.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R125 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R125: {stale}')

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
        raise SystemExit(f'R125 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r125 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
