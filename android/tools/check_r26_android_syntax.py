#!/usr/bin/env python3
"""Parse-gate for R26 authored Android sources using the real kotlinc frontend.

Without android.jar/Compose, resolution errors are EXPECTED. This gate fails only on
syntax-class diagnostics (which precede resolution), proving the files parse. It is NOT
a type check and NOT a compile claim: full compilation requires Studio/CI with the SDK.
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    'app/src/main/java/dev/fusion/shell/AdaptiveDock.kt',
    'feature/media/src/main/java/dev/fusion/media/StreamPlayerActivity.kt',
    'feature/maps/src/main/java/dev/fusion/maps/VectorAtlasBridge.kt',
    'feature/browser/src/main/java/dev/fusion/browser/OrbotHandoff.kt',
]
SYNTAX = re.compile(r'error: (expecting|syntax|mismatched input|unclosed|unresolved reference \S*$)')

def main():
    import argparse
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    out = ROOT / 'testing/build/r26'
    out.mkdir(parents=True, exist_ok=True)
    report = {}
    failed = False
    for rel in FILES:
        proc = subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-d', str(out / 'parse-check.jar'),
                               str(ROOT / rel)], capture_output=True, text=True, env=env)
        errors = [l for l in (proc.stdout + proc.stderr).splitlines() if 'error:' in l]
        syntax = [l for l in errors if SYNTAX.search(l) and 'unresolved reference' not in l]
        # 'unresolved reference' is resolution-class (missing SDK), never syntax.
        report[rel] = {'diagnostics': len(errors), 'syntax_errors': syntax,
                       'all_resolution_class': not syntax and len(errors) > 0}
        if syntax:
            failed = True
            print(f'SYNTAX FAIL {rel}:')
            print('\n'.join(syntax))
        else:
            print(f'PARSE OK {rel}: {len(errors)} resolution-only diagnostics (expected without android.jar).')
    report['scope'] = 'kotlinc parse only; NOT a type check, NOT compilation, NOT execution.'
    (out / 'android-syntax.json').write_text(json.dumps(report, indent=2) + '\n')
    if failed:
        raise SystemExit(1)

if __name__ == '__main__':
    main()
