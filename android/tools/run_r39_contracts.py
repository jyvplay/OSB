#!/usr/bin/env python3
"""Compile the 19 R39 kernels as separate kotlinc-jvm groups and run R39Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'ooxml-serial': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/SerialDate.kt'],
    'ooxml-letters': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt'],
    'ooxml-width': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColWidth.kt'],
    'documents-quote': ['core/documents/src/main/kotlin/dev/fusion/documents/SmartQuote.kt'],
    'documents-shy': ['core/documents/src/main/kotlin/dev/fusion/documents/HyphenSoft.kt'],
    'editor-roman': ['feature/editor/src/main/java/dev/fusion/editor/RomanNum.kt'],
    'pdf-crop': ['format/pdf/src/main/java/dev/fusion/format/pdf/CropBox.kt'],
    'image-ico': ['format/image/src/main/kotlin/dev/fusion/format/image/IcoDir.kt'],
    'browser-puny': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/Punycode.kt'],
    'hw-usb': ['core/hw/src/main/kotlin/dev/fusion/hw/UsbDesc.kt'],
    'media-midi': ['feature/media/src/main/kotlin/dev/fusion/media/MidiNote.kt'],
    'storage-ulid': ['core/storage/src/main/kotlin/dev/fusion/storage/Ulid.kt'],
    'security-jwt': ['core/security/src/main/kotlin/dev/fusion/security/JwtSplit.kt'],
    'vault-iban': ['feature/vault/src/main/kotlin/dev/fusion/vault/IbanCheck.kt'],
    'find-glob': ['feature/find/src/main/java/dev/fusion/find/GlobMatch.kt'],
    'privacy-mac': ['privacy/src/main/java/dev/fusion/privacy/MacAddr.kt'],
    'backup-3339': ['feature/backup/src/main/kotlin/dev/fusion/backup/Rfc3339.kt'],
    'alerts-quiet': ['feature/alerts/src/main/kotlin/dev/fusion/alerts/QuietHours.kt'],
    'capture-ev': ['feature/capture/src/main/kotlin/dev/fusion/capture/ExposureEv.kt'],
}
CONSUMER = ['testing/src/r39/kotlin/dev/fusion/testing/R39Checks.kt']


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
    build = ROOT / 'testing/build/r39'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r39-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r39-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r39'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R39Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R39 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
