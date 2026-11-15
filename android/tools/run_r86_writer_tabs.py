#!/usr/bin/env python3
"""One command for the R86 Writer tab-stop and page-break slice: compile, observe, verify.

(1) compile :format:ooxml plus the R86 writer probe with the pinned host kotlinc and -Werror;
(2) run the probe, which writes real .docx packages whose headings and paragraphs declare `w:tabs`,
    `w:pageBreakBefore` and `w:widowControl`, plus one package that declares none of them;
(3) verify every package with python-docx and raw OPC inspection.

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
ARTIFACTS = ROOT / 'testing/build/r86writer/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r86writer/kotlin/dev/fusion/testing/R86WriterTabsChecks.kt')
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
    with tempfile.TemporaryDirectory(prefix='r86-tabs-') as temporary:
        jar = Path(temporary) / 'r86tabs.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R86WriterTabsChecks', str(args.artifacts)],
                       env=env, check=True)
    verify = dict(env)
    if READERS.is_dir():
        # python-docx/openpyxl may live only in a disposable reader cache. Append it so the verifier is
        # self-sufficient, and keep any already-installed interpreter ahead of it on the path.
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r86_writer_tabs.py'),
                    str(args.artifacts), '--require-python-docx'], check=True, env=verify)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
