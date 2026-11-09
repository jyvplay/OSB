#!/usr/bin/env python3
"""Reproduce the production import-budget experiment end to end on the host; not Android."""
import argparse
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

PROBE = ROOT / 'testing/src/r72zip/kotlin/dev/fusion/testing/R72OoxmlZipProbe.kt'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'testing/build/r72ooxml')
    args = parser.parse_args()
    directory = args.output
    if directory.exists():
        raise SystemExit('Refusing to overwrite existing evidence: ' + str(directory))
    directory.mkdir(parents=True)
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    source = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/Ooxml.kt'
    jar = directory / 'probe.jar'
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    str(source), str(PROBE), '-d', str(jar)], env=env, check=True)
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                    'dev.fusion.testing.R72OoxmlZipProbe', 'seed', str(directory)], env=env, check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/r72_ooxml_zip_cases.py'), 'build', str(directory)],
                   env=env, check=True)
    subprocess.run([java, '-Xmx512m', '-Dfile.encoding=UTF-8', '-cp', str(jar),
                    'dev.fusion.testing.R72OoxmlZipProbe', 'read', str(directory)], env=env, check=True,
                   timeout=600)
    subprocess.run([sys.executable, str(ROOT / 'tools/r72_ooxml_zip_cases.py'), 'verify', str(directory)],
                   env=env, check=True)
    print('R72 import-budget comparators passed. No Android/provider/Office-rendering claim.')


if __name__ == '__main__':
    main()
