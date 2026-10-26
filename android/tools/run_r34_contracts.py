#!/usr/bin/env python3
"""Compile the 10 R34 kernels as separate kotlinc-jvm groups and run R34Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'calc': ['feature/calc/src/main/kotlin/dev/fusion/calc/StatSummary.kt'],
    'files': ['core/files/src/main/java/dev/fusion/files/MimeGuess.kt'],
    'notes': ['feature/notes/src/main/java/dev/fusion/notes/OutlineParse.kt'],
    'editor': ['feature/editor/src/main/java/dev/fusion/editor/BracketMatch.kt'],
    'dlna': ['cast/dlna/src/main/java/dev/fusion/cast/dlna/SoapFault.kt'],
    'commands': ['core/commands/src/main/kotlin/dev/fusion/commands/PipePlan.kt'],
    'image': ['format/image/src/main/kotlin/dev/fusion/format/image/JpegSeg.kt'],
    'archive': ['format/archive/src/main/kotlin/dev/fusion/archive/ZlibHead.kt'],
    'continuity': ['feature/continuity/src/main/kotlin/dev/fusion/continuity/VTodoParse.kt'],
    'privacy': ['privacy/src/main/java/dev/fusion/privacy/Pseudonym.kt'],
}
CONSUMER = ['testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt']

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    kotlinc = env['KOTLINC']
    java = env['JAVA_HOME'] + '/bin/java'
    build = ROOT / 'testing/build/r34'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r34-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r34-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R34Checks', str(ROOT / 'tools/fixtures/r34')]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R34 contracts FAILED (exit {r.returncode})')

if __name__ == '__main__':
    main()
