#!/usr/bin/env python3
"""R119 census deepen: register the legacy statistical aliases and FORECAST and their oracle corpus.

R119 changes exactly two product files. `FormulaEval.kt` gains five alias dispatch branches that delegate to the
modern kernels they rename — `VAR` to `VAR.S`, `VARP` to `VAR.P`, `STDEVP` to `STDEV.P`, `MODE` to `MODE.SNGL`,
`COVARIANCE` to `COVARIANCE.P` — and one new helper, `evalForecast`, for `FORECAST`/`FORECAST.LINEAR` (the
least-squares prediction `intercept + slope * x`). `SpillSheetEngine.kt` gains `FORECAST.LINEAR` in its OOXML
export prefix list, because Excel stores that name with the `_xlfn.` prefix; the legacy names are stored bare.
It changes no build file, no Android source and no existing function: the percentile, `MODE.SNGL`, `STDEV`/`VAR`
and `A`-family kernels are pinned unchanged by rule R119-7, because they are the kernels this round extends
rather than touches.

The new evidence is a corpus whose expected column comes from the documented definitions (Microsoft's function
reference for each name) and is independently recomputed in the controls through CPython's `statistics` module
(`variance`, `pvariance`, `pstdev`, `linear_regression`), a Kotlin JUnit test in the module's own `src/test`
that the existing `:format:ooxml:test` CI step executes, and the gate and controls registered here.

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
        'R119 exposes the legacy statistical aliases and FORECAST: VAR/VARP/STDEVP/MODE/COVARIANCE delegate to the '
        'modern kernels they rename (VAR.S/VAR.P/STDEV.P/MODE.SNGL/COVARIANCE.P), and FORECAST/FORECAST.LINEAR '
        'evaluate the least-squares prediction intercept + slope * x in a new helper. Before this, a sheet could '
        'evaluate VAR.S but not VAR, and SLOPE/INTERCEPT but not FORECAST. The evidence is a 212-row corpus whose '
        'expected column is the documented definitions, independently recomputed through the statistics module, '
        'executed by :format:ooxml:test in CI.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R119 exports FORECAST.LINEAR with the _xlfn. prefix, because Excel stores that name that way in OOXML; '
        'the legacy FORECAST/VAR/VARP/STDEVP/MODE/COVARIANCE names are stored bare and are not prefixed.',
    ),
}
CHECKS = [
    'tools/check_r119_legacy_stats.py',
    'tools/test_r119_legacy_stats.py',
    'tools/gen_r119_legacy_stats_corpus.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/LegacyStatsFunctionTest.kt',
    'format/ooxml/src/test/resources/r119/legacy_stats_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r119_legacy_stats_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r119_legacy_stats.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r119_legacy_stats',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R119 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R119: {stale}')

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
        raise SystemExit(f'R119 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r119 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
