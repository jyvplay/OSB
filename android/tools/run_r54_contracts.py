#!/usr/bin/env python3
"""Compile and execute the R54 real JVM office/copy contracts; no Android SDK is implied."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    compiler = env['KOTLINC']
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    out = ROOT / 'testing/build/r54'
    out.mkdir(parents=True, exist_ok=True)
    ooxml = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml'
    docs = ROOT / 'core/documents/src/main/kotlin/dev/fusion/documents'
    source = [ooxml / (name + '.kt') for name in [
        'BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'SpillSheetEngine',
        'FormulaXlsx', 'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx',
    ]] + [docs / (name + '.kt') for name in [
        'TextDocument', 'TextEncoding', 'SessionCodec', 'CopyProtocol', 'VerifiedCopy', 'FrozenExport',
    ]] + [ROOT / 'feature/editor/src/main/java/dev/fusion/editor/MarkdownOfficeBridge.kt',
          ROOT / 'testing/src/r54/kotlin/dev/fusion/testing/R54Checks.kt']
    jar = out / 'r54-contracts.jar'
    subprocess.run([compiler, '-jvm-target', '17', '-Werror', '-include-runtime', *map(str, source), '-d', str(jar)], env=env, check=True)
    subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar), 'dev.fusion.testing.R54Checks', str(out / 'artifacts')], env=env, check=True, timeout=120)


if __name__ == '__main__':
    main()
