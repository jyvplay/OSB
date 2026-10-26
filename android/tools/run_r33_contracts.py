#!/usr/bin/env python3
"""Compile the 10 R33 kernels as separate kotlinc-jvm groups and run R33Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'messages': ['feature/messages/src/main/kotlin/dev/fusion/messages/E164Format.kt'],
    'terminal': ['feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiSgr.kt'],
    'beam': ['feature/beam/src/main/kotlin/dev/fusion/beam/ChunkPlan.kt'],
    'files': ['feature/files/src/main/kotlin/dev/fusion/filesui/GlobMatch.kt'],
    'find': ['feature/find/src/main/kotlin/dev/fusion/find/FuzzyScore.kt'],
    'together': ['feature/together/src/main/kotlin/dev/fusion/together/VectorClock.kt'],
    'documents': ['core/documents/src/main/kotlin/dev/fusion/documents/TextEncoding.kt',
                  'core/documents/src/main/kotlin/dev/fusion/documents/TextDocument.kt',
                  'core/documents/src/main/kotlin/dev/fusion/documents/LineDiff.kt',
                  'core/documents/src/main/kotlin/dev/fusion/documents/DiffHunks.kt'],
    'image': ['format/image/src/main/kotlin/dev/fusion/format/image/BmpInfo.kt'],
    'archive': ['format/archive/src/main/kotlin/dev/fusion/archive/GzipHead.kt'],
    'vault': ['feature/vault/src/main/kotlin/dev/fusion/vault/TotpCode.kt'],
}
CONSUMER = ['testing/src/r33/kotlin/dev/fusion/testing/R33Checks.kt']

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    kotlinc = env['KOTLINC']
    java = env['JAVA_HOME'] + '/bin/java'
    build = ROOT / 'testing/build/r33'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r33-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r33-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R33Checks', str(ROOT / 'tools/fixtures/r33')]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R33 contracts FAILED (exit {r.returncode})')

if __name__ == '__main__':
    main()
