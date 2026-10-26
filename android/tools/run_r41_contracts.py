#!/usr/bin/env python3
"""Compile the 21 R41 kernels as separate kotlinc-jvm groups and run R41Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'ooxml-refs': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt',
                   'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt',
                   'format/ooxml/src/main/kotlin/dev/fusion/ooxml/R1c1Ref.kt'],
    'pdf-geometry': ['format/pdf/src/main/java/dev/fusion/format/pdf/CropBox.kt',
                     'format/pdf/src/main/java/dev/fusion/format/pdf/PdfRotate.kt',
                     'format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageGeometry.kt'],
    'commands-shlex': ['core/commands/src/main/kotlin/dev/fusion/commands/ShlexJoin.kt'],
    'automation-duration': ['feature/automation/src/main/kotlin/dev/fusion/automation/DurationIso.kt'],
    'maps-dms': ['feature/maps/src/main/kotlin/dev/fusion/maps/DmsFormat.kt'],
    'model-color': ['core/model/src/main/kotlin/dev/fusion/model/ColorHex.kt'],
    'a11y-readease': ['feature/accessibility/src/main/kotlin/dev/fusion/a11y/ReadEase.kt'],
    'lens-ean': ['feature/lens/src/main/kotlin/dev/fusion/lens/Ean13.kt'],
    'browser-datauri': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/DataUri.kt'],
    'index-trigram': ['core/index/src/main/kotlin/dev/fusion/index/Trigram.kt'],
    'together-turn': ['feature/together/src/main/kotlin/dev/fusion/together/TurnOrder.kt'],
    'perf-ewma': ['core/perf/src/main/kotlin/dev/fusion/perf/Ewma.kt'],
    'settings-migrate': ['core/settings/src/main/kotlin/dev/fusion/settings/KeyMigrate.kt'],
    'castv2-ns': ['cast/castv2/src/main/kotlin/dev/fusion/castv2/CastNs.kt'],
    'documents-width': ['core/documents/src/main/kotlin/dev/fusion/documents/LineWidth.kt'],
    'storage-shard': ['core/storage/src/main/kotlin/dev/fusion/storage/ShardPath.kt'],
    'find-span': ['feature/find/src/main/java/dev/fusion/find/SpanMerge.kt'],
    'beam-piece': ['feature/beam/src/main/kotlin/dev/fusion/beam/PiecePlan.kt'],
    'editor-tabs': ['feature/editor/src/main/java/dev/fusion/editor/TabStops.kt'],
    'launcher-grid': ['feature/launcher/src/main/kotlin/dev/fusion/launcher/GridPos.kt'],
}
CONSUMER = ['testing/src/r41/kotlin/dev/fusion/testing/R41Checks.kt']


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
    build = ROOT / 'testing/build/r41'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r41-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r41-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r41'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R41Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R41 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
