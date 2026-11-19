#!/usr/bin/env python3
"""R110 office runner: compile the product once, then check every `:format:ooxml`-only probe across the boundary.

The defect this round repairs could not be seen by any of the seventeen host runners before it: they compile the
product and the probe in **one** kotlinc invocation, and Kotlin allows a smart cast there. Gradle gives each probe
source set its own compilation unit with `:format:ooxml` as a dependency, and refuses the same code — which is why
`:testing:assemble` has been red since before R92 and why no JVM test had ever executed in CI.

Steps, in order, all required:

1. build one jar from `format/archive` + `format/ooxml` with the pinned host kotlinc;
2. `tools/check_r110_probe_boundaries.py --jar …` — compile each of the 56 `:format:ooxml`-only probe source sets
   against that jar, separately, and require zero diagnostics;
3. `tools/test_r110_probe_boundaries.py` — 13 controls, including a self-contained two-compilation reproduction of
   the Kotlin rule (pre-repair shape fails, repaired shape compiles) and the gate run on the real tree;
4. run the R93 probe itself and print its summary line, to show the repair changed no assertion.

Host JVM only: kotlinc is a real compiler and its diagnostics are the verdict, but this is not Gradle execution, not
Android execution, and not a rendered cell.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
READERS = Path.home() / '.cache/fusion-r76-readers'
BUILD = ROOT / 'testing/build/r110'
PRODUCT_JAR = BUILD / 'product.jar'
ARTIFACTS = ROOT / 'testing/build/r93/artifacts'


def product_sources() -> list[str]:
    return sorted(
        str(p)
        for directory in ('format/archive/src/main/kotlin', 'format/ooxml/src/main/kotlin')
        for p in (ROOT / directory).rglob('*.kt')
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-lanes', action='store_true', help='only build the product jar')
    parser.add_argument('--only', action='append', default=None, help='limit the boundary check to one source set')
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    BUILD.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [env['KOTLINC'], '-jvm-target', '17', *product_sources(), '-d', str(PRODUCT_JAR)],
        env=env,
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )
    print(f'R110_PRODUCT_JAR sources={len(product_sources())} jar={PRODUCT_JAR.relative_to(ROOT)}')
    if args.skip_lanes:
        return 0

    boundary = [sys.executable, str(ROOT / 'tools/check_r110_probe_boundaries.py'), '--jar', str(PRODUCT_JAR)]
    if args.only:
        for name in args.only:
            boundary += ['--only', name]
    subprocess.run(boundary, env=env, check=True)

    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    verify['R110_PRODUCT_JAR'] = str(PRODUCT_JAR)
    subprocess.run(
        [sys.executable, '-m', 'unittest', 'test_r110_probe_boundaries'],
        cwd=str(ROOT / 'tools'),
        env=verify,
        check=True,
    )

    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='r110-probe-') as temporary:
        probe_jar = Path(temporary) / 'r93.jar'
        subprocess.run(
            [
                env['KOTLINC'], '-jvm-target', '17', '-Werror', '-cp', str(PRODUCT_JAR),
                str(ROOT / 'testing/src/r93/kotlin/dev/fusion/testing/R93Checks.kt'), '-include-runtime',
                '-d', str(probe_jar),
            ],
            env=env,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        completed = subprocess.run(
            [
                str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8',
                '-cp', f'{probe_jar}{os.pathsep}{PRODUCT_JAR}',
                'dev.fusion.testing.R93Checks', str(ARTIFACTS),
            ],
            env=env,
            check=True,
            capture_output=True,
            text=True,
        )
        print(completed.stdout.strip().splitlines()[-1])
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
