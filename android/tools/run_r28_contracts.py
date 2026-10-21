#!/usr/bin/env python3
"""Separately compile the R28 production kernels and execute the consumer checks on real fixtures."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
GROUPS = {
    'files': ['core/files/src/main/kotlin/dev/fusion/files/TrashLedger.kt'],
    'windowing': ['core/windowing/src/main/kotlin/dev/fusion/windowing/SnapZones.kt'],
    'find': ['feature/find/src/main/kotlin/dev/fusion/find/FrecencyLedger.kt'],
    'maps': ['feature/maps/src/main/kotlin/dev/fusion/maps/TileMath.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/ChapterMarks.kt'],
    'security': ['core/security/src/main/kotlin/dev/fusion/security/ConsentLedger.kt'],
    'browser': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/CookieJar.kt'],
    'automation': ['feature/automation/src/main/kotlin/dev/fusion/automation/Recurrence.kt'],
    'notes': ['feature/notes/src/main/kotlin/dev/fusion/notes/FrontMatter.kt'],
    'terminal': ['feature/terminal/src/main/kotlin/dev/fusion/terminal/Scrollback.kt'],
}
CHECKS = 'testing/src/r28/kotlin/dev/fusion/testing/R28Checks.kt'
FIXTURES = 'tools/fixtures/r28'

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=None)
    a = p.parse_args()
    env = os.environ.copy()
    if a.tools:
        sys.path.insert(0, str(ROOT / 'tools'))
        import bootstrap_host_tools
        env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    compiler = env.get('KOTLINC', 'kotlinc')
    java = str(Path(env['JAVA_HOME']) / 'bin/java') if env.get('JAVA_HOME') else 'java'
    out = ROOT / 'testing/build/r28'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').unlink(missing_ok=True)
    commands = []
    start = time.monotonic()

    def run(args, **kw):
        commands.append(args)
        return subprocess.run(args, check=True, env=env, **kw)

    jars = []
    for name, sources in GROUPS.items():
        jar = out / f'r28-{name}.jar'
        run([compiler, '-jvm-target', '17', '-module-name', f'r28_{name}',
             *(str(ROOT / s) for s in sources), '-d', str(jar)])
        jars.append(jar)
    checks_jar = out / 'r28-checks.jar'
    run([compiler, '-jvm-target', '17', '-module-name', 'r28_consumer', '-classpath',
         os.pathsep.join(str(j) for j in jars), str(ROOT / CHECKS), '-include-runtime', '-d', str(checks_jar)])
    native = run([java, '-cp', os.pathsep.join([str(checks_jar), *[str(j) for j in jars]]),
                  'dev.fusion.testing.R28Checks', str(ROOT / FIXTURES)], capture_output=True, text=True)
    (out / 'result.txt').write_text(native.stdout + native.stderr)
    print(native.stdout, end='')
    files = [s for group in GROUPS.values() for s in group] + [CHECKS] + sorted(
        str(f.relative_to(ROOT)) for f in (ROOT / FIXTURES).iterdir())
    report = {'elapsed_seconds': round(time.monotonic() - start, 3), 'exit_code': 0,
              'commands': commands, 'output': native.stdout,
              'sha256': {f: hashlib.sha256((ROOT / f).read_bytes()).hexdigest() for f in files},
              'scope': 'Actual separate host compiler/consumer/fixtures only; NOT Android execution, device, service, anonymity or acceptance evidence.'}
    (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')

if __name__ == '__main__':
    main()
