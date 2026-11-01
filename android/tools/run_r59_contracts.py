#!/usr/bin/env python3
"""Compile/run actual R59 production Kotlin on the host; not Android or Excel evidence."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    ooxml = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml'
    names = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'FormulaXlsx',
             'OfficeSheetDraft', 'OfficeSheetTextGrid')
    sources = [ooxml / (name + '.kt') for name in names]
    sources.append(ROOT / 'testing/src/r59/kotlin/dev/fusion/testing/R59Checks.kt')
    out = ROOT / 'testing/build/r59'
    out.mkdir(parents=True, exist_ok=True)
    jar = out / 'r59-contracts.jar'
    # Never let a failed compile leave a previous candidate's output looking current.
    for path in (jar, *(out / 'artifacts' / name for name in ('typed-input.tsv', 'typed.xlsx', 'dependent.xlsx', 'classifications.tsv'))):
        path.unlink(missing_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8',
                    '-cp', str(jar), 'dev.fusion.testing.R59Checks', str(out / 'artifacts')],
                   check=True, env=env, timeout=120)


if __name__ == '__main__':
    main()
