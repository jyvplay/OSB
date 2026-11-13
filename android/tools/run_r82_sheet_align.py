#!/usr/bin/env python3
"""One command for the R82 spreadsheet cell-alignment slice: compile, observe, verify.

(1) compile :format:ooxml plus the R82 sheet probe with the pinned host kotlinc and -Werror;
(2) run the probe, which writes real .xlsx workbooks declaring horizontal and vertical cell
    alignment beside fonts, fills, borders and number formats, plus one workbook that declares none;
(3) verify every workbook with openpyxl and raw OPC inspection.

Host-only: no Excel, LibreOffice or Android execution is involved.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
ARTIFACTS = ROOT / 'testing/build/r82sheet/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r82sheet/kotlin/dev/fusion/testing/R82SheetAlignChecks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r82-align-') as temporary:
        jar = Path(temporary) / 'r82alignsheet.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R82SheetAlignChecks', str(args.artifacts)],
                       env=env, check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r82_sheet_align.py'),
                    str(args.artifacts), '--require-openpyxl'], check=True, env=env)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
