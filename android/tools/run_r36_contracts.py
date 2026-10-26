#!/usr/bin/env python3
"""Compile the 12 R36 kernels as separate kotlinc-jvm groups and run R36Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'calc': ['feature/calc/src/main/kotlin/dev/fusion/calc/RpnEval.kt'],
    'files': ['feature/files/src/main/kotlin/dev/fusion/filesui/NameClash.kt'],
    'editor': ['feature/editor/src/main/java/dev/fusion/editor/WrapCol.kt'],
    'commands': ['core/commands/src/main/kotlin/dev/fusion/commands/RedirPlan.kt'],
    'cast': ['cast/castv2/src/main/kotlin/dev/fusion/castv2/CastState.kt'],
    'maps': ['feature/maps/src/main/kotlin/dev/fusion/maps/ScaleBar.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/WavHead.kt'],
    'image': ['format/image/src/main/kotlin/dev/fusion/format/image/TiffHead.kt'],
    'privacy': ['privacy/src/main/java/dev/fusion/privacy/KAnon.kt'],
    'security': ['core/security/src/main/kotlin/dev/fusion/security/AttemptWindow.kt'],
    'lens': ['feature/lens/src/main/kotlin/dev/fusion/lens/DofNear.kt'],
    'vault': ['feature/vault/src/main/kotlin/dev/fusion/vault/ShannonBits.kt'],
}
CONSUMER = ['testing/src/r36/kotlin/dev/fusion/testing/R36Checks.kt']

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    kotlinc = env['KOTLINC']
    java = env['JAVA_HOME'] + '/bin/java'
    build = ROOT / 'testing/build/r36'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r36-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r36-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R36Checks', str(ROOT / 'tools/fixtures/r36')]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R36 contracts FAILED (exit {r.returncode})')

if __name__ == '__main__':
    main()
