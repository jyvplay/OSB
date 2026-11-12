#!/usr/bin/env python3
"""One command for the R76 Writer section-layout slice: compile, observe, verify.

(1) compile :format:ooxml plus the R76 writer probe with the pinned host kotlinc and -Werror;
(2) run the probe, which writes real .docx packages with page geometry, headers/footers and a
    legacy package without either;
(3) verify every package with python-docx and raw OPC inspection.

Host-only: no Word, LibreOffice or Android execution is involved.
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
ARTIFACTS = ROOT / 'testing/build/r76writer/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r76writer/kotlin/dev/fusion/testing/R76WriterLayoutChecks.kt')
    return archive + ooxml + [probe]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r76-layout-') as temporary:
        jar = Path(temporary) / 'r76layout.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R76WriterLayoutChecks', str(args.artifacts)],
                       env=env, check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r76_writer_layout.py'),
                    str(args.artifacts), '--require-python-docx'], check=True, env=env)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
