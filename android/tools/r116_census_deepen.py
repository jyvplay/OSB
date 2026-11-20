#!/usr/bin/env python3
"""R116 census deepen: register the trigonometry the formula engine now exposes and its oracle corpus.

R116 changes exactly one product file. `FormulaEval.kt` gains fourteen names — `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`,
`ATAN`, `ATAN2`, `SINH`, `COSH`, `TANH`, `ASINH`, `ACOSH`, `ATANH`, `PI` — and one helper. It changes no build file, no
Android source and no existing function: the reciprocal kernels (`SEC`/`CSC`/`COT`, `SECH`/`CSCH`/`COTH`) and their
inverses (`ACOT`/`ACOTH`) are pinned unchanged by rule R116-7, because they are the callers this round exists to
complete rather than to touch.

The new evidence is a corpus whose expected column comes from CPython's `math` (the platform C library) rather than
from Kotlin (the JVM's fdlibm port), a Kotlin JUnit test in the module's own `src/test` that the existing
`:format:ooxml:test` CI step executes, and the gate and controls registered here.

As with every earlier deepen script, any pinned file outside this list that has drifted aborts the run.
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
        'R116 exposes the trigonometry the reciprocal kernels already called internally: SIN/COS/TAN/ASIN/ACOS/ATAN/'
        'ATAN2/SINH/COSH/TANH/ASINH/ACOSH/ATANH and PI(), with #NUM! domains, #DIV/0! for ATAN2(0,0) and the '
        'spreadsheet argument order for ATAN2(x_num, y_num). Before this, a sheet could evaluate SEC(x) and not COS(x). '
        'The evidence is a 534-row corpus whose expected column is CPython `math` (platform libm) against the JVM '
        'fdlibm port, executed by :format:ooxml:test in CI.',
    ),
}
CHECKS = [
    'tools/check_r116_trig.py',
    'tools/test_r116_trig.py',
    'tools/gen_r116_trig_corpus.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/TrigFunctionTest.kt',
    'format/ooxml/src/test/resources/r116/trig_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r116_trig_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r116_trig.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r116_trig',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R116 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R116: {stale}')

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
        raise SystemExit(f'R116 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r116 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
