#!/usr/bin/env python3
"""R107 census deepen: re-pin the files this round changed and record the new checks, refusing anything else.

The census in `docs/module-testability.json` binds every listed production and check file to a SHA-256. A round that
edits one of those files therefore cannot be honest about "the reviewed evidence" without re-pinning it — and a
script that re-pins whatever it finds would launder an unreviewed edit. This one names the files R107 is allowed to
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
        'R107 adds the plural criteria functions COUNTIFS, SUMIFS and AVERAGEIFS to the R106 criteria grammar: one or '
        'more range/criterion pairs of which every pair must hold at the same position, the accumulated range first '
        'for the last two, pairs aligned by their own row-major positions (a range\'s position is free, its width and '
        'height are not), an unpaired argument refused by parity, every pair read at every position so an error '
        'under a criterion cannot hide behind an earlier failure, an error in the accumulated range refused only '
        'where it is read, numbers only accumulated, #DIV/0! for AVERAGEIFS with no matching number, and the '
        'grammar\'s existing MAX_CALL_ARGS of 8 bounding the forms at four pairs (COUNTIFS) and three '
        '(SUMIFS, AVERAGEIFS).',
    ),
}
# Files R107 edited but did not own. The R106 lane needed no change: its grammar is a prefix of this one and its
# 64 cases re-derive unchanged.
REPIN: dict[str, str] = {}

CHECKS = [
    'testing/src/r107/kotlin/dev/fusion/testing/R107Checks.kt',
    'tools/check_r107_plural_criteria.py',
    'tools/run_r107_checks.py',
    'tools/test_r107_plural_criteria.py',
]
COMMANDS = {
    ':format:ooxml': [
        'python3 tools/run_r107_checks.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r107_plural_criteria',
        ':testing:r107PluralCriteriaContracts',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R107 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R107: {stale}')

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
        print(f'r107 re-pinned {path} for {owner}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
