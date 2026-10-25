#!/usr/bin/env python3
"""Compile the 24 R44 kernels as separate kotlinc-jvm groups and run R44Checks.

Host JVM only; NOT Android, device, service, or acceptance evidence.
Each group is compiled alone so a kernel cannot borrow a sibling's symbols.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'ooxml-hide': ['format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt',
                   'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt',
                   'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetHide.kt'],
    'documents-quote': ['core/documents/src/main/kotlin/dev/fusion/documents/QuoteNest.kt'],
    'editor-goto': ['feature/editor/src/main/java/dev/fusion/editor/GoToLine.kt'],
    'pdf-objnum': ['format/pdf/src/main/java/dev/fusion/format/pdf/ObjNum.kt'],
    'security-sri': ['core/security/src/main/kotlin/dev/fusion/security/SriHash.kt'],
    'archive-debver': ['format/archive/src/main/kotlin/dev/fusion/archive/DebVersion.kt'],
    'lens-issn-upce': ['feature/lens/src/main/kotlin/dev/fusion/lens/Ean13.kt',
                       'feature/lens/src/main/kotlin/dev/fusion/lens/Issn.kt',
                       'feature/lens/src/main/kotlin/dev/fusion/lens/UpcE.kt'],
    'terminal-tilde': ['feature/terminal/src/main/kotlin/dev/fusion/terminal/CwdTilde.kt'],
    'index-lev': ['core/index/src/main/kotlin/dev/fusion/index/Levenshtein.kt'],
    'settings-lint': ['core/settings/src/main/kotlin/dev/fusion/settings/PrefKeyLint.kt'],
    'commands-hist': ['core/commands/src/main/kotlin/dev/fusion/commands/HistDedup.kt'],
    'media-samples': ['feature/media/src/main/kotlin/dev/fusion/media/SampleBytes.kt'],
    'messages-subject': ['feature/messages/src/main/kotlin/dev/fusion/messages/SubjectNorm.kt'],
    'privacy-gps': ['privacy/src/main/java/dev/fusion/privacy/GpsRational.kt'],
    'hw-inch': ['core/hw/src/main/kotlin/dev/fusion/hw/DisplayInch.kt'],
    'backup-name': ['feature/backup/src/main/kotlin/dev/fusion/backup/BackupName.kt'],
    'launcher-alpha': ['feature/launcher/src/main/kotlin/dev/fusion/launcher/AlphaIndex.kt'],
    'image-orient': ['format/image/src/main/kotlin/dev/fusion/format/image/ExifOrient.kt'],
    'browser-lang': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/AcceptLang.kt'],
    'notes-tags': ['feature/notes/src/main/kotlin/dev/fusion/notes/TagExtract.kt'],
    'perf-frame': ['core/perf/src/main/kotlin/dev/fusion/perf/FrameBudget.kt'],
    'files-dot': ['feature/files/src/main/kotlin/dev/fusion/filesui/HiddenDot.kt'],
    'together-code': ['feature/together/src/main/kotlin/dev/fusion/together/SessionCode.kt'],
}
CONSUMER = ['testing/src/r44/kotlin/dev/fusion/testing/R44Checks.kt']


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
    build = ROOT / 'testing/build/r44'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r44-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r44-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    fixtures = a.fixtures.expanduser().resolve() if a.fixtures else ROOT / 'tools/fixtures/r44'
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R44Checks', str(fixtures)]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R44 contracts FAILED (exit {r.returncode})')


if __name__ == '__main__':
    main()
