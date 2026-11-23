#!/usr/bin/env python3
"""R128 census deepen: register `T` and `N` value conversion functions
and their oracle corpus in `:format:ooxml`.

R128 changes three product files:
- `FormulaEval.kt` adds T/N to the evalCall dispatch (two names added to existing
  branches) and extends the SCOPE doc string.
- `SpillSheetEngine.kt` adds T/N to the OOXML `_xlfn.` export prefix list.

No build file, no Android source, no existing function is changed. The new evidence is a
20-row corpus whose expected column comes from documented definitions, executed by
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
        'R128 adds T (text or empty) and N (number or zero) value conversion functions. '
        'T returns the text if its argument is text, otherwise empty string. N returns '
        'the number if its argument is a number, 1 for TRUE, 0 for FALSE/empty/unparseable '
        'text, and propagates errors. Both refuse wrong arity with #VALUE!.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R128 exports T and N with the _xlfn. prefix alongside existing '
        'future-function names, so formulas written by the engine round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r128_textnum.py',
    'tools/gen_r128_textnum_corpus.py',
    'tools/r128_census_deepen.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R128TextNumTest.kt',
    'format/ooxml/src/test/resources/r128/textnum_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r128_textnum_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r128_textnum.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R128 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R128: {stale}')

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
        raise SystemExit(f'R128 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r128 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
