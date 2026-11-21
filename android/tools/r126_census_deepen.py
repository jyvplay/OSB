#!/usr/bin/env python3
"""R126 census deepen: register `CONFIDENCE` (legacy alias for CONFIDENCE.NORM) and its oracle corpus.

R126 changes exactly two product files. `FormulaEval.kt` adds `CONFIDENCE` to the existing
`CONFIDENCE.NORM` dispatch branch (one name added to an existing branch). `SpillSheetEngine.kt`
adds `CONFIDENCE` to the OOXML export prefix list. It changes no build file, no Android source
and no existing function.

The new evidence is a 16-row corpus whose expected column comes from the documented definition
CONFIDENCE(alpha, standard_dev, size) = NORM.S.INV(1 − alpha/2) × standard_dev / √size,
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
        'R126 exposes `CONFIDENCE` as a legacy alias for `CONFIDENCE.NORM`. '
        '`CONFIDENCE(alpha, standard_dev, size)` returns the confidence interval for a '
        'population mean using the normal distribution. Before this round `CONFIDENCE` was '
        'listed in the SCOPE string but not dispatched, returning #NAME? from a formula; now '
        'it evaluates identically to `CONFIDENCE.NORM`.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R126 exports `CONFIDENCE` with the _xlfn. prefix alongside `CONFIDENCE.NORM`, the way '
        'Excel stores future-function names in OOXML, so formulas written by the engine '
        'round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r126_confidence.py',
    'tools/gen_r126_confidence_corpus.py',
    'tools/r126_census_deepen.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R126ConfidenceTest.kt',
    'format/ooxml/src/test/resources/r126/confidence_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r126_confidence_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r126_confidence.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R126 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R126: {stale}')

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
        raise SystemExit(f'R126 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r126 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
