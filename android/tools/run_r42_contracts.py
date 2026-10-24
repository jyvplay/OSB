#!/usr/bin/env python3
"""Compile the 22 R42 kernels as separate kotlinc-jvm groups and run R42Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'ooxml-abs': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt',
                  'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt',
                  'format/ooxml/src/main/kotlin/dev/fusion/ooxml/AbsRef.kt',
                  'format/ooxml/src/main/kotlin/dev/fusion/ooxml/MergedCells.kt'],
    'pdf-date': ['format/pdf/src/main/java/dev/fusion/format/pdf/PdfDate.kt'],
    'maps-nmea': ['feature/maps/src/main/kotlin/dev/fusion/maps/NmeaLat.kt'],
    'browser-hsts': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/HstsParse.kt'],
    'continuity-damm': ['feature/continuity/src/main/kotlin/dev/fusion/continuity/PairPin.kt'],
    'archive-ar': ['format/archive/src/main/kotlin/dev/fusion/archive/ArHead.kt'],
    'documents-case': ['core/documents/src/main/kotlin/dev/fusion/documents/CaseCycle.kt'],
    'editor-comment': ['feature/editor/src/main/java/dev/fusion/editor/CommentToggle.kt'],
    'notes-unitprice': ['feature/notes/src/main/kotlin/dev/fusion/notes/UnitPrice.kt'],
    'capture-thirds': ['feature/capture/src/main/kotlin/dev/fusion/capture/Rule3rds.kt'],
    'perf-histo': ['core/perf/src/main/kotlin/dev/fusion/perf/Histo.kt'],
    'terminal-csi': ['feature/terminal/src/main/kotlin/dev/fusion/terminal/CsiCursor.kt'],
    'privacy-gpc': ['privacy/src/main/java/dev/fusion/privacy/GpcHeader.kt'],
    'vault-policy': ['feature/vault/src/main/kotlin/dev/fusion/vault/PwPolicy.kt'],
    'media-gain': ['feature/media/src/main/kotlin/dev/fusion/media/GainDb.kt'],
    'storage-hex': ['core/storage/src/main/kotlin/dev/fusion/storage/HexBytes.kt'],
    'alerts-snooze': ['feature/alerts/src/main/kotlin/dev/fusion/alerts/SnoozeSeq.kt'],
    'calc-nicestep': ['feature/calc/src/main/kotlin/dev/fusion/calc/NiceStep.kt'],
    'automation-nextrun': ['feature/automation/src/main/kotlin/dev/fusion/automation/NextRun.kt'],
    'messages-2231': ['feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2231.kt'],
    'image-bmp': ['format/image/src/main/kotlin/dev/fusion/format/image/BmpHead.kt'],
}
CONSUMER = ['testing/src/r42/kotlin/dev/fusion/testing/R42Checks.kt']


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
    build = ROOT / 'testing/build/r42'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r42-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r42-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r42'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R42Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R42 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
