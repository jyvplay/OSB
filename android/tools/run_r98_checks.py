#!/usr/bin/env python3
#!/usr/bin/env python3
"""R98 office runner: compile the ooxml module plus the R98 probe, then run every independent check.

The R98 probe covers the two halves of print layout that R94 left out: fit-to-page/page numbering on the sheet,
and the sheet-scoped `_xlnm.Print_Area` / `_xlnm.Print_Titles` records in the workbook.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format/archive` + `:format/ooxml` + `testing/src/r98/.../R98Checks.kt` with the pinned host
   kotlinc and `-Werror`;
2. run the probe, which writes `r98.xlsx`, `r98-plain.xlsx`, `r98-foreign.xlsx`, `r98-foreign-fit.xlsx`,
   `print.tsv` and `names.tsv` into `testing/build/r98/artifacts`;
3. `tools/check_r98_sheet_print.py` — raw OPC plus openpyxl's `page_setup`, `sheet_properties.pageSetUpPr` and
   page margins, compared to `print.tsv` sheet by sheet;
4. `tools/check_r98_print_records.py` — raw OPC plus openpyxl's `defined_names`/`print_area`/`print_title_*`,
   compared to `names.tsv`, including quoting and the fold-back-on-read contract.

Host JVM only. No Excel, LibreOffice, browser or Android device is involved: no page was paginated or printed.
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
ARTIFACTS = ROOT / 'testing/build/r98/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r98/kotlin/dev/fusion/testing/R98Checks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r98-tail-') as temporary:
        jar = Path(temporary) / 'r98print.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R98Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    for command in ([sys.executable, str(ROOT / 'tools/check_r98_sheet_print.py'),
                     str(args.artifacts), '--require-openpyxl'],
                    [sys.executable, str(ROOT / 'tools/check_r98_print_records.py'),
                     str(args.artifacts), '--require-openpyxl']):
        subprocess.run(command, env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
