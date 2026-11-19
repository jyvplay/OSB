#!/usr/bin/env python3
"""R108 census deepen: re-pin the files this round changed and record the new checks, refusing anything else.

The census in `docs/module-testability.json` binds every listed production and check file to a SHA-256. A round that
edits one of those files therefore cannot be honest about "the reviewed evidence" without re-pinning it — and a
script that re-pins whatever it finds would launder an unreviewed edit. This one names the files R108 is allowed to
touch, refuses to run if any other pinned file has changed, appends the round's checks and commands to the row they
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
        "R108 adds the text functions MID, FIND, SEARCH, EXACT, SUBSTITUTE, REPT and VALUE to the grammar: one-based whole indices (#VALUE! for anything else), MID answering empty text past the end, FIND case-sensitive and wildcard-free while SEARCH folds case, reads */?/~ patterns and answers where a match starts, EXACT as the only case-sensitive equality, SUBSTITUTE replacing every occurrence or one instance case-sensitively and leaving an empty old text or an instance past the end unchanged, REPT and SUBSTITUTE bounded by the 32767-character cell text instead of truncating, VALUE reading the reader's own number grammar, and a stated scan bound of 1,000,000 token-position steps for SEARCH rather than unbounded quadratic work.",
    ),
}
# Files the previous round edited but did not own. That lane needed no change: its grammar is a prefix
# of this one and its 60 cases re-derive unchanged, and `wildcard` keeps its semantics through the
# extracted token list and the matcher it now shares with `SEARCH`.
REPIN: dict[str, str] = {}

CHECKS = [
    'testing/src/r108/kotlin/dev/fusion/testing/R108Checks.kt',
    'tools/check_r108_text_functions.py',
    'tools/run_r108_checks.py',
    'tools/test_r108_text_functions.py',
]
COMMANDS = {
    ':format:ooxml': [
        'python3 tools/run_r108_checks.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r108_text_functions',
        ':testing:r108TextFunctionContracts',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R108 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R108: {stale}')

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
    for path, (owner, _) in TOUCHED.items():
        print(f'r108 re-pinned {path} for {owner}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
