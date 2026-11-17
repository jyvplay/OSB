#!/usr/bin/env python3
"""R104 office runner: compile the ooxml module plus the R104 probe, then run the independent checks.

R104 replaces the regex reader an `expression` rule used to get with a tokenizer, a recursive-descent parser and an
evaluator over sixteen functions — `AND`, `OR`, `NOT`, `ISBLANK`, `ISNUMBER`, `ISTEXT`, `LEN`, `TRIM`, `UPPER`,
`LOWER`, `LEFT`, `RIGHT`, `ABS`, `ROUND`, `MOD`, `ROW`. Before this, a conditional format that said
`AND(A1>5,B1<2)` was simply *not decided*, and the answer to "did the rule fire?" was therefore unknown for every
formula in the wild that was not one of the two regex shapes.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was skipped
and why, rather than reporting a pass):

1. compile `:format/archive` + `:format:ooxml` + `testing/src/r104/.../R104Checks.kt` with the pinned host kotlinc
   and `-Werror`;
2. run the probe, which decides 97 hand-written formula cases, writes `r104.xlsx` (fourteen expression rules) and
   `cases.tsv`, `rules.tsv`, `values.tsv`, `summary.txt` into `testing/build/r104/artifacts`;
3. `tools/check_r104_grammar.py` — an independent Python tokenizer/parser/evaluator that re-decides every case and
   requires three answers to agree (the table's expectation, the Kotlin answer, its own), plus the raw
   `<formula>` text and openpyxl 3.1.5's reading of the same fourteen rules;
4. `tools/test_r104_grammar.py` — 32 controls on that Python reference implementation, because an independent lane
   that is itself untested is just a second opinion from the same author.

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
ARTIFACTS = ROOT / 'testing/build/r104/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r104/kotlin/dev/fusion/testing/R104Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r104-tail-') as temporary:
        jar = Path(temporary) / 'r104grammar.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R104Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r104_grammar.py'),
                    str(args.artifacts), '--require-openpyxl'], env=verify, check=True)
    subprocess.run([sys.executable, '-m', 'unittest', 'test_r104_grammar'], cwd=str(ROOT / 'tools'),
                   env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
