#!/usr/bin/env python3
"""R120 census deepen: register the distribution functions and inverses and their oracle corpus.

R120 changes exactly two product files. `FormulaEval.kt` gains dispatch branches for `T.DIST`,
`T.DIST.2T`, `T.DIST.RT`, `F.DIST`, `T.INV`, `T.INV.2T`, `CHISQ.INV`, `CHISQ.INV.RT`, `F.INV`,
`F.INV.RT`, `BETA.INV`, `GAMMA.INV` and `GAMMALN.PRECISE`; `T.DIST` is a small wrapper around the
`TDIST.RT`/`TDIST.2T` kernels and `GAMMALN.PRECISE` is the Lanczos log-gamma kernel, while the eight
inverses are quantiles found by bisection on the regularized incomplete-beta and incomplete-gamma CDFs
(the same kernels `TDIST.2T`, `F.DIST.RT`, `BETA.DIST`, `CHISQ.DIST`, `GAMMA.DIST` already use — this
round extends them, it does not rewrite them). `SpillSheetEngine.kt` gains all thirteen names in its
OOXML export prefix list, because Excel stores future-function names with the `_xlfn.` prefix. It
changes no build file, no Android source and no existing function.

The new evidence is a 612-row corpus whose expected column comes from the documented definitions
(Microsoft's function reference for each name) and is independently recomputed in the controls through
numerical integration of the probability density functions (Simpson's rule) — a different algorithm from
the generator's continued fractions — executed by `:format:ooxml:test` in CI via a Kotlin JUnit test in
the module's own `src/test`, and bounded by the gate and controls registered here.

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
        'R120 exposes the distribution names the engine was missing: `T.DIST`, `T.DIST.2T`, `T.DIST.RT` '
        'and `F.DIST` delegate to the Student and Fisher CDF kernels, `GAMMALN.PRECISE` is the Lanczos '
        'log-gamma kernel, and `T.INV`, `T.INV.2T`, `CHISQ.INV`, `CHISQ.INV.RT`, `F.INV`, `F.INV.RT`, '
        '`BETA.INV` and `GAMMA.INV` are quantiles found by bisection on the regularized incomplete-beta '
        'and incomplete-gamma CDFs. Before this, a sheet could evaluate none of those thirteen names '
        '(every one returned #NAME?); now each evaluates to the documented value or a defined refusal.',
    ),
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt': (
        ':format:ooxml',
        'R120 exports the thirteen new distribution names with the _xlfn. prefix, the way Excel stores '
        'future-function names in OOXML, so formulas written by the engine round-trip through OOXML.',
    ),
}
CHECKS = [
    'tools/check_r120_dist_inverse.py',
    'tools/test_r120_dist_inverse.py',
    'tools/gen_r120_dist_inverse_corpus.py',
    'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R120DistInverseTest.kt',
    'format/ooxml/src/test/resources/r120/dist_inverse_corpus.tsv',
]
COMMANDS = {
    ':format:ooxml': [
        'PYTHONPATH=tools python3 tools/gen_r120_dist_inverse_corpus.py',
        'PYTHONPATH=tools python3 tools/check_r120_dist_inverse.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r120_dist_inverse',
        ':format:ooxml:test',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(TOUCHED) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R120 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R120: {stale}')

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
        raise SystemExit(f'R120 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(TOUCHED) + CHECKS:
        print(f'r120 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
