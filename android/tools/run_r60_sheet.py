#!/usr/bin/env python3
"""Run R60 transposed-grid production contracts on host; not an Android or Excel test."""
from pathlib import Path
import re
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
    sources.append(ROOT / 'testing/src/r60sheet/kotlin/dev/fusion/testing/R60SheetChecks.kt')
    out = ROOT / 'testing/build/r60sheet'
    artifacts = out / 'artifacts'
    artifacts.mkdir(parents=True, exist_ok=True)
    for path in artifacts.iterdir():
        if re.fullmatch(r'(?:input-[0-9]{3}\.tsv|transposed-[0-9]{3}\.xlsx)', path.name):
            path.unlink()
    jar = out / 'r60-sheet.jar'
    jar.unlink(missing_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8',
                    '-cp', str(jar), 'dev.fusion.testing.R60SheetChecks', str(artifacts)],
                   check=True, env=env, timeout=120)


if __name__ == '__main__':
    main()
