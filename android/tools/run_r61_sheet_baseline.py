#!/usr/bin/env python3
"""Compare the 64 R60 default-policy workbooks against pinned actual predecessor sources."""
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

BASELINE = '86b39d9047071f756e1623300fe8399af28ad01d'
NAMES = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'FormulaXlsx',
         'OfficeSheetDraft', 'OfficeSheetTextGrid')


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    subprocess.run([sys.executable, str(ROOT / 'tools/run_r60_sheet.py')], check=True, env=env)
    with tempfile.TemporaryDirectory(prefix='r61-sheet-baseline-') as temp:
        folder = Path(temp)
        sources = []
        for name in NAMES:
            rel = f'android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/{name}.kt'
            data = subprocess.check_output(['git', 'show', f'{BASELINE}:{rel}'], cwd=REPO)
            file = folder / f'{name}.kt'; file.write_bytes(data); sources.append(file)
        rel = 'android/testing/src/r60sheet/kotlin/dev/fusion/testing/R60SheetChecks.kt'
        probe = folder / 'R60SheetChecks.kt'
        probe.write_bytes(subprocess.check_output(['git', 'show', f'{BASELINE}:{rel}'], cwd=REPO))
        sources.append(probe)
        legacy_probe = ROOT / 'testing/src/r61sheet/kotlin/dev/fusion/testing/R61LegacyGridProbe.kt'
        jar = folder / 'baseline.jar'; artifacts = folder / 'artifacts'; artifacts.mkdir()
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *map(str, sources), str(legacy_probe), '-d', str(jar)], env=env, check=True)
        subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R60SheetChecks', str(artifacts)], env=env, check=True, timeout=120)
        current = ROOT / 'testing/build/r60sheet/artifacts'
        for index in range(64):
            for prefix, suffix in (('input-', '.tsv'), ('transposed-', '.xlsx')):
                name = f'{prefix}{index:03d}{suffix}'
                if (artifacts / name).read_bytes() != (current / name).read_bytes():
                    raise ValueError('Pinned predecessor differs: ' + name)
        old_legacy = folder / 'old-legacy'; new_legacy = folder / 'new-legacy'
        old_legacy.mkdir(); new_legacy.mkdir()
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar), 'dev.fusion.testing.R61LegacyGridProbe', str(old_legacy)],
                       env=env, check=True, timeout=120)
        current_sources = [ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml' / (name + '.kt') for name in NAMES]
        current_jar = folder / 'candidate.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *map(str, current_sources), str(legacy_probe), '-d', str(current_jar)], env=env, check=True)
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(current_jar), 'dev.fusion.testing.R61LegacyGridProbe', str(new_legacy)],
                       env=env, check=True, timeout=120)
        for index in range(128):
            name = f'case-{index:03d}.xlsx'
            if (old_legacy / name).read_bytes() != (new_legacy / name).read_bytes():
                raise ValueError('Pinned old public overload differs: ' + name)
        print(json.dumps({'baseline': BASELINE, 'old_grid_sha256': hashlib.sha256((folder / 'OfficeSheetTextGrid.kt').read_bytes()).hexdigest(),
                          'matched_inputs': 64, 'byte_identical_xlsx': 64, 'legacy_overload_cases': 128,
                          'scope': 'Finite exact-byte compatibility, not all inputs or Office rendering.'}))


if __name__ == '__main__':
    main()
