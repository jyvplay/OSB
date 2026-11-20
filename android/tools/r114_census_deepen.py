#!/usr/bin/env python3
"""R114 census deepen: re-pin the two gates this round extended, and nothing else.

R114 changes no product source and no build file. It extends two existing static gates:

* `check_r113_include_closure.py` now expands `listOf(...).forEach { srcDir("../$it/…") }` templates to their real
  directories (all 43 include-filtered sets are modelled; the three previously unmodelled ones are r13, r14 and r15)
  and models capitalised top-level functions as provided names;
* `check_r112_visual_contracts.py` gains R112-8 (a capture call stays inside the runtime arity and duplicate limits)
  and R112-9 (a capture name matches the `NativeCaptureNames` allowlist), so two `require`s that would otherwise fail
  only after an emulator boot are decided locally.

Both gates are pinned in the census, so their new hashes must be recorded; the script refuses to run if any pinned
file outside this list has drifted, exactly like its predecessors.
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
    'tools/check_r113_include_closure.py': (
        'R114 expanded the `"../$it/src/main/kotlin"` loop templates to their concrete directories (r13, r14, r15 are '
        'now modelled instead of reported unmodelled: 43 of 43 include-filtered sets) and treats a capitalised '
        'top-level `fun` as a provided name, while still refusing to read an extension receiver as a declaration.'
    ),
    'tools/test_r113_include_closure.py': (
        'R114 added controls for template expansion, for an unexpandable template staying unmodelled rather than being '
        'accused of having no directory, and for a capitalised function being provided by an excluded file.'
    ),
    'tools/check_r112_visual_contracts.py': (
        'R114 added R112-8 and R112-9: a dialog capture call must stay inside the eight-tag and no-duplicate limits of '
        'the runtime `require`, and its name must match the NativeCaptureNames allowlist, because both currently fail '
        'only after an emulator boots.'
    ),
    'tools/test_r112_visual_contracts.py': (
        'R114 added the three controls that make R112-8 and R112-9 falsifiable: nine extra tags, a repeated tag, and '
        'a capture name outside the allowlist.'
    ),
}

CHECKS: list[str] = []
COMMANDS: dict[str, list[str]] = {}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in list(REPIN) + CHECKS:
        assert (ROOT / path).is_file(), f'missing R114 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R114: {stale}')

    repinned = 0
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
            repinned += 1
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

    if repinned != len(REPIN) * len([row for row in data['rows']
                                     if any(path in row['sha256'] for path in REPIN)]):
        pass  # the same file is pinned in several rows; the check below is the meaningful one
    missing = sorted(path for path in REPIN
                     if not any(path in row['sha256'] for row in data['rows']))
    if missing:
        raise SystemExit(f'R114 files not pinned anywhere: {missing}')

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path in REPIN:
        print(f'r114 re-pinned {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
