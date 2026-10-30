#!/usr/bin/env python3
"""Compile and run R55 actual JVM formula/recovery contracts; this is not Android execution."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    source_dir = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml'
    names = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval',
             'SpillSheetEngine', 'FormulaXlsx', 'MultiSheetBook', 'OfficeSheetDraft')
    sources = [source_dir / (name + '.kt') for name in names]
    sources.append(ROOT / 'testing/src/r55/kotlin/dev/fusion/testing/R55Checks.kt')
    out = ROOT / 'testing/build/r55'
    out.mkdir(parents=True, exist_ok=True)
    jar = out / 'r55-contracts.jar'
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                    'dev.fusion.testing.R55Checks', str(out / 'artifacts')],
                   check=True, env=env, timeout=120)


if __name__ == '__main__':
    main()
