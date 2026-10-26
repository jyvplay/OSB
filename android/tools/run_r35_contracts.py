#!/usr/bin/env python3
"""Compile the 10 R35 kernels as separate kotlinc-jvm groups and run R35Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'lens': ['feature/lens/src/main/kotlin/dev/fusion/lens/EvShift.kt'],
    'vault': ['feature/vault/src/main/kotlin/dev/fusion/vault/XorSplit.kt'],
    'documents': ['core/documents/src/main/kotlin/dev/fusion/documents/WordDiff.kt'],
    'files': ['feature/files/src/main/kotlin/dev/fusion/filesui/DupGroup.kt'],
    'index': ['core/index/src/main/kotlin/dev/fusion/index/SnippetCut.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/Id3Head.kt'],
    'maps': ['feature/maps/src/main/kotlin/dev/fusion/maps/TileMath.kt',
             'feature/maps/src/main/kotlin/dev/fusion/maps/TileCover.kt'],
    'continuity': ['feature/continuity/src/main/kotlin/dev/fusion/continuity/VCardParse.kt'],
    'privacy': ['privacy/src/main/java/dev/fusion/privacy/FieldMask.kt'],
    'editor': ['feature/editor/src/main/java/dev/fusion/editor/IndentDetect.kt'],
}
CONSUMER = ['testing/src/r35/kotlin/dev/fusion/testing/R35Checks.kt']

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    kotlinc = env['KOTLINC']
    java = env['JAVA_HOME'] + '/bin/java'
    build = ROOT / 'testing/build/r35'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r35-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r35-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R35Checks', str(ROOT / 'tools/fixtures/r35')]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R35 contracts FAILED (exit {r.returncode})')

if __name__ == '__main__':
    main()
