#!/usr/bin/env python3
"""R123 census deepen: register `CHISQ.TEST` / `F.TEST` / `Z.TEST` / `T.TEST` / `SKEW.P` and their oracle corpus.

R123 changes exactly two product files. `FormulaEval.kt` gains dispatch branches for
`CHISQ.TEST`, `F.TEST`, `Z.TEST`, `T.TEST` and `SKEW.P`; `SpillSheetEngine.kt` gains all five
names in its OOXML export prefix list, because Excel stores future-function names with the
`_xlfn.` prefix. It changes no build file, no Android source and no existing function.

The new evidence is a 100-row corpus whose expected column comes from the documented definitions
(chi-square test of independence, two-sample F test, one-sample Z test, Student's t test,
population skewness), executed by `:format:ooxml:test` in CI via a Kotlin JUnit test in the
module's own `src/test`, and bounded by the gate and controls registered here.

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
        'R123 exposes `CHISQ.TEST`, `F.TEST`, `Z.TEST`, `T.TEST` and `SKEW.P`, statistical '
        'test functions. `CHISQ.TEST(actual, expected)` returns the chi-square test of '
        'independence p-value; `F.TEST(array1, array2)` returns the two-sample F test p-value; '
        '`Z.TEST(array, x, [sigma])` returns the one-sample Z test p-value; `T.TEST(array1, '
        'array2, tails, type)` returns Student\'s t test p-value; `SKEW.P(number1, ...)` '
        'returns population skewness. Before this round all five names returned #NAME? from a '
        'formula; now each evaluates to the documented value or a defined refusal.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R123 exports `CHISQ.TEST`, `F.TEST`, `Z.TEST`, `T.TEST` and `SKEW.P` with the '
        '_xlfn. prefix, the way Excel stores future-function names in OOXML, so formulas '
        'written by the engine round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r123_stat_test.py',
    'tools/gen_r123_stat_test_corpus.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R123StatTest.kt',
    'format/ooxml/src/test/resources/r123/stat_test_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r123_stat_test_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r123_stat_test.py',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R123 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R123: {stale}')

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
        raise SystemExit(f'R123 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r123 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
