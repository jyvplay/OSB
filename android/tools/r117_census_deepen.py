#!/usr/bin/env python3
"""R117 census deepen: register the percentile variants and the `A` aggregate family and their oracle corpus.

R117 changes exactly two product files. `FormulaEval.kt` gains the `*.INC`/`*.EXC` percentile variants
(`PERCENTILE.INC`, `PERCENTILE.EXC`, `QUARTILE.INC`, `QUARTILE.EXC`) on the existing percentile kernel and one new
helper, `evalAggregateA`, for the `A`-suffixed family (`AVERAGEA`, `MAXA`, `MINA`, `VARA`, `VARPA`, `STDEVA`,
`STDEVPA`). `SpillSheetEngine.kt` gains the four dotted names in its OOXML export prefix list, because Excel stores
them with the `_xlfn.` prefix. It changes no build file, no Android source and no existing function: the legacy
inclusive percentile kernel, `MODE.SNGL` and the `STDEV`/`VAR` kernels are pinned unchanged by rule R117-7, because
they are the kernels this round extends rather than touches.

The new evidence is a corpus whose expected column comes from the documented definitions (ECMA-376 18.17.7 for the
percentile rank, Microsoft's function reference for the `A` family) and is independently recomputed in the controls
through CPython's `statistics.quantiles` and a separate transcription of the counting rules, a Kotlin JUnit test in
the module's own `src/test` that the existing `:format:ooxml:test` CI step executes, and the gate and controls
registered here.

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
        'R117 exposes the percentile variants and the A-suffixed aggregate family: PERCENTILE.INC/PERCENTILE.EXC/'
        'QUARTILE.INC/QUARTILE.EXC on the existing percentile kernel (the exclusive rank pos = k*(n+1)-1 refuses '
        'ranks outside the data with #NUM!), and AVERAGEA/MAXA/MINA/VARA/VARPA/STDEVA/STDEVPA in a new helper that '
        'counts text as 0 and logicals as 1/0, where the non-A aggregates ignore them. Before this, a sheet could '
        'evaluate PERCENTILE and not PERCENTILE.EXC, and AVERAGE but not AVERAGEA. The evidence is a 776-row corpus '
        'whose expected column is the documented definitions, independently recomputed through statistics.quantiles, '
        'executed by :format:ooxml:test in CI.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R117 exports PERCENTILE.INC/PERCENTILE.EXC/QUARTILE.INC/QUARTILE.EXC with the _xlfn. prefix, because Excel '
        'stores those four names that way in OOXML; the legacy PERCENTILE/QUARTILE and the A-family names are stored '
        'bare and are not prefixed.',
    ),
}
CHECKS = [
    'tools/check_r117_percentile.py',
    'tools/test_r117_percentile.py',
    'tools/gen_r117_percentile_corpus.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/PercentileFunctionTest.kt',
    'format/ooxml/src/test/resources/r117/percentile_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r117_percentile_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r117_percentile.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r117_percentile',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R117 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R117: {stale}')

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
        raise SystemExit(f'R117 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r117 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
