#!/usr/bin/env python3
"""Pinned main versus candidate for the four core readers' ZIP byte budgets.

Compiles the whole `format/archive` + `format/ooxml` sources twice so both
builds see identical input, then compares per-fixture outcomes. Host-only.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools
import r72_core_zip_cases as cases

BASE = 'aacae2396ca3af6363e973999a2115132efa272c'
PACKAGES = ('format/archive/src/main/kotlin/dev/fusion/archive',
            'format/ooxml/src/main/kotlin/dev/fusion/ooxml')
PROBE = ROOT / 'testing/src/r72core/kotlin/dev/fusion/testing/R72CoreZipProbe.kt'


def compile_sources(env, directory, label, sources):
    jar = directory / f'{label}.jar'
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *sources, str(PROBE), '-d', str(jar)], env=env, check=True)
    return jar


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    with tempfile.TemporaryDirectory(prefix='r72-core-') as temporary:
        directory = Path(temporary)
        pinned = directory / 'pinned'
        pinned.mkdir()
        for package in PACKAGES:
            for path in sorted((ROOT / package).glob('*.kt')):
                relative = path.relative_to(REPO).as_posix()
                exists = subprocess.run(['git', 'cat-file', '-e', f'{BASE}:{relative}'], cwd=REPO)
                if exists.returncode != 0:
                    # File added after the pinned baseline; only the candidate compiles it.
                    continue
                (pinned / path.name).write_bytes(subprocess.check_output(['git', 'show', f'{BASE}:{relative}'], cwd=REPO))
        pinned_sources = []
        candidate_sources = []
        for package in PACKAGES:
            for path in sorted((ROOT / package).glob('*.kt')):
                candidate_sources.append(str(path))
                if (pinned / path.name).exists():
                    pinned_sources.append(str(pinned / path.name))
        jars = {'pinned': compile_sources(env, directory, 'pinned', pinned_sources),
                'candidate': compile_sources(env, directory, 'candidate', candidate_sources)}
        evidence = directory / 'evidence'
        evidence.mkdir()
        cases.build(evidence)
        rows = cases.read_tsv(evidence / 'cases.tsv', cases.HEADER)
        outcomes = {}
        for label, jar in jars.items():
            target = evidence / f'{label}.txt'
            subprocess.run([java, '-Xmx512m', '-cp', str(jar),
                            'dev.fusion.testing.R72CoreZipProbe', 'read', str(evidence)],
                           env=env, check=True, timeout=900)
            produced = evidence / 'candidate.txt'
            produced.rename(target)
            outcomes[label] = cases.observations(target)
        pinned_bad = cases.compare(rows, outcomes['pinned'], 'pinned')
        if pinned_bad:
            raise SystemExit('Pinned build did not reproduce its recorded outcomes: ' + ', '.join(pinned_bad))
        expected_gaps = sorted(row['id'] for row in rows if row['pinned'] != row['expected'])
        if not expected_gaps:
            raise SystemExit('No counterexample declared between pinned and candidate')
        mismatches = cases.compare(rows, outcomes['candidate'])
        if mismatches:
            raise SystemExit('Candidate mismatches: ' + ', '.join(mismatches))
        print(f'R72 core readers: {len(rows)} archives; pinned reproduced {len(expected_gaps)} '
              f'counterexamples ({", ".join(expected_gaps)}); candidate 0 mismatches')
    print('R72 core-reader comparators passed. Host-only; no Android or Office rendering claim.')


if __name__ == '__main__':
    main()
