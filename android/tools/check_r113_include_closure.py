#!/usr/bin/env python3
"""R113 include-closure gate: an include(...)-filtered Gradle source set must see every sibling it references.

`android/testing/build.gradle.kts` assembles 102 probe source sets. Forty-four of them filter their file list with
`kotlin.include(...)`, so Gradle compiles a *subset* of the directories they point at. A subset compiles as one
module: a reference from an included file to a top-level declaration in an excluded file of the same package is an
unresolved symbol, and nothing in this sandbox can see it without a Kotlin compiler.

R112 hit exactly that: `:testing:compileR47Kotlin` failed in CI on nine `Xlsx*` symbols because the r47 allowlist
named only `XlsxStyledBook.kt`. The 18-minute CI round trip found it; this gate finds it in about a second, on every
include-filtered set at once, and says which file and which omission caused it.

Four contracts:

  R113-1 closure        an included file may not reference a same-package top-level declaration that only an
                        excluded file provides.
  R113-2 live pattern   every include pattern matches at least one file; a pattern that matches nothing is drift.
  R113-3 unique pattern no include pattern is listed twice (Gradle accepts it, a reader cannot tell which is real).
  R113-4 resolvable set a set with an include filter has at least one source directory on disk, so it cannot
                        silently compile nothing.

Sets whose srcDir is a `"../$it/..."` template are reported as not modelled rather than guessed at.
Comments and string literals are stripped before references are read: a KDoc mention is not a dependency.
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import re
import sys
from pathlib import Path

DEFAULT_ROOT = Path(__file__).resolve().parents[1]
BUILD = 'testing/build.gradle.kts'

RULES = {
    'R113-1': 'an included file references only declarations the same compilation provides',
    'R113-2': 'every include pattern matches at least one file',
    'R113-3': 'no include pattern is listed twice',
    'R113-4': 'an include-filtered set has a source directory that exists',
}

# Type-level and property declarations only. An extension (`fun Map<..>.str()`, `val Map<..>.x`) declares a receiver,
# not a name, and the same text pattern cannot tell the two apart safely; functions are therefore not modelled and the
# gate says so. Types and properties are the class that broke r47 (`object XlsxValidation`).
DECLARATION = re.compile(r'^(?P<indent>[ \t]*)(?:@\w+\s+)*(?:public |internal |private |protected )*'
                         r'(?:data |sealed |enum |annotation |abstract |open |value |inline |const )*'
                         r'(?:object|class|interface|typealias|val|var)\s+(?P<name>[A-Z][A-Za-z0-9_]*)', re.M)
PACKAGE = re.compile(r'^package\s+([\w.]+)', re.M)
IDENTIFIER = re.compile(r'\b[A-Z][A-Za-z0-9_]*\b')


def code_only(text: str) -> str:
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    text = re.sub(r'//[^\n]*', ' ', text)
    return re.sub(r'"(?:\\.|[^"\\])*"', ' "" ', text)


def source_sets(text: str) -> dict[str, dict]:
    """`{name: {directories, patterns, template}}` for every `kotlin.sourceSets.named("<name>") { ... }` block."""
    sets: dict[str, dict] = {}
    for match in re.finditer(r'kotlin\.sourceSets\.named\("([^"]+)"\)\s*\{', text):
        name, index, depth = match.group(1), match.end(), 1
        while index < len(text) and depth:
            depth += 1 if text[index] == '{' else -1 if text[index] == '}' else 0
            index += 1
        body = text[match.end():index - 1]
        directories = re.findall(r'kotlin\.srcDir\("([^"]+)"\)', body)
        patterns: list[str] = []
        for start in [i for i in range(len(body)) if body.startswith('kotlin.include(', i)]:
            cursor, brace = body.find('(', start) + 1, 1
            while cursor < len(body) and brace:
                brace += 1 if body[cursor] == '(' else -1 if body[cursor] == ')' else 0
                cursor += 1
            patterns.extend(re.findall(r'"([^"]+)"', body[body.find('(', start):cursor]))
        sets[name] = {'directories': directories, 'patterns': patterns,
                      'template': any('$it' in directory for directory in directories)}
    return sets


def declarations(code: str) -> tuple[set[str], set[str]]:
    """`(top-level names, every name declared in this file)`.

    A nested or private declaration in an included file shadows a same-package declaration of the same name, so a
    reference is only suspicious when the file itself does not declare it.
    """
    top, local = set(), set()
    for match in DECLARATION.finditer(code):
        name, indent = match.group('name'), match.group('indent')
        tail = code[match.end():match.end() + 4].lstrip()
        if tail[:1] in {'.', '<'}:
            continue  # extension receiver, not a declaration of `name`
        local.add(name)
        if not indent:
            top.add(name)
    return top, local


class Sources:
    """Reads each file once: package, declarations, referenced capitalised identifiers."""

    def __init__(self, root: Path):
        self.root = root
        self._cache: dict[str, dict] = {}

    def read(self, path: Path) -> dict:
        key = str(path)
        if key not in self._cache:
            code = code_only(path.read_text(encoding='utf-8', errors='replace'))
            package = PACKAGE.search(code)
            top, local = declarations(code)
            self._cache[key] = {
                'package': package.group(1) if package else '',
                'declares': top,
                'local': local,
                'uses': set(IDENTIFIER.findall(code)),
            }
        return self._cache[key]


def matches(relative: str, pattern: str) -> bool:
    return fnmatch.fnmatch(relative, pattern) or fnmatch.fnmatch(Path(relative).name, pattern.lstrip('*/'))


def files_for(root: Path, directories: list[str]) -> list[tuple[Path, str]]:
    """`srcDir` in `testing/build.gradle.kts` is relative to the `testing` module, not to the repository root."""
    found: list[tuple[Path, str]] = []
    for directory in directories:
        base = root / 'testing' / directory
        if not base.is_dir():
            continue
        for path in sorted(base.rglob('*.kt')):
            found.append((path, str(path.relative_to(base))))
    return found


def violations(root: Path = DEFAULT_ROOT) -> tuple[list[str], dict]:
    build = root / BUILD
    if not build.is_file():
        raise SystemExit(f'R113-0: missing {BUILD}')
    sets = source_sets(build.read_text(encoding='utf-8'))
    sources = Sources(root)
    found: list[str] = []
    modelled = 0
    for name in sorted(sets):
        info = sets[name]
        if not info['patterns']:
            continue
        if info['template']:
            continue  # reported as not modelled below
        candidates = [(path, relative) for path, relative in files_for(root, info['directories'])
                      if not any(part in {'build', '.gradle'} for part in path.parts)]
        included = [(path, relative) for path, relative in candidates
                    if any(matches(relative, pattern) for pattern in info['patterns'])]
        excluded = [(path, relative) for path, relative in candidates
                    if not any(matches(relative, pattern) for pattern in info['patterns'])]
        modelled += 1
        if not any((root / 'testing' / directory).is_dir() for directory in info['directories']):
            found.append(f'R113-4: {name} has an include filter but no source directory exists')
        for pattern in info['patterns']:
            if not any(matches(relative, pattern) for _, relative in candidates):
                found.append(f'R113-2: {name} include pattern {pattern} matches no file')
        seen: set[str] = set()
        for pattern in info['patterns']:
            if pattern in seen:
                found.append(f'R113-3: {name} lists include pattern {pattern} twice')
            seen.add(pattern)
        provided: dict[tuple[str, str], str] = {}
        for path, _ in excluded:
            data = sources.read(path)
            for declaration in data['declares']:
                provided.setdefault((data['package'], declaration), path.name)
        for path, _ in included:
            data = sources.read(path)
            for used in data['uses'] - data['local']:
                owner = provided.get((data['package'], used))
                if owner:
                    found.append(f'R113-1: {name} compiles {path.name} which uses {used} from {owner}, '
                                 f'omitted by its include filter')
    summary = {'sets': len(sets), 'with_include': sum(1 for s in sets.values() if s['patterns']),
               'modelled': modelled, 'templates': sum(1 for s in sets.values() if s['patterns'] and s['template'])}
    return found, summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=DEFAULT_ROOT)
    parser.add_argument('--list', action='store_true')
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    if args.list:
        for rule, text in RULES.items():
            print(f'{rule}\t{text}')
        return 0
    found, summary = violations(args.root)
    if args.json:
        print(json.dumps({'summary': summary, 'violations': found}, sort_keys=True))
    if found:
        for item in found:
            print(item)
        if not args.json:
            print(json.dumps(summary, sort_keys=True))
        return 1
    if not args.json:
        print(f"R113_INCLUDE_CLOSURE_OK sets={summary['sets']} with_include={summary['with_include']} "
              f"modelled={summary['modelled']} unmodelled_templates={summary['templates']}")
    return 0


if __name__ == '__main__':
    sys.exit(main())
