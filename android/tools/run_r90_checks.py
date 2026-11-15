#!/usr/bin/env python3
"""R90 office runner: compile the ooxml module plus the R90 probe, then run every independent check.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format:archive` + `:format:ooxml` + `testing/src/r90/.../R90Checks.kt` with the pinned host
   kotlinc and `-Werror`;
2. run the probe, which writes the R90 `.docx`/`.xlsx` packages and the `decorations.tsv` / `decorations_xlsx.tsv` intent
   tables into `testing/build/r90/artifacts`;
3. `tools/check_r90_writer_decorations.py --require-python-docx` (raw OPC + python-docx's Font API (superscript/subscript/highlight));
4. `tools/check_r90_sheet_decorations.py --require-openpyxl` (raw OPC + openpyxl's Font object (u/strike));
5. the docx-preview render lane (`tools/check_r87_docx_render.py --r90`), which turns each `<w:highlight>` into CSS `background-color` and reports how many `w:vertAlign` values the
   renderer drops;
6. the exceljs reader lane (`tools/check_r87_sheet_exceljs.py --r90`), which reports `font.underline`/`font.strike`.

Host JVM and host Node only. No Word, Excel, LibreOffice, Android device or browser is involved.
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
ARTIFACTS = ROOT / 'testing/build/r90/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r90/kotlin/dev/fusion/testing/R90Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r90-deco-') as temporary:
        jar = Path(temporary) / 'r90deco.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R90Checks', str(args.artifacts)], env=env, check=True)
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r90_writer_decorations.py'),
                    str(args.artifacts), '--require-python-docx'], check=True, env=verify)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r90_sheet_decorations.py'),
                    str(args.artifacts), '--require-openpyxl'], check=True, env=verify)
    if args.skip_render:
        print('R90 third-party lanes skipped by request (--skip-render).')
        return 0
    if not NODE_MODULES.is_dir():
        print('R90 renderer lane skipped: tools/r87node/node_modules is absent '
              '(run `npm ci --prefix tools/r87node`).')
        return 0
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_docx_render.py'),
                    '--r90', str(args.artifacts)], check=True, env=verify)
    if (R86_ARTIFACTS / 'views.tsv').is_file():
        subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_sheet_exceljs.py'),
                        str(R86_ARTIFACTS), '--r90', str(args.artifacts)], check=True, env=verify)
    else:
        print(f'R90 exceljs lane skipped: {R86_ARTIFACTS}/views.tsv is absent '
              '(run tools/run_r86_sheet_views.py).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
