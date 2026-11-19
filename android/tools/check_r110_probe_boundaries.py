#!/usr/bin/env python3
"""R110 probe-boundary check: compile the probe source sets Gradle gives its own compilation unit.

The R93 probe sat in `main` for seventeen rounds with a defect only a compiler could see: it read `column.values`
inside `if (column.values != null)`, which Kotlin accepts inside one compilation unit and refuses across two —

    error: smart cast to 'Iterable<TypeVariable(T)>' is impossible, because 'values' is a public API property
    declared in different module.

`testing/build.gradle.kts` declares every probe source set with `:format:ooxml` as a dependency, so under
`:testing:assemble` the probe is a different module from the product, the smart cast is illegal, and the job that runs
it fails before a single JVM test executes. Every host runner in this repository compiles the product sources and the
probe **in one kotlinc invocation**, which is exactly the situation Kotlin allows — so seventeen green host rounds
could not observe the break. The compile unit was wrong, not the assertions.

This tool compiles the two units the way Gradle does:

1. build one jar from the product (`format/archive` + `format/ooxml`);
2. read `testing/build.gradle.kts` and select the source sets whose declaration this sandbox can reproduce exactly —
   a single `kotlin.srcDir("src/…/kotlin")`, no `include(...)` filter, no `$it` loop directory, no
   `extendsFrom(configurations.implementation)`, and `:format:ooxml` as the only dependency;
3. compile each selected set's sources against that jar, separately, with the pinned host kotlinc;
4. require zero `error:` diagnostics and report the cross-module smart-cast class by name.

Everything else is **excluded and said so**, with the reason (§`--list`): 46 of the 102 source sets are assembled by
Gradle out of several directories, `include` globs or extra modules, and compiling those here would be a *different*
compilation unit — a claim about this tool rather than about the build. This sandbox has no Android SDK and no Gradle
distribution, so that boundary is real and is not papered over.

The verdict is kotlinc's, not this file's. It is host-only: a successful compile is not Gradle execution, and says
nothing about rendering or about the behaviour of the code it compiles.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTING = ROOT / 'testing'
sys.path.insert(0, str(ROOT / 'tools'))

import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
BUILD_FILE = TESTING / 'build.gradle.kts'
PRODUCT_DIRS = ('format/archive/src/main/kotlin', 'format/ooxml/src/main/kotlin')
PRODUCT_DEPENDENCY = ':format:ooxml'

# `val r108Text by sourceSets.creating`
DECLARED = re.compile(r'val (\w+) by sourceSets\.creating')
# `kotlin.sourceSets.named("r108Text") { … }`, with the body captured up to the closing brace on its own line.
SOURCE_DIR = re.compile(r'kotlin\.sourceSets\.named\("(\w+)"\) \{(.*?)\n\}', re.S)
ANY_SRC_DIR = re.compile(r'kotlin\.srcDir\("([^"]+)"\)')
INCLUDE = re.compile(r'kotlin\.include\(')
# `add(r108Text.implementationConfigurationName, project(":format:ooxml"))`
DEPENDENCY = re.compile(r'add\((\w+)\.implementationConfigurationName,\s*project\("([^"]+)"\)\)')
EXTENDS = re.compile(r'configurations\[(\w+)\.implementationConfigurationName\]\.extendsFrom\(')
SMART_CAST = re.compile(
    r"smart cast to .* is impossible, because '(\w+)' is a public API property declared in different module"
)


class Fail(Exception):
    """The check could not run, or ran and disagreed."""


def require(condition, message: str) -> None:
    if not condition:
        raise Fail(message)


def parse_source_sets(build_file: Path = BUILD_FILE) -> dict[str, dict]:
    """Every `testing` source set with what its Gradle declaration actually says.

    Each entry carries the declared `srcDir`s, whether an `include(...)` filter is present, whether a directory is a
    `"../$it/…"` loop template, whether the set inherits the module's whole implementation configuration, and its
    project dependencies — because each of those decides whether this sandbox can reproduce the unit at all.
    """
    text = build_file.read_text(encoding='utf-8')
    order = DECLARED.findall(text)
    bodies = {name: body for name, body in SOURCE_DIR.findall(text)}
    dependencies: dict[str, list[str]] = {}
    for name, project in DEPENDENCY.findall(text):
        dependencies.setdefault(name, []).append(project)
    extends = set(EXTENDS.findall(text))
    sets = {}
    for name in order:
        body = bodies.get(name)
        require(body is not None, f'source set {name} has no kotlin.sourceSets.named(...) block')
        sets[name] = {
            'directories': ANY_SRC_DIR.findall(body),
            'include': bool(INCLUDE.search(body)),
            'loop': '$it' in body,
            'extends_from': name in extends,
            'dependencies': dependencies.get(name, []),
        }
    return sets


def exclusion(info: dict, root: Path = TESTING) -> str | None:
    """Why this source set cannot be compiled here, or None when its declaration is reproducible."""
    if info['extends_from']:
        return 'inherits the module implementation configuration'
    if info['loop']:
        return 'a srcDir is a "$it" loop template'
    if info['include']:
        return 'an include(...) filter picks the files'
    if len(info['directories']) != 1:
        return f'{len(info["directories"])} source directories'
    if info['dependencies'] != [PRODUCT_DEPENDENCY]:
        listed = ', '.join(info['dependencies']) or 'no project dependency'
        return f'depends on {listed}'
    directory = info['directories'][0]
    if not (root / directory).is_dir():
        return f'{directory} does not exist'
    return None


def selectable(sets: dict[str, dict], root: Path = TESTING) -> tuple[dict[str, str], dict[str, str]]:
    """`(selected name -> srcDir, excluded name -> reason)`, with `root` the folder `srcDir`s are relative to."""
    selected, excluded = {}, {}
    for name, info in sets.items():
        reason = exclusion(info, root)
        if reason is None:
            selected[name] = info['directories'][0]
        else:
            excluded[name] = reason
    return selected, excluded


def product_sources() -> list[str]:
    return sorted(str(p) for directory in PRODUCT_DIRS for p in (ROOT / directory).rglob('*.kt'))


def build_product_jar(kotlinc: str, jar: Path, java_home: str) -> int:
    jar.parent.mkdir(parents=True, exist_ok=True)
    sources = product_sources()
    require(sources, 'the product has no Kotlin sources to compile')
    completed = subprocess.run(
        [kotlinc, '-jvm-target', '17', *sources, '-d', str(jar)],
        capture_output=True,
        text=True,
        env={**os.environ, 'JAVA_HOME': java_home},
    )
    require(completed.returncode == 0, f'the product did not compile:\n{completed.stderr[-2000:]}')
    return len(sources)


def diagnostics(stderr: str) -> list[str]:
    """Kotlin's error lines, without kotlinc's `error: ` prefix, in the compiler's own order."""
    out = []
    for line in stderr.splitlines():
        marker = 'error: '
        if marker in line:
            out.append(line.split(marker, 1)[1].strip())
    return out


