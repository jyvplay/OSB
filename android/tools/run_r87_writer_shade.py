#!/usr/bin/env python3
"""One command for the R87 Writer shading slice: compile, observe, verify.

(1) compile :format:ooxml plus the R87 writer probe with the pinned host kotlinc and -Werror;
(2) run the probe, which writes real .docx packages whose paragraphs declare `w:shd` fills and whose runs
    carry tab characters, plus packages that declare neither;
(3) verify every package with raw OPC + python-docx, and — when the pinned Node renderer is installed —
    hand the same packages to docx-preview so a third-party renderer must produce the declared colours,
    text and tab spans.

Host-only: no Word, LibreOffice or Android execution is involved.
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
READERS = Path.home() / '.cache/fusion-r76-readers'
ARTIFACTS = ROOT / 'testing/build/r87writer/artifacts'
NODE_MODULES = ROOT / 'tools/r87node/node_modules'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r87writer/kotlin/dev/fusion/testing/R87WriterShadeChecks.kt')
    return archive + ooxml + [probe]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    parser.add_argument('--skip-render', action='store_true', help='skip the Node renderer lane')
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r87-shade-') as temporary:
        jar = Path(temporary) / 'r87shade.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R87WriterShadeChecks', str(args.artifacts)],
                       env=env, check=True)
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_writer_shade.py'),
                    str(args.artifacts), '--require-python-docx'], check=True, env=verify)
    if not args.skip_render:
        if NODE_MODULES.is_dir():
            subprocess.run([sys.executable, str(ROOT / 'tools/check_r87_docx_render.py'),
                            '--r87', str(args.artifacts)], check=True, env=verify)
        else:
            print('R87 renderer lane skipped: tools/r87node/node_modules is absent '
                  '(run `npm ci --prefix tools/r87node`)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
