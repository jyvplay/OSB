#!/usr/bin/env python3
"""R133 census deepen: register IMCOSH/IMCOT/IMCOTH/VDB/ODDFPRICE/ODDFYIELD/ODDLPRICE/ODDLYIELD
and their oracle corpus in `:format:ooxml`.

R133 changes two product files:
- `FormulaEval.kt` adds 8 functions to the evalCall dispatch, implements them,
  and extends the SCOPE doc string.
- `SpillSheetEngine.kt` adds all 8 names to the OOXML `_xlfn.` export prefix list.

No build file, no Android source, no existing function is changed. The new evidence is a
47-row corpus whose expected column comes from documented formulas, executed by
`:format:ooxml:test` in CI via a Kotlin JUnit test in the module's own `src/test`,
and bounded by the gate and controls registered here.
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
        'R133 adds IMCOSH/IMCOT/IMCOTH complex hyperbolic functions (cosh/cot/coth of '
        'complex numbers), VDB variable-declining-balance depreciation, and '
        'ODDFPRICE/ODDFYIELD/ODDLPRICE/ODDLYIELD odd-coupon-period bond price/yield '
        'functions. All refuse wrong arity with #VALUE! and invalid inputs with #NUM!.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R133 exports IMCOSH/IMCOT/IMCOTH/VDB/ODDFPRICE/ODDFYIELD/ODDLPRICE/ODDLYIELD '
        'with the _xlfn. prefix alongside existing future-function names, so formulas '
        'written by the engine round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r133_remaining.py',
    'tools/gen_r133_remaining_corpus.py',
    'tools/r133_census_deepen.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R133RemainingTest.kt',
    'format/ooxml/src/test/resources/r133/remaining_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r133_remaining_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r133_remaining.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R133 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R133: {stale}')

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
        raise SystemExit(f'R133 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r133 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
