#!/usr/bin/env python3
"""R112 census deepen: re-pin the three files R112 touched, add the round's gate, refuse anything else.

`docs/module-testability.json` binds every reviewed production and check file to a SHA-256. R112 edited three pinned
files — the two androidTest sources that judge dialog text fit and the editor dialog itself — so the census cannot
stay honest unless those hashes are re-pinned, and a script that re-pinned whatever it found would launder an
unreviewed edit. This one names the three files, refuses to run if any other pinned file has changed, appends the
round's checks and commands to the two modules that own them, and re-renders `docs/MODULE-TESTABILITY.md` through
`tools/module_testability.py`.

R112 changes no product behaviour: the editor edit is an inert `testTag` on an existing bounded column, and the
androidTest edit makes the shared text-fit judge reveal a scroll-clipped node before judging it.
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
    'app/src/androidTest/java/dev/fusion/shell/NativeVisualEvidence.kt': (
        'R112 made the shared text-fit judge reveal a node in its own scroll viewport before judging fit, so a scroll '
        'offset is no longer reported as a layout defect while a notice taller than its viewport still fails; the R54 '
        'dialog now also records the details viewport height.'
    ),
    'app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt': (
        'R112 states the honest dialog contract for the numbered opt-in: each notice must be fully showable inside the '
        'bounded details viewport and the pinned safety summary must not scroll, instead of the earlier claim that two '
        'notices 424 px apart are simultaneously fully visible in a viewport measured at roughly 386 px plus one row.'
    ),
    'feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt': (
        'R112 added an inert testTag to the bounded details column of the formatted-DOCX dialog so a device run can '
        'record the viewport height; no text, bound or behaviour changed.'
    ),
}

CHECKS = [
    'tools/check_r112_visual_contracts.py',
    'tools/test_r112_visual_contracts.py',
]
COMMANDS = {
    ':app': [
        'python3 tools/check_r112_visual_contracts.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r112_visual_contracts',
    ],
    ':feature:editor': [
        'python3 tools/check_r112_visual_contracts.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r112_visual_contracts',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R112 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R112: {stale}')

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
        print(f'r112 re-pinned {path}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
