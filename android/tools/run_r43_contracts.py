#!/usr/bin/env python3
"""Compile the 23 R43 kernels as separate kotlinc-jvm groups and run R43Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'ooxml-freeze': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt',
                     'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt',
                     'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetFreeze.kt'],
    'documents-bidi': ['core/documents/src/main/kotlin/dev/fusion/documents/BidiStrip.kt'],
    'editor-dup': ['feature/editor/src/main/java/dev/fusion/editor/DupLines.kt'],
    'pdf-perm': ['format/pdf/src/main/java/dev/fusion/format/pdf/PdfPerm.kt'],
    'browser-referer': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/RefererPolicy.kt'],
    'archive-debctl': ['format/archive/src/main/kotlin/dev/fusion/archive/DebControl.kt'],
    'capabilities-api': ['core/capabilities/src/main/java/dev/fusion/capabilities/ApiName.kt'],
    'together-seat': ['feature/together/src/main/kotlin/dev/fusion/together/SeatColor.kt'],
    'media-bpm': ['feature/media/src/main/kotlin/dev/fusion/media/Bpm.kt'],
    'beam-tier': ['feature/beam/src/main/kotlin/dev/fusion/beam/TrackerTier.kt'],
    'lens-qr': ['feature/lens/src/main/kotlin/dev/fusion/lens/QrVersion.kt'],
    'security-rpid': ['core/security/src/main/kotlin/dev/fusion/security/RpIdMatch.kt'],
    'model-range': ['core/model/src/main/kotlin/dev/fusion/model/SemVer.kt',
                    'core/model/src/main/kotlin/dev/fusion/model/VersionRange.kt'],
    'find-snippet': ['feature/find/src/main/java/dev/fusion/find/SpanMerge.kt',
                     'feature/find/src/main/java/dev/fusion/find/SnippetWindow.kt'],
    'vault-age': ['feature/vault/src/main/kotlin/dev/fusion/vault/Bech32.kt',
                  'feature/vault/src/main/kotlin/dev/fusion/vault/AgeId.kt'],
    'files-breadcrumb': ['feature/files/src/main/kotlin/dev/fusion/filesui/BreadcrumbTrim.kt'],
    'perf-apdex': ['core/perf/src/main/kotlin/dev/fusion/perf/Apdex.kt'],
    'calc-percent': ['feature/calc/src/main/kotlin/dev/fusion/calc/PercentChange.kt'],
    'airplay-transport': ['cast/airplay/src/main/kotlin/dev/fusion/airplay/RtspTransport.kt'],
    'messages-msgid': ['feature/messages/src/main/kotlin/dev/fusion/messages/MsgIdParse.kt'],
    'privacy-coarse': ['privacy/src/main/java/dev/fusion/privacy/TimeCoarse.kt'],
    'windowing-aspect': ['core/windowing/src/main/kotlin/dev/fusion/windowing/AspectSnap.kt'],
    'alerts-key': ['feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertKey.kt'],
}
CONSUMER = ['testing/src/r43/kotlin/dev/fusion/testing/R43Checks.kt']


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
    build = ROOT / 'testing/build/r43'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r43-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r43-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r43'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R43Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R43 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
