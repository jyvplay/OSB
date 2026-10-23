#!/usr/bin/env python3
"""Compile the 17 R38 kernels as separate kotlinc-jvm groups and run R38Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'beam-magnet': ['feature/beam/src/main/kotlin/dev/fusion/beam/MagnetParse.kt'],
    'beam-bencode': ['feature/beam/src/main/kotlin/dev/fusion/beam/Bencode.kt'],
    'media': ['feature/media/src/main/kotlin/dev/fusion/media/FlacHead.kt'],
    'image': ['format/image/src/main/kotlin/dev/fusion/format/image/WebpHead.kt'],
    'model': ['core/model/src/main/kotlin/dev/fusion/model/SemVer.kt'],
    'storage': ['core/storage/src/main/kotlin/dev/fusion/storage/UuidInfo.kt'],
    'browser': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/HttpRange.kt'],
    'messages': ['feature/messages/src/main/kotlin/dev/fusion/messages/QuotedPrint.kt'],
    'security-pem': ['core/security/src/main/kotlin/dev/fusion/security/PemBound.kt'],
    'automation': ['feature/automation/src/main/kotlin/dev/fusion/automation/IsoWeek.kt'],
    'a11y': ['feature/accessibility/src/main/kotlin/dev/fusion/a11y/HslColor.kt'],
    'hw': ['core/hw/src/main/kotlin/dev/fusion/hw/EdidHead.kt'],
    'capture': ['feature/capture/src/main/kotlin/dev/fusion/capture/VttTime.kt'],
    'privacy': ['privacy/src/main/java/dev/fusion/privacy/LuhnCheck.kt'],
    'index': ['core/index/src/main/kotlin/dev/fusion/index/Soundex.kt'],
    'pdf': ['format/pdf/src/main/java/dev/fusion/format/pdf/PdfName.kt'],
    'security-cadence': ['core/security/src/main/kotlin/dev/fusion/security/KeyCadence.kt'],
}
CONSUMER = ['testing/src/r38/kotlin/dev/fusion/testing/R38Checks.kt']


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
    build = ROOT / 'testing/build/r38'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r38-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r38-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r38'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R38Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R38 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
