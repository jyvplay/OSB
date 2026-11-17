#!/usr/bin/env python3
"""R104 census deepen: re-pin the files this round changed and record the new checks, refusing anything else.

The census in `docs/module-testability.json` binds every listed production and check file to a SHA-256. A round that
edits one of those files therefore cannot be honest about "the reviewed evidence" without re-pinning it — and a
script that re-pins whatever it finds would launder an unreviewed edit. This one names the files R104 is allowed to
touch, refuses to run if any other pinned file has changed, appends the round's checks and commands to the rows they
belong to, and then re-renders `docs/MODULE-TESTABILITY.md` through `tools/module_testability.py`.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# The one production file this round edited, plus the files it added and wants recorded as checks.
TOUCHED = {
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/XlsxConditionalFormatVerdict.kt': (
        ':format:ooxml',
        'R104 replaces the two-regex expression reader with a bounded tokenizer, recursive-descent parser and '
        'evaluator over sixteen functions (AND/OR/NOT/ISBLANK/ISNUMBER/ISTEXT/LEN/TRIM/UPPER/LOWER/LEFT/RIGHT/'
        'ABS/ROUND/MOD/ROW), refuses formulas outside that grammar with a reason, and keeps the R103 relative-'
        'reference rule inside every function argument.',
    ),
}
CHECKS = [
    'testing/src/r104/kotlin/dev/fusion/testing/R104Checks.kt',
    'tools/check_r104_grammar.py',
    'tools/check_r104_studio.py',
    'tools/run_r104_checks.py',
    'tools/test_r104_grammar.py',
    'tools/test_r104_studio_gate.py',
]
COMMANDS = {
    ':format:ooxml': [
        'python3 tools/run_r104_checks.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r104_grammar',
        ':testing:r104GrammarContracts',
    ],
    ':app': [
        'python3 tools/check_r104_studio.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r104_studio_gate',
    ],
}
STUDIO_NOTE = (
    'R104 adds a source-only Android Studio Rabbit 1 readiness gate: AGP inside the IDE\'s 7.1-9.4 range, wrapper '
    'Gradle at or above the plugin minimum, every Android module on JDK 17 with a namespace and a compileSdk no '
    'newer than AGP 9.4 accepts, AndroidX on, the AGP 9 Kotlin bridge declared, an exported launcher, and the two '
    'documented flavors. It is configuration evidence only: no Android SDK, Gradle distribution or device exists '
    'in this sandbox, so the first real build is on a laptop.'
)


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R104 file: {path}'
    for path in TOUCHED:
        assert (ROOT / path).is_file(), f'missing touched file: {path}'

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
        raise SystemExit(f'refusing to re-pin files outside R104: {stale}')

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
        if module == ':app':
            if STUDIO_NOTE not in row['behavior']:
                row['behavior'] = row['behavior'].rstrip() + ' ' + STUDIO_NOTE
        if module not in COMMANDS:
            continue
        for check in CHECKS:
            if module == ':format:ooxml' or check in (':app', 'tools/check_r104_studio.py', 'tools/test_r104_studio_gate.py'):
                if check not in row['checks']:
                    row['checks'].append(check)
                row['sha256'][check] = sha(check)
        for command in COMMANDS[module]:
            if command not in row['command']:
                row['command'] = row['command'].rstrip() + '; ' + command

    census.write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for path, (owner, _) in TOUCHED.items():
        print(f'r104 re-pinned {path} for {owner}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
