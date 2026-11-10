#!/usr/bin/env python3
"""One command for the R75 fraction-format slice: compile, observe, verify.

(1) compile the ooxml module plus the R75 sheet probe with the pinned host kotlinc and -Werror;
(2) run the probe, which renders every (value, format) pair through the production renderer;
(3) compare every observation with the independent exhaustive oracle.
Host-only: no Excel, LibreOffice or Android execution is involved.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
ARTIFACTS = ROOT / 'testing/build/r75fractions/artifacts'


def kotlin_sources() -> list[str]:
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r75writer/kotlin/dev/fusion/testing/R75SheetFractionChecks.kt')
    return archive + ooxml + [probe]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first.')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r75-fraction-') as temporary:
        jar = Path(temporary) / 'r75frac.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R75SheetFractionChecks', str(args.artifacts)],
                       env=env, check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r75_sheet_fractions.py'), str(args.artifacts)],
                   check=True, env=env)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
