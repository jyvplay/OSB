#!/usr/bin/env python3
"""Compile and execute R57 Writer JVM contracts; does not claim Android execution."""
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
             'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx', 'StructuredWriterDocx')
    sources = [ooxml / (name + '.kt') for name in names]
    sources.append(ROOT / 'testing/src/r57/kotlin/dev/fusion/testing/R57Checks.kt')
    out = ROOT / 'testing/build/r57'
    out.mkdir(parents=True, exist_ok=True)
    jar = out / 'r57-contracts.jar'
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8',
                    '-cp', str(jar), 'dev.fusion.testing.R57Checks', str(out / 'artifacts')],
                   check=True, env=env, timeout=120)


if __name__ == '__main__':
    main()
