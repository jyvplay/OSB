#!/usr/bin/env python3
"""Separately compile the R29 production kernels and execute the consumer checks on real fixtures."""
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
    'beam': ['feature/beam/src/main/kotlin/dev/fusion/beam/ShareManifest.kt'],
    'storage': ['core/storage/src/main/kotlin/dev/fusion/storage/QuotaLedger.kt'],
    'alerts': ['feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertThrottle.kt'],
    'files': ['feature/files/src/main/kotlin/dev/fusion/filesui/FileSort.kt'],
    'commands': ['core/commands/src/main/kotlin/dev/fusion/commands/AliasTable.kt'],
    'a11y': ['feature/accessibility/src/main/kotlin/dev/fusion/a11y/ContrastRatio.kt'],
    'settings': ['core/settings/src/main/kotlin/dev/fusion/settings/LocaleMatch.kt'],
    'backup': ['feature/backup/src/main/kotlin/dev/fusion/backup/RetentionPlan.kt'],
    'dlna': ['cast/dlna/src/main/java/dev/fusion/cast/dlna/UpnpParsing.kt',
             'cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLite.kt'],
    'ooxml': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/CsvTable.kt',
              'format/ooxml/src/main/kotlin/dev/fusion/ooxml/LiteralWorkbook.kt',
              'format/ooxml/src/main/kotlin/dev/fusion/ooxml/CsvDialect.kt'],
    'usb': ['app/src/main/java/dev/fusion/shell/UsbAllowlist.kt',
            'testing/src/r29/kotlin/android/content/AndroidHostStubs.kt'],
}
CHECKS = 'testing/src/r29/kotlin/dev/fusion/testing/R29Checks.kt'
FIXTURES = 'tools/fixtures/r29'

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
    out = ROOT / 'testing/build/r29'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').unlink(missing_ok=True)
    commands = []
    start = time.monotonic()

    def run(args, **kw):
        commands.append(args)
        return subprocess.run(args, check=True, env=env, **kw)

    jars = []
    for name, sources in GROUPS.items():
        jar = out / f'r29-{name}.jar'
        run([compiler, '-jvm-target', '17', '-module-name', f'r29_{name}',
             *(str(ROOT / s) for s in sources), '-d', str(jar)])
        jars.append(jar)
    checks_jar = out / 'r29-checks.jar'
    run([compiler, '-jvm-target', '17', '-module-name', 'r29_consumer', '-classpath',
         os.pathsep.join(str(j) for j in jars), str(ROOT / CHECKS), '-include-runtime', '-d', str(checks_jar)])
    native = run([java, '-cp', os.pathsep.join([str(checks_jar), *[str(j) for j in jars]]),
                  'dev.fusion.testing.R29Checks', str(ROOT / FIXTURES)], capture_output=True, text=True)
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
