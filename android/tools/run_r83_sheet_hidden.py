#!/usr/bin/env python3
"""One command for the R83 spreadsheet hidden-dimension slice: compile, observe, verify.

(1) compile :format:ooxml plus the R83 sheet probe with the pinned host kotlinc and -Werror;
(2) run the probe, which writes real .xlsx workbooks hiding rows and columns beside widths and
    heights, plus one workbook that hides nothing;
(3) verify every workbook with openpyxl and raw OPC inspection.

Host-only: no Excel, LibreOffice or Android execution is involved.
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
ARTIFACTS = ROOT / 'testing/build/r83sheet/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r83sheet/kotlin/dev/fusion/testing/R83SheetHiddenChecks.kt')
    return archive + ooxml + [probe]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r83-hidden-') as temporary:
        jar = Path(temporary) / 'r83hidden.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R83SheetHiddenChecks', str(args.artifacts)],
                       env=env, check=True)
    verify = dict(env)
    if READERS.is_dir():
        # python-docx/openpyxl may live only in a disposable reader cache. Append it so the verifier is
        # self-sufficient, and keep any already-installed interpreter ahead of it on the path.
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r83_sheet_hidden.py'),
                    str(args.artifacts), '--require-openpyxl'], check=True, env=verify)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
