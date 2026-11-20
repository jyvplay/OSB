#!/usr/bin/env python3
"""R115 office-case inventory: decide locally what the managed-device job decides only after a boot.

`tools/r64_office_case_gate.py` requires exactly 37 named cases from a real emulator run, and the workflow selects
them with an instrumentation `class=` argument that mixes whole classes with one `Class#method` filter. Two things can
go wrong that nobody sees until an 18-minute device run:

* a case is renamed or deleted in Kotlin, so the runner produces 36 cases and the gate fails on arrival;
* the workflow's selection drifts from the expected list, so extra cases run (a silently larger suite) or the one
  filtered method is no longer the expected one.

This gate reproduces that decision statically, from the same sources, and adds one inventory rule the device job has
no way to check: two captures that write the same file name would overwrite each other's evidence.

  R115-1 expected present   every expected case exists as an `@Test fun` in the class it names.
  R115-2 unique methods     an office test class declares no duplicate method name.
  R115-3 selection equals   the cases the workflow selects are exactly the expected cases — no additions, no losses.
  R115-4 classes resolve    every instrumented class, with or without a `#method` filter, has a source file.
  R115-5 capture unique     no two dialog captures write the same file name.

Comments and string literals are stripped before annotations are read, so a commented-out `@Test` is not a case.
"""
from __future__ import annotations

import argparse
import importlib
import re
import sys
from pathlib import Path

from kotlin_code_only import code_only, strip_comments

DEFAULT_ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = '.github/workflows/android-r54-office.yml'
TEST_DIR = 'app/src/androidTest/java/dev/fusion/shell'
GATE_MODULE = 'r64_office_case_gate'

RULES = {
    'R115-1': 'every expected Office case exists as an @Test fun in the class it names',
    'R115-2': 'an Office test class declares no duplicate method name',
    'R115-3': 'the cases the workflow selects are exactly the expected cases',
    'R115-4': 'every instrumented class has a source file',
    'R115-5': 'no two dialog captures write the same file name',
}

CASE = re.compile(r'@Test(?:\([^)]*\))?\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(')
SELECTION = re.compile(r'class=([^\s]+)')


def expected_cases() -> frozenset[str]:
    """The list the device gate enforces, imported rather than copied, so the two cannot drift apart."""
    return frozenset(importlib.import_module(GATE_MODULE).EXPECTED)


def workflow_code(text: str) -> str:
    """The workflow without its remarks: a commented-out `class=` must not select anything."""
    kept: list[str] = []
    for line in text.splitlines():
        if line.lstrip().startswith('#'):
            kept.append('')
        else:
            kept.append(line.split(' #', 1)[0])
    return '\n'.join(kept)


def selections(root: Path) -> list[tuple[str, str | None]]:
    """`(class, method-or-None)` for every entry of the workflow's instrumentation `class=` argument."""
    # `--root` is the Android module during normal use, so the workflow is one level up; a synthetic root may hold
    # its own copy, which is preferred so controls can mutate the selection without touching this repository.
    workflow = root / WORKFLOW
    if not workflow.is_file():
        workflow = root.parent / WORKFLOW
    if not workflow.is_file():
        raise SystemExit(f'R115-0: missing {WORKFLOW}')
    found: list[tuple[str, str | None]] = []
    for argument in SELECTION.findall(workflow_code(workflow.read_text(encoding='utf-8'))):
        for entry in argument.split(','):
            entry = entry.strip().strip('"\'')
            if not entry:
                continue
            name, _, method = entry.partition('#')
            found.append((name, method or None))
    return found


def declared(root: Path, name: str) -> list[str] | None:
    path = root / TEST_DIR / f'{name.split(".")[-1]}.kt'
    if not path.is_file():
        return None
    return CASE.findall(code_only(path.read_text(encoding='utf-8')))


def arguments(text: str, open_index: int) -> list[str]:
    """Top-level arguments of the call whose `(` is at `open_index`, read from a literal-aware view of `text`."""
    found: list[str] = []
    depth, begin = 0, open_index + 1
    for index in range(open_index, len(text)):
        character = text[index]
        if character in '([{':
            depth += 1
        elif character in ')]}':
            depth -= 1
            if not depth:
                found.append(text[begin:index])
                return found
        elif character == ',' and depth == 1:
            found.append(text[begin:index])
            begin = index + 1
    return found or [text[open_index + 1:]]


def capture_name(call: list[str]) -> str:
    """The name a capture would write: `name = ...` if passed by name, else the third positional argument."""
    chosen = next((item for item in call if item.strip().startswith('name')), None)
    if chosen is None:
        chosen = call[2] if len(call) > 2 else (call[0] if call else '')
    literal = re.search(r'"((?:[^"\\]|\\.)*)"', chosen)
    return literal.group(1) if literal else ' '.join(chosen.split())


def captures(root: Path) -> list[str]:
    """The file name every `captureDialogDisplay(...)` call site would write."""
    names: list[str] = []
    for path in sorted((root / TEST_DIR).glob('*.kt')):
        if path.name == 'NativeVisualEvidence.kt':
            continue
        raw = path.read_text(encoding='utf-8')
        # Two views of the same bytes, comments blanked in both and literals kept only in `readable`, so a
        # parenthesis inside a string cannot unbalance the argument scan and a commented-out call claims no name.
        readable = strip_comments(raw)
        for match in re.finditer(r'captureDialogDisplay\s*\(', readable):
            names.append(capture_name(arguments(readable, readable.index('(', match.start()))))
    return names


def violations(root: Path = DEFAULT_ROOT, expected: frozenset[str] | None = None) -> tuple[list[str], dict]:
    wanted = expected_cases() if expected is None else frozenset(expected)
    found: list[str] = []
    chosen = selections(root)
    selected: set[str] = set()
    for name, method in chosen:
        methods = declared(root, name)
        if methods is None:
            found.append(f'R115-4: instrumented class {name} has no source file under {TEST_DIR}')
            continue
        if len(set(methods)) != len(methods):
            duplicates = sorted({m for m in methods if methods.count(m) > 1})
            found.append(f'R115-2: {name} declares {", ".join(duplicates)} more than once')
        selected |= {f'{name}.{m}' for m in ([method] if method else methods)}
    missing = sorted(wanted - selected)
    for case in missing:
        found.append(f'R115-1: expected case {case} is not declared as an @Test fun')
    for case in sorted(selected - wanted):
        found.append(f'R115-3: the workflow also selects {case}, which is not an expected case')
    names = captures(root)
    repeated = sorted({name for name in names if names.count(name) > 1})
    for name in repeated:
        found.append(f'R115-5: capture name {name} is used more than once and would overwrite evidence')
    summary = {'expected': len(wanted), 'selected': len(selected), 'classes': len({n for n, _ in chosen}),
               'captures': len(names)}
    return found, summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=DEFAULT_ROOT)
    parser.add_argument('--list', action='store_true')
    args = parser.parse_args()
    if args.list:
        for rule, text in RULES.items():
            print(f'{rule}\t{text}')
        return 0
    found, summary = violations(args.root)
    if found:
        for item in found:
            print(item)
        return 1
    print(f"R115_OFFICE_CASES_OK expected={summary['expected']} selected={summary['selected']} "
          f"classes={summary['classes']} captures={summary['captures']}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
