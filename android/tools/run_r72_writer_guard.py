#!/usr/bin/env python3
"""Pinned-versus-candidate plain Writer guard: byte-identical corpus plus small-heap probe.

Pinned sources come from `main` (`aacae23`); the candidate is the working tree.
Host-only, finite: this is not an Android memory or device measurement.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

BASE = 'aacae2396ca3af6363e973999a2115132efa272c'
NAMES = ('BoundedXml', 'ColLetters', 'SheetRange', 'CellErr', 'FormulaEval', 'NumberFormatRender', 'NumFmtSections', 'FormulaXlsx',
         'DocxNumbering', 'DocxPackage', 'OfficeSheetDraft', 'PlainWriterDocx', 'StructuredWriterDocx')
PROBE = ROOT / 'testing/src/r72writer/kotlin/dev/fusion/testing/R72WriterResourceProbe.kt'
SIZE = 25 * 1024 * 1024
# The same already-built String fits here; the unguarded writer's encode does not.
HEAP = '80m'


def compile_probe(env, directory, sources, label):
    jar = directory / f'{label}.jar'
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), str(PROBE), '-d', str(jar)], env=env, check=True)
    return jar


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    with tempfile.TemporaryDirectory(prefix='r72-writer-') as temporary:
        folder = Path(temporary)
        pinned_sources = []
        for name in NAMES:
            rel = f'android/format/ooxml/src/main/kotlin/dev/fusion/ooxml/{name}.kt'
            target = folder / f'pinned-{name}.kt'
            if subprocess.run(['git', 'cat-file', '-e', f'{BASE}:{rel}'], cwd=REPO,
                              capture_output=True).returncode != 0:
                # Files added after the pinned commit cannot exist in it.
                continue
            target.write_bytes(subprocess.check_output(['git', 'show', f'{BASE}:{rel}'], cwd=REPO))
            pinned_sources.append(target)
        candidate_sources = [ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml' / (n + '.kt') for n in NAMES]
        jars = {'pinned': compile_probe(env, folder, pinned_sources, 'pinned'),
                'candidate': compile_probe(env, folder, candidate_sources, 'candidate')}
        for label, jar in jars.items():
            run = subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                                  'dev.fusion.testing.R72WriterResourceProbe', 'boundaries'],
                                 env=env, capture_output=True, text=True, timeout=600)
            print(f'R72 {label} boundaries exit={run.returncode}: {run.stdout.strip()}')
            if run.returncode != 0:
                print(run.stderr.strip(), file=sys.stderr)
                raise SystemExit(f'{label} boundary probe failed')
        # Retained Writer corpora must stay byte-identical between pinned and candidate.
        artifacts = {}
        for label, jar in jars.items():
            target = folder / f'artifacts-{label}'
            target.mkdir()
            writer_check = ROOT / 'testing/src/r59writer/kotlin/dev/fusion/testing/R59WriterChecks.kt'
            jar_with_corpus = folder / f'{label}-corpus.jar'
            subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                            *map(str, pinned_sources if label == 'pinned' else candidate_sources),
                            str(writer_check), '-d', str(jar_with_corpus)], env=env, check=True)
            subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar_with_corpus),
                            'dev.fusion.testing.R59WriterChecks', str(target)], env=env, check=True, timeout=600)
            artifacts[label] = target
        pinned_files = {p.name for p in artifacts['pinned'].iterdir()}
        candidate_files = {p.name for p in artifacts['candidate'].iterdir()}
        if pinned_files != candidate_files:
            raise SystemExit('R72 retained Writer artifact names differ')
        for name in sorted(pinned_files):
            if (artifacts['pinned'] / name).read_bytes() != (artifacts['candidate'] / name).read_bytes():
                raise SystemExit('R72 retained Writer output changed: ' + name)
        print(f'R72 retained Writer corpus: {len(pinned_files)} artifacts byte-identical')
        # Constrained-heap probe on the same already-built String in separate JVMs.
        observations = {}
        for label, jar in jars.items():
            run = subprocess.run([java, f'-Xmx{HEAP}', '-Dfile.encoding=UTF-8', '-cp', str(jar),
                                  'dev.fusion.testing.R72WriterResourceProbe', 'oversized', str(SIZE)],
                                 env=env, capture_output=True, text=True, timeout=600)
            observations[label] = run
            summary = (run.stdout.strip() or run.stderr.strip().splitlines()[-1:] or [''])[-1]
            print(f'R72 {label} -Xmx{HEAP} {SIZE} chars exit={run.returncode}: {summary}')
        candidate = observations['candidate']
        if candidate.returncode != 0 or 'REFUSED phase=plain-writer' not in candidate.stdout:
            raise SystemExit('Candidate did not explicitly refuse oversized input in the bounded heap')
        pinned = observations['pinned']
        if pinned.returncode not in (0, 12, 13):
            raise SystemExit('Pinned probe neither refused nor hit a classified OOM')
        if pinned.returncode == 12:
            print('Pinned build hit a classified writer OOM; candidate refused the same input.')
        elif pinned.returncode == 13:
            print('INCONCLUSIVE: pinned probe could not allocate its own input under this heap; no counterexample claimed.')
            raise SystemExit('Probe heap too small to allocate the input String; rerun with a larger -Xmx')
        else:
            print('NOTE: pinned build did not fail on this host/heap; no counterexample claimed.')
    print('R72 Writer guard comparators passed. Host-only finite evidence; no Android memory claim.')


if __name__ == '__main__':
    main()
