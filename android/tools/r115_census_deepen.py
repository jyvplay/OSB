#!/usr/bin/env python3
"""R115 census deepen: register the office-case inventory and the shared comment scanner.

R115 changes no product source, no build file and no Kotlin test. It adds

* `tools/check_r115_office_cases.py` — the local mirror of the managed-device job's "exactly 37 named cases" gate,
  plus a capture-name uniqueness rule the device job cannot check; and
* `tools/kotlin_code_only.py` — one correct scanner shared by the R112, R113 and R115 gates, replacing three copies
  that stripped comments *before* strings and therefore let a quoted `"/*"` delete the code after it.

Both gates that consume the scanner changed with it, so their pinned hashes are re-pinned here too. As with every
previous deepen script, any pinned file outside this list that has drifted aborts the run.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TOUCHED: dict[str, tuple[str, str]] = {}
REPIN: dict[str, str] = {
    'tools/check_r112_visual_contracts.py': (
        'R115 replaced its private comment stripper with tools/kotlin_code_only.py, whose single-pass scanner keeps a '
        'quoted "/*" or "//" from deleting the code that follows it.'
    ),
    'tools/check_r113_include_closure.py': (
        'R115 replaced its private comment stripper with tools/kotlin_code_only.py for the same reason: the '
        'strip-comments-then-strings order could delete an entire class body and hide a real closure gap.'
    ),
}
CHECKS = [
    'tools/check_r115_office_cases.py',
    'tools/test_r115_office_cases.py',
    'tools/kotlin_code_only.py',
]
COMMANDS = {
    ':app': [
        'PYTHONPATH=tools python3 tools/check_r115_office_cases.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r115_office_cases',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(REPIN) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R115 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R115: {stale}')

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

    missing = sorted(path for path in list(REPIN) + CHECKS
                     if not any(path in row['sha256'] for row in data['rows']))
    if missing:
        raise SystemExit(f'R115 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in list(REPIN) + CHECKS:
        print(f'r115 registered {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
