#!/usr/bin/env python3
"""Pinned-versus-candidate execution of the real Office readers on R72 ZIP fixtures.

Pinned sources come from `main` (`aacae23`), compiled in a fresh JVM with `-Werror`;
candidate sources are the working tree. Host-only comparison, not Android.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

BASE = 'aacae2396ca3af6363e973999a2115132efa272c'
SOURCES = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'NumberFormatRender', 'NumFmtSections', 'FormulaXlsx',
           'DocxNumbering', 'DocxPackage')
PROBE = 'testing/src/r72zip/kotlin/dev/fusion/testing/R72OfficeZipProbe.kt'
FIXTURES = 'tools/r72_office_zip_cases.py'
REFERENCE = 'tools/check_r72_office_zip_reference.py'


def run(command, env, cwd=None, **options):
    return subprocess.run([str(part) for part in command], env=env, cwd=cwd, check=True,
                          timeout=900, **options)


def compile_and_seed(env, directory, sources):
    jar = directory / 'probe.jar'
    run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime', *sources,
         ROOT / PROBE, '-d', jar], env)
    run([Path(env['JAVA_HOME']) / 'bin/java', '-Dfile.encoding=UTF-8', '-cp', jar,
         'dev.fusion.testing.R72OfficeZipProbe', 'seed', directory], env)
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'testing/build/r72zip')
    args = parser.parse_args()
    output = args.output
    if output.exists():
        raise SystemExit('Refusing to overwrite existing evidence: ' + str(output))
    output.mkdir(parents=True)
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    env['PYTHONPATH'] = str(Path.home() / '.cache/fusion-r72-readers') + ':' + env.get('PYTHONPATH', '')
    java = Path(env['JAVA_HOME']) / 'bin/java'
    with tempfile.TemporaryDirectory(prefix='r72-zip-pinned-') as temporary:
        pinned_dir = Path(temporary)
        sources = []
        for name in SOURCES:
            rel = f'android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/{name}.kt'
            target = pinned_dir / f'{name}.kt'
            if subprocess.run(['git', 'cat-file', '-e', f'{BASE}:{rel}'], cwd=REPO,
                              capture_output=True).returncode != 0:
                # Added after the pinned commit; the older sources do not reference it.
                continue
            target.write_bytes(subprocess.check_output(['git', 'show', f'{BASE}:{rel}'], cwd=REPO))
            sources.append(target)
        pinned_jar = compile_and_seed(env, pinned_dir, sources)
        fixtures = output / 'fixtures'
        fixtures.mkdir()
        for seed in ('seed.docx', 'seed.xlsx'):
            (fixtures / seed).write_bytes((pinned_dir / seed).read_bytes())
        run([sys.executable, ROOT / FIXTURES, fixtures], env)
        pinned, current = output / 'pinned', output / 'candidate'
        for directory in (pinned, current):
            directory.mkdir()
            for path in fixtures.iterdir():
                (directory / path.name).write_bytes(path.read_bytes())
        run([java, '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp', pinned_jar,
             'dev.fusion.testing.R72OfficeZipProbe', 'read', pinned], env)
        run([sys.executable, ROOT / REFERENCE, pinned, '--baseline'], env)
        current_sources = [ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml' / (n + '.kt') for n in SOURCES]
        current_jar = compile_and_seed(env, current, current_sources)
        run([java, '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp', current_jar,
             'dev.fusion.testing.R72OfficeZipProbe', 'read', current], env)
        run([sys.executable, ROOT / REFERENCE, current], env)
        for directory in (pinned, current):
            for name in ('seed.docx', 'seed.xlsx'):
                if (directory / name).read_bytes() != (fixtures / name).read_bytes():
                    raise SystemExit(f'Writer output changed: {directory.name}/{name}')
        print('Pinned and candidate seeds byte-identical.')
    print('R72 Office ZIP comparators passed. No Android, desktop Office or universal safety claim.')


if __name__ == '__main__':
    main()
