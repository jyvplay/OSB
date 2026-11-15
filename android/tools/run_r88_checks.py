#!/usr/bin/env python3
"""R88 office runner: compile the ooxml module plus the R88 probe, then run every independent check.

Steps, in order, all of them required unless the tooling is missing (in which case the runner says which
lane was skipped and why, rather than reporting a pass):

1. compile `:format:archive` + `:format:ooxml` + `testing/src/r88/.../R88Checks.kt` with the pinned host
   kotlinc and `-Werror`;
2. run the probe, which writes the R88 `.docx`/`.xlsx` packages and the `borders.tsv` / `grid.tsv` intent
   tables into `testing/build/r88/artifacts`;
3. `tools/check_r88_writer_borders.py --require-python-docx` (raw OPC + python-docx's lxml tree);
4. `tools/check_r88_sheet_gridlines.py --require-openpyxl` (raw OPC + openpyxl);
5. the docx-preview render lane (`tools/check_r87_docx_render.py --r88`), which turns each `w:pBdr` edge
   into CSS border width/style/colour;
6. the exceljs reader lane (`tools/check_r87_sheet_exceljs.py --r88`), which reports `showGridLines`.

Every lane is a host-JVM/host-Node check. No Word, Excel, LibreOffice, Android device or browser is
involved, and no pixel of a Word or Excel rendering exists anywhere in this repository.
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
NODE_MODULES = ROOT / 'tools/r87node/node_modules'
R86_ARTIFACTS = ROOT / 'testing/build/r86sheet/artifacts'
ARTIFACTS = ROOT / 'testing/build/r88/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r88/kotlin/dev/fusion/testing/R88Checks.kt')
    return archive + ooxml + [probe]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    parser.add_argument('--skip-render', action='store_true', help='skip the two third-party lanes')
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r88-office-') as temporary:
        jar = Path(temporary) / 'r88office.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R88Checks', str(args.artifacts)],
                       env=env, check=True)
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r88_writer_borders.py'),
                    str(args.artifacts), '--require-python-docx'], check=True, env=verify)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r88_sheet_gridlines.py'),
                    str(args.artifacts), '--require-openpyxl'], check=True, env=verify)
    if args.skip_render:
        print('R88 third-party lanes skipped by request (--skip-render).')
        return 0
    if not NODE_MODULES.is_dir():
        print('R88 renderer lane skipped: tools/r87node/node_modules is absent '
              '(run `npm ci --prefix tools/r87node`).')
        return 0
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_docx_render.py'),
                    '--r88', str(args.artifacts)], check=True, env=verify)
    if (R86_ARTIFACTS / 'views.tsv').is_file():
        subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_sheet_exceljs.py'),
                        str(R86_ARTIFACTS), '--r88', str(args.artifacts)], check=True, env=verify)
    else:
        print(f'R88 exceljs lane skipped: {R86_ARTIFACTS}/views.tsv is absent '
              '(run tools/run_r86_sheet_views.py).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
