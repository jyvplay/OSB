#!/usr/bin/env python3
"""R113 census deepen: register the include-closure gate on the two modules it protects.

R113 adds no product source and re-pins no changed file: the round is a gate over `testing/build.gradle.kts`, the
44 probe source sets that filter their file list with `kotlin.include(...)`, and the module those probes compile
(`:format:ooxml`). The census therefore only gains two checks and their commands, on `:testing` (which owns the
source-set declarations) and `:format:ooxml` (whose files the closure keeps compilable inside the probes).

Like the earlier deepen scripts it refuses to run if any *other* pinned file drifted, so it can only record what this
round actually touched.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TOUCHED: dict[str, tuple[str, str]] = {}
REPIN: dict[str, str] = {}
CHECKS = [
    'tools/check_r113_include_closure.py',
    'tools/test_r113_include_closure.py',
]
COMMANDS = {
    ':testing': [
        'python3 tools/check_r113_include_closure.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r113_include_closure',
    ],
    ':format:ooxml': [
        'python3 tools/check_r113_include_closure.py',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R113 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R113: {stale}')

    touched = 0
    for row in data['rows']:
        module = row['module']
        if module not in COMMANDS:
            continue
        for check in CHECKS:
            if module == ':testing' or check == 'tools/check_r113_include_closure.py':
                if check not in row['checks']:
                    row['checks'].append(check)
                row['sha256'][check] = sha(check)
        for command in COMMANDS[module]:
            if command not in row['command']:
                row['command'] = row['command'].rstrip() + '; ' + command
        touched += 1
    if touched != len(COMMANDS):
        raise SystemExit(f'expected rows {sorted(COMMANDS)}, found {touched}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in CHECKS:
        print(f'r113 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
