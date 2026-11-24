#!/usr/bin/env python3
"""R138 census deepen: register IMCSCH/IMLOG2/IMLOG10 complex functions and
its oracle corpus in `:format:ooxml`.

R138 changes two product files:
- `FormulaEval.kt` adds IMCSCH, IMLOG2, IMLOG10 to the R53 scalar function dispatch
  table and extends the SCOPE doc string.
- `SpillSheetEngine.kt` adds IMCSCH, IMLOG2, IMLOG10 to the `_xlfn.` export list.

No build file, no Android source, no existing function is changed. The new evidence is a
20-row corpus whose expected column comes from documented complex-number semantics, executed
by `:format:ooxml:test` in CI via a Kotlin JUnit test in the module's own `src/test`,
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
        'R138 adds IMCSCH/IMLOG2/IMLOG10 complex functions (cosecant, base-2 and base-10 '
        'logarithm of complex numbers in x+yi or x+yj format).',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R138 exports IMCSCH, IMLOG2, IMLOG10 with the _xlfn. prefix alongside existing '
        'future-function names, so formulas written by the engine round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r138_complex_more.py',
    'tools/gen_r138_complex_more_corpus.py',
    'tools/r138_census_deepen.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R138ComplexMoreTest.kt',
    'format/ooxml/src/test/resources/r138/complex_more_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r138_complex_more_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r138_complex_more.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R138 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R138: {stale}')

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
        raise SystemExit(f'R138 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r138 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
