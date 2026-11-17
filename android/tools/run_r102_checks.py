#!/usr/bin/env python3
"""R102 office runner: compile the ooxml module plus the R102 probe, then run the independent checks.

R102 completes the `<dxf>` differential format — the paint payload every painting `cfRule` points at — from
"bold/italic/strike/underline, a font colour and a solid fill" to everything `CT_Dxf` can hold short of a gradient:
font family and size, a number format, a patterned fill, a bordered box, alignment and protection. The evaluator
carries those fields to the caller unchanged, merged field-by-field across the rules that fired.

Steps, in order, all required unless the tooling is missing (in which case the runner prints which lane was
skipped and why, rather than reporting a pass):

1. compile `:format/archive` + `:format:ooxml` + `testing/src/r102/.../R102Checks.kt` with the pinned host kotlinc
   and `-Werror`;
2. run the probe, which writes `r102.xlsx` (six formats, one per section group plus a kitchen sink),
   `r102-foreign.xlsx` (five dxfs this model must refuse or partially hold) and `formats.tsv`, `merge.tsv`,
   `detail.tsv`, `summary.txt` into `testing/build/r102/artifacts`;
3. `tools/check_r102_dxf.py` — the raw OPC re-encoding, openpyxl 3.1.5's own dxf model, a Python re-derivation of
   the merge, and the foreign-package facts each refusal depends on.

Host JVM only. No Excel, LibreOffice, browser or Android device is involved: no cell was painted, so every claim
here is about what a dxf *says*, never about what a screen shows.
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
ARTIFACTS = ROOT / 'testing/build/r102/artifacts'


def kotlin_sources() -> list[str]:
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r102/kotlin/dev/fusion/testing/R102Checks.kt')
    return archive + ooxml + [probe]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    parser.add_argument('--skip-lanes', action='store_true', help='only write the artifacts')
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r102-tail-') as temporary:
        jar = Path(temporary) / 'r102dxf.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R102Checks', str(args.artifacts)], env=env, check=True)
    if args.skip_lanes:
        return 0
    verify = dict(env)
    if READERS.is_dir():
        existing = verify.get('PYTHONPATH')
        verify['PYTHONPATH'] = (existing + os.pathsep if existing else '') + str(READERS)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r102_dxf.py'),
                    str(args.artifacts), '--require-openpyxl'], env=verify, check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
