#!/usr/bin/env python3
"""Compile and execute real Writer and spreadsheet production sources; no Android claim."""
import os
from pathlib import Path
import subprocess
from bootstrap_host_tools import DEFAULT, environment

ROOT = Path(__file__).resolve().parents[1]

def main():
    env = environment(DEFAULT)
    compiler = os.environ.get('KOTLINC', env['KOTLINC'])
    java = Path(os.environ.get('JAVA_HOME', env['JAVA_HOME'])) / 'bin/java'
    out = ROOT / 'testing/build/r54'
    out.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / 'feature/editor/src/main/java/dev/fusion/editor' / (n + '.kt') for n in
               ['WriterMailMergeEngine', 'WriterFootnotePaginator', 'WriterTrackChangesAcceptRejectEngine']]
    sources += [ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml' / (n + '.kt') for n in
                ['FormulaEval', 'ColLetters', 'SheetRange', 'CellErr']]
    sources += [ROOT / 'testing/src/r54/R54Checks.kt']
    subprocess.run([compiler, '-Werror', *map(str, sources), '-include-runtime', '-d', str(out / 'checks.jar')], env=env, check=True)
    subprocess.run([str(java), '-jar', str(out / 'checks.jar')], check=True)

if __name__ == '__main__':
    main()
