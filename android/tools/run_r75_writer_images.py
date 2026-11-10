#!/usr/bin/env python3
"""One command for the R75 Writer image slice: compile, run, verify, decode.

Steps: (1) compile the ooxml module plus the R75 check with the pinned host kotlinc and -Werror;
(2) run the JVM check, which writes real DOCX packages; (3) verify those packages with the
independent python reader; (4) when ImageMagick is available, decode the embedded media with it,
which is a third, non-Python decoder. Nothing here is Word rendering or Android evidence.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools  # noqa: E402

CACHE = Path.home() / '.cache/fusion-host-r23'
ARTIFACTS = ROOT / 'testing/build/r75writer/artifacts'


def kotlin_sources() -> list[str]:
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    check = str(ROOT / 'testing/src/r75writer/kotlin/dev/fusion/testing/R75WriterImageChecks.kt')
    return archive + ooxml + [check]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm distribution of kotlinc).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r75-compile-') as temporary:
        jar = Path(temporary) / 'r75.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R75WriterImageChecks', str(args.artifacts)],
                       env=env, check=True)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r75_writer_images.py'), str(args.artifacts)],
                   check=True, env=env)
    identify = shutil.which('identify')
    if identify is None:
        print('ImageMagick identify unavailable: third-decoder confirmation NOT run.')
    else:
        for package, expected in (('images.docx', ['PNG 4x3', 'PNG 8x2']), ('mixed.docx', ['PNG 4x3', 'JPEG 16x16'])):
            with zipfile.ZipFile(args.artifacts / package) as archive:
                for name in sorted(n for n in archive.namelist() if n.startswith('word/media/')):
                    suffix = Path(name).suffix
                    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as handle:
                        handle.write(archive.read(name))
                        temp = Path(handle.name)
                    try:
                        out = subprocess.run([identify, str(temp)], capture_output=True, text=True, check=True)
                        print('identify', package, name, '::', out.stdout.strip().split(' ', 2)[-1])
                    finally:
                        temp.unlink()
        print('ImageMagick decoded the embedded media; expected shapes were', ', '.join(
            shape for _, shapes in (('images.docx', ['PNG 4x3', 'PNG 8x2']),) for shape in shapes))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
