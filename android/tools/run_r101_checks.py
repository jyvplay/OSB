#!/usr/bin/env python3
"""R101 office runner: compile the ooxml module plus the R101 probe, then run the independent checks.

The R101 probe closes the `cfRule` kind set: `containsBlanks`, `notContainsBlanks`, `containsErrors`,
`notContainsErrors`, `timePeriod` (all seventeen presets) and `iconSet` (3, 4 and 5 icons, with `showValue`,
`percent` and `reverse`), plus verdicts for each. Together with R96 and R100 that is every kind the base schema
defines.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format/archive` + `:format/ooxml` + `testing/src/r101/.../R101Checks.kt` with the pinned host
   kotlinc and `-Werror`;
2. run the probe, which writes `r101.xlsx` (twenty rules, one per kind plus three icon sets), `r101-presets.xlsx`
   (all seventeen `timePeriod` presets, kept apart because openpyxl cannot parse seven of them) and
   `r101-foreign.xlsx`, plus `kinds.tsv`, `rules.tsv`, `values.tsv`, `calendar.tsv`, `verdicts.tsv`,
   `summary.txt` into `testing/build/r101/artifacts`;
3. `tools/check_r101_kinds.py` — raw OPC attributes and the `<iconSet>` child, openpyxl 3.1.5's reading of the
   same rules and its documented refusal of the seven non-UI presets, the preset workbook's order, and a Python
   re-derivation of all 51 verdicts with `datetime` as the date oracle.

Host JVM only. No Excel, LibreOffice, browser or Android device is involved: no icon was drawn, and the verdicts
are claims about this evaluator rather than about a spreadsheet.
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
ARTIFACTS = ROOT / 'testing/build/r101/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r101/kotlin/dev/fusion/testing/R101Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r101-tail-') as temporary:
        jar = Path(temporary) / 'r101kinds.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R101Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r101_kinds.py'),
                    str(args.artifacts), '--require-openpyxl'], env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
