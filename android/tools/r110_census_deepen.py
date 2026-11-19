#!/usr/bin/env python3
"""R110 census deepen: re-pin the probe this round repaired and record the new checks, refusing anything else.

The census in `docs/module-testability.json` binds every listed production and check file to a SHA-256. A round that
edits one of those files therefore cannot be honest about "the reviewed evidence" without re-pinning it — and a
script that re-pins whatever it finds would launder an unreviewed edit. This one names the file R110 is allowed to
touch, refuses to run if any other pinned file has changed, appends the round's checks and commands to the row they
belong to, and then re-renders `docs/MODULE-TESTABILITY.md` through `tools/module_testability.py`.

R110 changes no product file: the defect was in the R93 probe, and the repair is two local `val` bindings there.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# R110 changes no product source: the defect was in the R93 *probe*, so there is nothing to add to a `production`
# list and no behaviour claim to append to a module row.
TOUCHED: dict[str, tuple[str, str]] = {}
# The one file this round edited, which the census already pins as a check. It is re-pinned with the note below so
# the row says what changed and why, and `owned` lets the stale-file gate accept it.
REPIN: dict[str, str] = {
    'testing/src/r93/kotlin/dev/fusion/testing/R93Checks.kt': (
        'R110 repaired the R93 probe, which is pinned here as evidence: R93Checks.kt read `column.values` and `column.custom` inside their own null checks, which Kotlin accepts inside one compilation unit and refuses across two, so the Gradle source set (a separate module from `:format:ooxml`) failed to compile and no JVM test had run in CI. Both sites now bind the property to a local first; nothing about what the probe asserts changed (still 72 assertions, 259 verdicts, 4 hidden, 2 undecided), and tools/check_r110_probe_boundaries.py now compiles the 56 reproducible probe source sets the way Gradle does, which is the gate that would have seen it.'
    ),
}

CHECKS = [
    'tools/check_r110_probe_boundaries.py',
    'tools/run_r110_checks.py',
    'tools/test_r110_probe_boundaries.py',
    'tools/r110_mutants.py',
]
COMMANDS = {
    ':format:ooxml': [
        'python3 tools/run_r110_checks.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r110_probe_boundaries',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R110 file: {path}'
    for path in TOUCHED:
        assert (ROOT / path).is_file(), f'missing touched file: {path}'

    census = ROOT / 'docs/module-testability.json'
    data = json.loads(census.read_text(encoding='utf-8'))

    owned = set(TOUCHED) | set(CHECKS) | set(REPIN)
    stale = sorted(
        (row['module'], path)
        for row in data['rows']
        for path, pinned in row['sha256'].items()
        if path not in owned and sha(path) != pinned
    )
    if stale:
        raise SystemExit(f'refusing to re-pin files outside R110: {stale}')

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
        for path, note in REPIN.items():
            if path not in row['sha256']:
                continue
            row['sha256'][path] = sha(path)
            if note not in row['behavior']:
                row['behavior'] = row['behavior'].rstrip() + ' ' + note
        if module not in COMMANDS:
            continue
        for check in CHECKS:
            if check not in row['checks']:
                row['checks'].append(check)
            row['sha256'][check] = sha(check)
        for command in COMMANDS[module]:
            if command not in row['command']:
                row['command'] = row['command'].rstrip() + '; ' + command

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(REPIN) + list(CHECKS):
        print(f'r110 re-pinned {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
