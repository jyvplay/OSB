#!/usr/bin/env python3
"""Compile real R24 continuation production kernels and a separate consumer; execute without Android stubs."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
GROUPS = {
    'appearance': ['core/settings/src/main/kotlin/dev/fusion/settings/Appearance.kt'],
    'design': ['core/designsystem/src/main/kotlin/dev/fusion/designsystem/' + n for n in ['Contrast.kt', 'ProfessionalPalettes.kt', 'DesktopMetrics.kt']],
    'tabletop': ['feature/launcher/src/main/kotlin/dev/fusion/launcher/' + n for n in ['Tabletop.kt', 'TabletopStore.kt']],
    'maps': ['feature/maps/src/main/kotlin/dev/fusion/maps/OfflineMap.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/ServiceLink.kt'],
    'input': ['core/security/src/main/kotlin/dev/fusion/security/InputRearm.kt'],
}


def main():
    out = ROOT / 'testing/build/r24c'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').unlink(missing_ok=True)
    (out / 'result.txt').unlink(missing_ok=True)
    compiler = os.environ.get('KOTLINC', 'kotlinc')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    sources, jars, commands = [], [], []
    start = time.monotonic()
    def run(command, **kwargs):
        commands.append(command)
        return subprocess.run(command, check=True, **kwargs)
    for name, relative in GROUPS.items():
        files = [ROOT / f for f in relative]
        jar = out / (name + '.jar')
        cmd = [compiler, '-jvm-target', '17', '-module-name', name]
        if jars:
            cmd += ['-classpath', os.pathsep.join(map(str, jars))]
        run(cmd + list(map(str, files)) + ['-d', str(jar)])
        sources += files
        jars.append(jar)
    checks = sorted((ROOT / 'testing/src/r24c/kotlin').rglob('*.kt'))
    consumer = out / 'checks.jar'
    cp = os.pathsep.join(map(str, jars))
    run([compiler, '-jvm-target', '17', '-classpath', cp, *map(str, checks), '-include-runtime', '-d', str(consumer)])
    asset = ROOT / 'feature/maps/src/main/assets/natural-earth-land-v1.txt'
    result = run([java, '-cp', str(consumer) + os.pathsep + cp, 'dev.fusion.testing.R24CChecks', str(asset)], capture_output=True, text=True)
    (out / 'result.txt').write_text(result.stdout + result.stderr)
    print(result.stdout + result.stderr, end='')
    receipt = {'scope': 'Real host compiler/files/processes and bounded policies only. NOT Android/native UI/hardware/full-spec acceptance.',
               'exit_code': result.returncode, 'elapsed_seconds': round(time.monotonic() - start, 3), 'output': result.stdout,
               'commands': commands, 'sha256': {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sources + checks + [asset]}}
    (out / 'result.json').write_text(json.dumps(receipt, indent=2) + '\n')


if __name__ == '__main__':
    main()
