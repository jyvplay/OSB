#!/usr/bin/env python3
"""R94 office runner: compile the ooxml module plus the R94 probe, then run every independent check.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format/archive` + `:format/ooxml` + `testing/src/r94/.../R94Checks.kt` with the pinned host
   kotlinc and `-Werror`;
2. run the probe, which writes the R94 `.xlsx` packages and the intent tables `merges.tsv`, `hyperlinks.tsv`
   and `print.tsv` into `testing/build/r94/artifacts`;
3. `tools/check_r94_sheet_structure.py --require-openpyxl` (raw OPC, the worksheet relationship parts and
   openpyxl's `merged_cells` / `cell.hyperlink` models);
4. `tools/check_r94_sheet_print.py --require-openpyxl` (raw OPC + openpyxl's `page_margins`, `print_options`
   and `page_setup` models).

Host JVM only. No Excel, LibreOffice, browser or Android device is involved.
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
ARTIFACTS = ROOT / 'testing/build/r94/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r94/kotlin/dev/fusion/testing/R94Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r94-tail-') as temporary:
        jar = Path(temporary) / 'r94tail.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R94Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    for command in ([sys.executable, str(ROOT / 'tools/check_r94_sheet_structure.py'),
                     str(args.artifacts), '--require-openpyxl'],
                    [sys.executable, str(ROOT / 'tools/check_r94_sheet_print.py'),
                     str(args.artifacts), '--require-openpyxl']):
        subprocess.run(command, env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
