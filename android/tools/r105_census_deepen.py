#!/usr/bin/env python3
"""R105 census deepen: re-pin the files this round changed and record the new checks, refusing anything else.

The census in `docs/module-testability.json` binds every listed production and check file to a SHA-256. A round that
edits one of those files therefore cannot be honest about "the reviewed evidence" without re-pinning it — and a
script that re-pins whatever it finds would launder an unreviewed edit. This one names the files R105 is allowed to
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
        'R105 adds from:to ranges and the seven aggregates a conditional format actually uses (SUM, COUNT, COUNTA, '
        'AVERAGE, MIN, MAX, MEDIAN) to the R104 formula grammar, with Excel\'s range-vs-scalar rules, error '
        'propagation from a range cell, a 100000-cell read bound, normalization of a reversed range, and the R103 '
        'shift rule applied to both endpoints, refused with a reason when either leaves the grid.',
    ),
}
# Files R105 edited but did not own: two R104 examples were re-pointed at the same properties, because `SUM` and
# ranges became real and the old examples named them as unknown/out-of-grammar. Re-pinned, with the reason, so the
# census keeps binding exactly what was reviewed.
REPIN = {
    'testing/src/r104/kotlin/dev/fusion/testing/R104Checks.kt':
        'R105 re-pointed two of its examples at the same properties: the unknown-function case now names FOO instead '
        'of SUM, and the range case expects the range refusal text instead of a colon error.',
    'tools/check_r104_grammar.py':
        'R105 taught the R104 lane that a colon starts a range, so it refuses a range as a value the way the product '
        'does; its other 99 cases are unchanged.',
    'tools/test_r104_grammar.py':
        'R105 updated the two controls that pinned the old SUM/colon examples.',
    'testing/src/r103/kotlin/dev/fusion/testing/R103Checks.kt':
        'R105 re-pointed one entry of its out-of-grammar list from SUM(...) to FOO(...), because SUM became real; '
        'the assertion (an unreadable formula is undecided) and all 113 counts are unchanged.',
}

CHECKS = [
    'testing/src/r105/kotlin/dev/fusion/testing/R105Checks.kt',
    'tools/check_r105_ranges.py',
    'tools/run_r105_checks.py',
    'tools/test_r105_ranges.py',
]
COMMANDS = {
    ':format:ooxml': [
        'python3 tools/run_r105_checks.py',
        'PYTHONPATH=tools python3 -m unittest tools.test_r105_ranges',
        ':testing:r105RangesContracts',
    ],
}


def sha(rel: str) -> str:
    return hashlib.sha256((ROOT / rel).read_bytes()).hexdigest()


def main() -> int:
    for path in CHECKS:
        assert (ROOT / path).is_file(), f'missing R105 file: {path}'
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
        raise SystemExit(f'refusing to re-pin files outside R105: {stale}')

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
        print(f'r105 re-pinned {path} for {owner}')
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/module_testability.py'), '--check'], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
