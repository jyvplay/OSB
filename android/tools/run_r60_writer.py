#!/usr/bin/env python3
"""Run actual R60 Writer/consent host contracts; Office rendering and Android are separate."""
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
    names = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'NumberFormatRender', 'NumFmtSections', 'FormulaXlsx',
             'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx', 'StructuredWriterDocx')
    sources = [ooxml / (name + '.kt') for name in names]
    sources += [ROOT / 'core/documents/src/main/kotlin/dev/fusion/documents' / (name + '.kt')
                for name in ('TextDocument', 'TextEncoding', 'WriterCopyConsent')]
    sources.append(ROOT / 'testing/src/r60writer/kotlin/dev/fusion/testing/R60WriterChecks.kt')
    out = ROOT / 'testing/build/r60writer'
    artifacts = out / 'artifacts'
    artifacts.mkdir(parents=True, exist_ok=True)
    for path in artifacts.glob('doc-*.docx'):
        if re.fullmatch(r'doc-[0-9]{3}-(?:default|numbered)\.docx', path.name):
            path.unlink()
    jar = out / 'r60-writer.jar'
    jar.unlink(missing_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8',
                    '-cp', str(jar), 'dev.fusion.testing.R60WriterChecks', str(artifacts)],
                   check=True, env=env, timeout=120)


if __name__ == '__main__':
    main()
