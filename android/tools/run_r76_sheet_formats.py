#!/usr/bin/env python3
"""One command for the R76 spreadsheet number/date/text format slice: compile, observe, verify.

(1) compile the ooxml module plus the R76 probe with the pinned host kotlinc and -Werror;
(2) run the probe, which renders every corpus pair through the production renderer;
(3) verify every observation twice: by re-derivation, and by replay against the independent
    Apache-2.0 `ssf` implementation with an explicit adjudication table for the declared
    differences.

Host-only: no Excel, LibreOffice or Android execution is involved. The independent reference is
installed outside the repository:

    pip install --target "$HOME/.cache/fusion-r76-ssf" ssf==0.2.2 babel==2.9.1
    PYTHONPATH="$HOME/.cache/fusion-r76-ssf" python3 tools/run_r76_sheet_formats.py
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
ARTIFACTS = ROOT / 'testing/build/r76formats/artifacts'


def kotlin_sources() -> list[str]:
    # ooxml depends on :format:archive (StrictZip); compile both, exactly like the Gradle module.
    archive = sorted(str(p) for p in (ROOT / 'format/archive/src/main/kotlin').rglob('*.kt'))
    ooxml = sorted(str(p) for p in (ROOT / 'format/ooxml/src/main/kotlin').rglob('*.kt'))
    probe = str(ROOT / 'testing/src/r76sheet/kotlin/dev/fusion/testing/R76SheetFormatChecks.kt')
    return archive + ooxml + [probe]


def reference_available() -> bool:
    try:
        import ssf  # noqa: F401
    except Exception:
        return False
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    parser.add_argument('--no-reference', action='store_true',
                        help='verify by re-derivation only and downgrade the claim')
    args = parser.parse_args(argv)
    env = bootstrap_host_tools.environment(CACHE)
    if not Path(env['KOTLINC']).is_file():
        raise SystemExit('Run tools/bootstrap_host_tools.py install first (npm-pinned Kotlin compiler).')
    if args.artifacts.exists():
        shutil.rmtree(args.artifacts)
    args.artifacts.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix='r76-formats-') as temporary:
        jar = Path(temporary) / 'r76formats.jar'
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                        *kotlin_sources(), '-d', str(jar)], env=env, check=True)
        java = str(Path(env['JAVA_HOME']) / 'bin/java')
        subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar),
                        'dev.fusion.testing.R76SheetFormatChecks', str(args.artifacts)],
                       env=env, check=True)
    command = [sys.executable, str(ROOT / 'tools/check_r76_sheet_formats.py'), str(args.artifacts)]
    require_reference = not args.no_reference
    if require_reference and not reference_available():
        print('The independent ssf reference is not installed. Install it with:\n'
              '  pip install --target "$HOME/.cache/fusion-r76-ssf" ssf==0.2.2 babel==2.9.1\n'
              'and re-run with PYTHONPATH="$HOME/.cache/fusion-r76-ssf". '
              'Use --no-reference to verify by re-derivation only.', file=sys.stderr)
        return 2
    if require_reference:
        command.append('--require-reference')
    subprocess.run(command, check=True, env={**os.environ, **env})
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
