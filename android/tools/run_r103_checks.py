#!/usr/bin/env python3
"""R103 office runner: compile the ooxml module plus the R103 probe, then run the independent checks.

R103 fixes what a conditional-format formula's cell references *mean*. Excel reads them relative to the cell being
evaluated, anchored at the top-left of the range token that covers it, with `$` locking one coordinate. Until R103
this evaluator read the literal reference every time — so every cell except the anchor got the anchor's answer —
and `$`-locked references were not even parseable.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format/archive` + `:format:ooxml` + `testing/src/r103/.../R103Checks.kt` with the pinned host kotlinc
   and `-Werror`;
2. run the probe, which writes `r103.xlsx` (ten rules: relative, both `$` locks, both mixed locks, a multi-token
   `sqref`, two `ISBLANK` shapes, a cross-sheet reference, a `cellIs` operand and a grid-edge case) and
   `rules.tsv`, `values.tsv`, `verdicts.tsv`, `summary.txt` into `testing/build/r103/artifacts`;
3. `tools/check_r103_shift.py` — the raw OPC formulas, openpyxl 3.1.5's reading of the same formulas, and a Python
   re-derivation of every verdict from an independent implementation of the shift rule.

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
ARTIFACTS = ROOT / 'testing/build/r103/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r103/kotlin/dev/fusion/testing/R103Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r103-tail-') as temporary:
        jar = Path(temporary) / 'r103shift.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R103Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r103_shift.py'),
                    str(args.artifacts), '--require-openpyxl'], env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
