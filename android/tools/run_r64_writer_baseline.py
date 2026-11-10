#!/usr/bin/env python3
"""Compare pre-table Writer option outputs with independently compiled pinned R63 sources."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

BASE = 'f41f473f364cbe69a76b77f842fa4738daed3571'
NAMES = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'NumberFormatRender', 'NumFmtSections', 'FormulaXlsx',
         'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx', 'StructuredWriterDocx')


def main():
    current = ROOT / 'testing/build/r63writer/artifacts'
    if not (current / 'cases.csv').is_file():
        raise SystemExit('Run current R63 Writer corpus first')
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    with tempfile.TemporaryDirectory(prefix='r64-writer-baseline-') as directory:
        folder = Path(directory)
        paths = [f'android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/{name}.kt' for name in NAMES]
        paths.append('android/testing/src/r63writer/kotlin/dev/fusion/testing/R63WriterRestartChecks.kt')
        source_files = []
        skipped: list[str] = []
        for path in paths:
            if subprocess.run(['git', 'cat-file', '-e', f'{BASE}:{path}'], cwd=ROOT.parent,
                              capture_output=True).returncode != 0:
                skipped.append(path)
                continue
            data = subprocess.check_output(['git', 'show', f'{BASE}:{path}'], cwd=ROOT.parent)
            dest = folder / Path(path).name
            dest.write_bytes(data)
            source_files.append(dest)
        jar = folder / 'baseline.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *map(str, source_files), '-d', str(jar)], env=env, check=True)
        baseline = folder / 'artifacts'
        baseline.mkdir()
        subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R63WriterRestartChecks', str(baseline)], env=env, check=True, timeout=120)
        old = {p.name for p in baseline.iterdir()}
        new = {p.name for p in current.iterdir()}
        if old != new:
            raise SystemExit('Legacy Writer corpus artifact names changed')
        for name in sorted(old):
            if (baseline / name).read_bytes() != (current / name).read_bytes():
                raise SystemExit(f'Legacy Writer corpus bytes changed: {name}')
        print(f'R64 pinned {BASE[:7]} vs current: {len(old)} legacy Writer artifacts byte-identical')


if __name__ == '__main__':
    main()
