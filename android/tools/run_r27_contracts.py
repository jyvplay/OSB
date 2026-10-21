#!/usr/bin/env python3
"""Separately compile the R27 production kernels and execute the consumer checks on real fixtures."""
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
    'launcher': ['feature/launcher/src/main/kotlin/dev/fusion/launcher/Tabletop.kt',
                 'feature/launcher/src/main/kotlin/dev/fusion/launcher/TabletopArrange.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/ServiceLink.kt',
              'feature/media/src/main/kotlin/dev/fusion/media/PlaylistParser.kt',
              'feature/media/src/main/kotlin/dev/fusion/media/PodcastFeed.kt',
              'feature/media/src/main/kotlin/dev/fusion/media/StreamPolicy.kt'],
    'maps': ['feature/maps/src/main/kotlin/dev/fusion/maps/GpxTrack.kt',
             'feature/maps/src/main/kotlin/dev/fusion/maps/Gazetteer.kt'],
    'browser': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/SafeNavigation.kt',
                'feature/browser/src/main/kotlin/dev/fusion/browser/model/ContentBlocker.kt'],
    'documents': ['core/documents/src/main/kotlin/dev/fusion/documents/MarkdownDoc.kt'],
    'security': ['core/security/src/main/kotlin/dev/fusion/security/UsbPolicy.kt',
                 'core/security/src/main/kotlin/dev/fusion/security/UsbInventory.kt',
                 'core/security/src/main/kotlin/dev/fusion/security/FirewallPolicy.kt'],
    'designsystem': ['core/settings/src/main/kotlin/dev/fusion/settings/Appearance.kt',
                     'core/designsystem/src/main/kotlin/dev/fusion/designsystem/Contrast.kt',
                     'core/designsystem/src/main/kotlin/dev/fusion/designsystem/DesktopMetrics.kt',
                     'core/designsystem/src/main/kotlin/dev/fusion/designsystem/ProfessionalPalettes.kt',
                     'core/designsystem/src/main/kotlin/dev/fusion/designsystem/BeautyStandard.kt',
                     'core/designsystem/src/main/kotlin/dev/fusion/designsystem/TypeScale.kt'],
    'automation': ['feature/automation/src/main/kotlin/dev/fusion/automation/CronSpec.kt'],
}
CHECKS = 'testing/src/r27/kotlin/dev/fusion/testing/R27Checks.kt'
FIXTURES = 'tools/fixtures/r27'

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
    out = ROOT / 'testing/build/r27'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').unlink(missing_ok=True)
    commands = []
    start = time.monotonic()

    def run(args, **kw):
        commands.append(args)
        return subprocess.run(args, check=True, env=env, **kw)

    jars = []
    for name, sources in GROUPS.items():
        jar = out / f'r27-{name}.jar'
        run([compiler, '-jvm-target', '17', '-module-name', f'r27_{name}',
             *(str(ROOT / s) for s in sources), '-d', str(jar)])
        jars.append(jar)
    checks_jar = out / 'r27-checks.jar'
    run([compiler, '-jvm-target', '17', '-module-name', 'r27_consumer', '-classpath',
         os.pathsep.join(str(j) for j in jars), str(ROOT / CHECKS), '-include-runtime', '-d', str(checks_jar)])
    native = run([java, '-cp', os.pathsep.join([str(checks_jar), *[str(j) for j in jars]]),
                  'dev.fusion.testing.R27Checks', str(ROOT / FIXTURES)], capture_output=True, text=True)
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
