#!/usr/bin/env python3
"""R106 office runner: compile the ooxml module plus the R106 probe, then run the independent checks.

R106 adds the criteria functions `COUNTIF`, `SUMIF` and `AVERAGEIF` to the range grammar R105 introduced, and with
them the *criterion* value grammar: a comparison written as text (`">5"`, `"<=0"`, `"<>draft"`, `""`, `"<>"`), a
case-insensitive wildcard pattern (`"a*"`, `"?p*"`, `"~*"` for a literal star) and numeric-text folding. `COUNTIF`
and `SUMIF` are the most used non-trivial spreadsheet functions after `SUM`, and both were refused by name before.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was skipped
and why, rather than reporting a pass):

1. compile `:format/archive` + `:format:ooxml` + `testing/src/r106/.../R106Checks.kt` with the pinned host kotlinc
   and `-Werror`;
2. run the probe, which decides 64 hand-written criteria cases, writes `r106.xlsx` (twelve rules) and `cases.tsv`,
   `rules.tsv`, `values.tsv`, `summary.txt` into `testing/build/r106/artifacts`;
3. `tools/check_r106_criteria.py` — an independent Python tokenizer/parser/evaluator with its own criterion parser
   and its own dynamic-programming wildcard matcher, requiring the table, the Kotlin answer and its own answer to
   agree, plus the raw `<formula>` text and openpyxl 3.1.5's reading of the same twelve rules;
4. `tools/test_r106_criteria.py` — 39 controls on that Python reference implementation.

Host JVM only. No Excel, LibreOffice, browser or Android device is involved: a verdict is a claim about which rule
fires, never about what a screen shows.
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
READERS = Path.home() / '.cache/fusion-r76-readers'
ARTIFACTS = ROOT / 'testing/build/r106/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r106/kotlin/dev/fusion/testing/R106Checks.kt')
    return archive + ooxml + [probe]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    parser.add_argument('--skip-lanes', action='store_true', help='only write the artifacts')
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r106-tail-') as temporary:
        jar = Path(temporary) / 'r106criteria.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R106Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r106_criteria.py'),
                    str(args.artifacts), '--require-openpyxl'], env=verify, check=True)
    subprocess.run([sys.executable, '-m', 'unittest', 'test_r106_criteria'], cwd=str(ROOT / 'tools'),
                   env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
