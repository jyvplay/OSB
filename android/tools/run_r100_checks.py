#!/usr/bin/env python3
"""R100 office runner: compile the ooxml module plus the R100 probe, then run the independent check.

The R100 probe covers the seven conditional-format kinds that state a predicate — `containsText`,
`notContainsText`, `beginsWith`, `endsWith` (with their `text` and their own `operator` token), `aboveAverage`
(with `aboveAverage`/`equalAverage`/`stdDev`), `duplicateValues` and `uniqueValues` — and the verdicts they
produce, including the coercion of a number to its displayed text and the refusal of a blank `text`.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format/archive` + `:format/ooxml` + `testing/src/r100/.../R100Checks.kt` with the pinned host
   kotlinc and `-Werror`;
2. run the probe, which writes `r100.xlsx`, `r100-foreign.xlsx`, `kinds.tsv`, `rules.tsv`, `values.tsv`,
   `verdicts.tsv` and `summary.txt` into `testing/build/r100/artifacts`;
3. `tools/check_r100_conditional.py` — raw OPC attributes, `dxfs` resolution, openpyxl 3.1.5's reading of the
   same rules, and a Python re-derivation of all 31 verdicts.

Host JVM only. No Excel, LibreOffice, browser or Android device is involved: no cell was painted, and the
verdicts are claims about this evaluator rather than about a spreadsheet.
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
ARTIFACTS = ROOT / 'testing/build/r100/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r100/kotlin/dev/fusion/testing/R100Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r100-tail-') as temporary:
        jar = Path(temporary) / 'r100cf.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R100Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r100_conditional.py'),
                    str(args.artifacts), '--require-openpyxl'], env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