def compile_set(kotlinc: str, jar: Path, directory: str, out: Path, java_home: str) -> tuple[list[str], list[str]]:
    """`(errors, smart_casts)` for one source set compiled as its own module.

    The `srcDir` paths in `build.gradle.kts` are relative to `testing/`, not to the repository root.
    """
    files = sorted(str(p) for p in (TESTING / directory).rglob('*.kt'))
    require(files, f'{directory} holds no Kotlin source')
    completed = subprocess.run(
        [kotlinc, '-jvm-target', '17', '-cp', str(jar), *files, '-d', str(out)],
        capture_output=True,
        text=True,
        env={**os.environ, 'JAVA_HOME': java_home},
    )
    errors = diagnostics(completed.stderr)
    casts = [match for message in errors for match in SMART_CAST.findall(message)]
    return errors, casts


def verify(
    build_file: Path = BUILD_FILE,
    jar: Path | None = None,
    only: list[str] | None = None,
    kotlinc: str | None = None,
    java_home: str | None = None,
    keep: Path | None = None,
    quiet: bool = False,
) -> tuple[int, int, int, int]:
    """`(sets, files, smart_casts, excluded)`; raises Fail on the first disagreement."""
    sets = parse_source_sets(build_file)
    require(sets, f'no source sets were found in {build_file}')
    selected, excluded = selectable(sets)
    require(selected, 'no source set has a reproducible declaration')
    if only:
        unknown = [name for name in only if name not in selected]
        require(not unknown, f'not a compilable source set: {unknown}')
        selected = {name: selected[name] for name in only}
    environment = bootstrap_host_tools.environment(CACHE)
    compiler = kotlinc or environment['KOTLINC']
    home = java_home or environment['JAVA_HOME']
    require(Path(compiler).is_file(), 'run tools/bootstrap_host_tools.py install first (pinned kotlinc)')

    temporary = None
    if keep is None:
        temporary = tempfile.TemporaryDirectory(prefix='r110-boundary-')
        keep = Path(temporary.name)
    keep.mkdir(parents=True, exist_ok=True)
    try:
        product = jar or (keep / 'product.jar')
        if jar is None:
            build_product_jar(compiler, product, home)
        failures: list[str] = []
        compiled = 0
        files = 0
        casts = 0
        for name in sorted(selected):
            directory = selected[name]
            errors, found = compile_set(compiler, product, directory, keep / f'{name}.jar', home)
            files += len(sorted((TESTING / directory).rglob('*.kt')))
            if errors:
                failures.append(f'{name} ({directory}): {len(errors)} error(s)')
                for message in errors[:4]:
                    failures.append(f'    {message}')
                casts += len(found)
            else:
                compiled += 1
            if not quiet:
                print(f'{"ok  " if not errors else "FAIL"} {name:14s} {directory}')
        if failures:
            raise Fail(
                f'{len(failures)} line(s) of disagreement; the source sets that failed are:\n' + '\n'.join(failures),
            )
        print(
            f'R110_BOUNDARIES_OK sets={compiled} files={files} smart_casts={casts} '
            f'excluded={len(sets) - len(selected)} (declared by Gradle, not reproducible here)',
        )
        return compiled, files, casts, len(sets) - len(selected)
    finally:
        if temporary is not None:
            temporary.cleanup()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=None, help='reuse an existing product jar')
    parser.add_argument('--only', action='append', default=None, help='compile one source set (repeatable)')
    parser.add_argument('--list', action='store_true', help='print every source set with its selection reason')
    parser.add_argument('--keep', type=Path, default=None, help='keep the class output in this folder')
    args = parser.parse_args(argv)
    try:
        if args.list:
            selected, excluded = selectable(parse_source_sets())
            for name in sorted(selected):
                print(f'compile\t{name}\t{selected[name]}')
            for name in sorted(excluded):
                print(f'exclude\t{name}\t{excluded[name]}')
            print(f'{len(selected)} compilable, {len(excluded)} excluded')
            return 0
        verify(jar=args.jar, only=args.only, keep=args.keep)
    except Fail as failed:
        print(f'R110 boundary check FAILED: {failed}')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
