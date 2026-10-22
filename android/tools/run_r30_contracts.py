#!/usr/bin/env python3
"""Separately compile the R30 production kernels and execute the consumer checks on real fixtures."""
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
    'airplay': ['cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspMessage.kt'],
    'capture': ['feature/capture/src/main/kotlin/dev/fusion/capture/Timecode.kt'],
    'continuity': ['feature/continuity/src/main/kotlin/dev/fusion/continuity/CalendarExport.kt',
                   'feature/continuity/src/main/kotlin/dev/fusion/continuity/VEventParse.kt'],
    'together': ['feature/together/src/main/kotlin/dev/fusion/together/LamportClock.kt'],
    'image': ['format/image/src/main/kotlin/dev/fusion/format/image/GifInfo.kt'],
    'settings': ['feature/settings/src/main/kotlin/dev/fusion/settingsui/SettingsPatch.kt',
                 'feature/settings/src/main/kotlin/dev/fusion/settingsui/PatchScope.kt'],
    'archive': ['format/archive/src/main/kotlin/dev/fusion/archive/TarHeader.kt'],
    'lens': ['feature/lens/src/main/kotlin/dev/fusion/lens/ZoomLadder.kt'],
    'messages': ['feature/messages/src/main/kotlin/dev/fusion/messages/SmsSegments.kt'],
    'model': ['core/model/src/main/kotlin/dev/fusion/model/Models.kt',
              'core/model/src/main/kotlin/dev/fusion/model/DensitySelect.kt'],
}
CHECKS = 'testing/src/r30/kotlin/dev/fusion/testing/R30Checks.kt'
FIXTURES = 'tools/fixtures/r30'

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
    out = ROOT / 'testing/build/r30'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').unlink(missing_ok=True)
    commands = []
    start = time.monotonic()

    def run(args, **kw):
        commands.append(args)
        return subprocess.run(args, check=True, env=env, **kw)

    jars = []
    for name, sources in GROUPS.items():
        jar = out / f'r30-{name}.jar'
        run([compiler, '-jvm-target', '17', '-module-name', f'r30_{name}',
             *(str(ROOT / s) for s in sources), '-d', str(jar)])
        jars.append(jar)
    checks_jar = out / 'r30-checks.jar'
    run([compiler, '-jvm-target', '17', '-module-name', 'r30_consumer', '-classpath',
         os.pathsep.join(str(j) for j in jars), str(ROOT / CHECKS), '-include-runtime', '-d', str(checks_jar)])
    native = run([java, '-cp', os.pathsep.join([str(checks_jar), *[str(j) for j in jars]]),
                  'dev.fusion.testing.R30Checks', str(ROOT / FIXTURES)], capture_output=True, text=True)
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
