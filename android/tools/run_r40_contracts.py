#!/usr/bin/env python3
"""Compile the 20 R40 kernels as separate kotlinc-jvm groups and run R40Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'ooxml-numfmt': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/NumFmtSections.kt'],
    'ooxml-cellerr': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt'],
    'documents-dash': ['core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt'],
    'documents-obj': ['core/documents/src/main/kotlin/dev/fusion/documents/ObjReplace.kt'],
    'editor-footnote': ['feature/editor/src/main/java/dev/fusion/editor/FootnoteMark.kt'],
    'pdf-rotate': ['format/pdf/src/main/java/dev/fusion/format/pdf/PdfRotate.kt'],
    'browser-entity': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/HtmlEntity.kt'],
    'messages-2047': ['feature/messages/src/main/kotlin/dev/fusion/messages/Rfc2047.kt'],
    'archive-7z': ['format/archive/src/main/kotlin/dev/fusion/archive/SevenZHead.kt'],
    'index-fnv': ['core/index/src/main/kotlin/dev/fusion/index/Fnv1a.kt'],
    'lens-mrz': ['feature/lens/src/main/kotlin/dev/fusion/lens/Mrz.kt'],
    'files-safename': ['core/files/src/main/java/dev/fusion/files/SafeName.kt'],
    'perf-percentile': ['core/perf/src/main/kotlin/dev/fusion/perf/Percentile.kt'],
    'notes-task': ['feature/notes/src/main/kotlin/dev/fusion/notes/TaskCheckbox.kt'],
    'windowing-snap': ['core/windowing/src/main/kotlin/dev/fusion/windowing/SnapFraction.kt'],
    'airplay-plist': ['cast/airplay/src/main/kotlin/dev/fusion/airplay/PlistHead.kt'],
    'launcher-monogram': ['feature/launcher/src/main/kotlin/dev/fusion/launcher/Monogram.kt'],
    'vault-bech32': ['feature/vault/src/main/kotlin/dev/fusion/vault/Bech32.kt'],
    'security-otpauth': ['core/security/src/main/kotlin/dev/fusion/security/OtpauthUri.kt'],
    'privacy-ip': ['privacy/src/main/java/dev/fusion/privacy/IpRedact.kt'],
}
CONSUMER = ['testing/src/r40/kotlin/dev/fusion/testing/R40Checks.kt']


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
    build = ROOT / 'testing/build/r40'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r40-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r40-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r40'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R40Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R40 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
