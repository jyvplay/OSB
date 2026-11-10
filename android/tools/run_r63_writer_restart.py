#!/usr/bin/env python3
"""Compile and execute the actual R63 Writer list-instance corpus; not Word or Android."""
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
    names = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'NumberFormatRender', 'NumFmtSections', 'FormulaXlsx',
             'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx', 'StructuredWriterDocx')
    files = [src / (name + '.kt') for name in names]
    files.append(ROOT / 'testing/src/r63writer/kotlin/dev/fusion/testing/R63WriterRestartChecks.kt')
    out = ROOT / 'testing/build/r63writer'
    artifacts = out / 'artifacts'
    artifacts.mkdir(parents=True, exist_ok=True)
    for path in artifacts.iterdir():
        if path.is_symlink():
            raise SystemExit('Symlink artifact refused')
        if path.name == 'cases.csv' or re.fullmatch(r'doc-[0-9]{3}\.docx', path.name):
            if not path.is_file():
                raise SystemExit('Non-file artifact refused')
            path.unlink()
    jar = out / 'r63-writer.jar'
    jar.unlink(missing_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, files), '-d', str(jar)], env=env, check=True)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8', '-cp', str(jar),
                    'dev.fusion.testing.R63WriterRestartChecks', str(artifacts)], env=env, check=True, timeout=120)


if __name__ == '__main__':
    main()
