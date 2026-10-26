#!/usr/bin/env python3
"""Compile the 14 R37 kernels as separate kotlinc-jvm groups and run R37Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols;
the continuity group carries VTodoParse because TaskTally consumes its Todo.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'notes': ['feature/notes/src/main/java/dev/fusion/notes/SlugHead.kt'],
    'files': ['feature/files/src/main/kotlin/dev/fusion/filesui/SizeBucket.kt'],
    'continuity': ['feature/continuity/src/main/kotlin/dev/fusion/continuity/VTodoParse.kt',
                   'feature/continuity/src/main/kotlin/dev/fusion/continuity/TaskTally.kt'],
    'archive': ['format/archive/src/main/kotlin/dev/fusion/archive/XzHead.kt'],
    'editor': ['feature/editor/src/main/java/dev/fusion/editor/PasteWrap.kt'],
    'airplay': ['cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspReason.kt'],
    'privacy': ['privacy/src/main/java/dev/fusion/privacy/UtmStrip.kt'],
    'security': ['core/security/src/main/kotlin/dev/fusion/security/HibpRange.kt'],
    'maps': ['feature/maps/src/main/kotlin/dev/fusion/maps/PolyLine.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/Mp4Box.kt'],
    'terminal': ['feature/terminal/src/main/kotlin/dev/fusion/terminal/OscLink.kt'],
    'find': ['feature/find/src/main/kotlin/dev/fusion/find/RankFuse.kt'],
    'commands': ['core/commands/src/main/kotlin/dev/fusion/commands/ExitCode.kt'],
    'lens': ['feature/lens/src/main/kotlin/dev/fusion/lens/FovMath.kt'],
}
CONSUMER = ['testing/src/r37/kotlin/dev/fusion/testing/R37Checks.kt']

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    p.add_argument('--fixtures', type=Path, default=None)
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    kotlinc = env['KOTLINC']
    java = env['JAVA_HOME'] + '/bin/java'
    build = ROOT / 'testing/build/r37'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r37-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r37-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r37'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R37Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R37 contracts FAILED (exit {r.returncode})')

if __name__ == '__main__':
    main()
