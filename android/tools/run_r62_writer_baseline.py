#!/usr/bin/env python3
"""Compare pre-R62 Writer overloads against the pinned predecessor, finite cases only."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

BASELINE = 'e89d1a21416b6f6c6d9fd5a01acf4d0d6a6b628c'
NAMES = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'NumberFormatRender', 'NumFmtSections', 'FormulaXlsx',
         'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx', 'StructuredWriterDocx')


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    with tempfile.TemporaryDirectory(prefix='r62-writer-baseline-') as temp:
        folder = Path(temp); old = folder / 'old'; new = folder / 'new'; old.mkdir(); new.mkdir()
        sources = []
        for name in NAMES:
            rel = f'android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/{name}.kt'
            if subprocess.run(['git', 'cat-file', '-e', f'{BASELINE}:{rel}'], cwd=REPO,
                              capture_output=True).returncode != 0:
                # Paths added after the pinned commit (e.g. new production files) are absent-at-base.
                continue
            data = subprocess.check_output(['git', 'show', f'{BASELINE}:{rel}'], cwd=REPO)
            path = folder / (name + '.kt'); path.write_bytes(data); sources.append(path)
        probe = ROOT / 'testing/src/r62writer/kotlin/dev/fusion/testing/R62WriterLegacyProbe.kt'
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        for target, files in ((old, sources), (new, [ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml' / (name + '.kt') for name in NAMES])):
            jar = target / 'probe.jar'
            subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                            *map(str, files), str(probe), '-d', str(jar)], env=env, check=True)
            subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar), 'dev.fusion.testing.R62WriterLegacyProbe', str(target)],
                           env=env, check=True, timeout=120)
        for i in range(128):
            name = f'doc-{i:03d}.docx'
            if (old / name).read_bytes() != (new / name).read_bytes(): raise ValueError('Pinned legacy DOCX differs: ' + name)
        print(json.dumps({'baseline': BASELINE, 'old_source_sha256': hashlib.sha256((folder / 'StructuredWriterDocx.kt').read_bytes()).hexdigest(),
                          'byte_identical': 128, 'scope': 'Finite legacy-overload byte comparison only; not Word rendering.'}))


if __name__ == '__main__': main()
