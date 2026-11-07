#!/usr/bin/env python3
"""Compile and execute bounded values-only CSV exports; not Android or Excel."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    src = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml'
    names = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'FormulaXlsx',
             'OfficeSheetDraft', 'OfficeSheetCsv')
    files = [src / (name + '.kt') for name in names]
    files.append(ROOT / 'testing/src/r64csv/kotlin/dev/fusion/testing/R64SheetCsvChecks.kt')
    out = ROOT / 'testing/build/r64csv'; artifacts = out / 'artifacts'
    for directory in (ROOT / 'testing', ROOT / 'testing/build', out, artifacts):
        if directory.is_symlink(): raise SystemExit('Symlink build/artifact directory refused')
    artifacts.mkdir(parents=True, exist_ok=True)
    for path in artifacts.iterdir():
        if path.is_symlink(): raise SystemExit('Symlink artifact refused')
        if path.name == 'cases.csv' or re.fullmatch(r'sheet-[0-9]{3}\.csv', path.name):
            if not path.is_file(): raise SystemExit('Non-file artifact refused')
            path.unlink()
    jar = out / 'r64-csv.jar'; jar.unlink(missing_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, files), '-d', str(jar)], env=env, check=True)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8', '-cp', str(jar),
                    'dev.fusion.testing.R64SheetCsvChecks', str(artifacts)], env=env, check=True, timeout=120)


if __name__ == '__main__':
    main()
