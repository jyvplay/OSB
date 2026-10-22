#!/usr/bin/env python3
"""Compile the 10 R31 kernels as separate kotlinc-jvm groups and run R31Checks.

Cross-module dependencies are explicit per group (hw carries Models.kt for
CodecInfo). Host JVM only; NOT Android, device, service, or acceptance evidence.
"""
import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GROUPS = {
    'privacy': ['privacy/src/main/java/dev/fusion/privacy/LogScrubber.kt'],
    'capabilities': ['core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityGate.kt'],
    'index': ['core/index/src/main/kotlin/dev/fusion/index/SearchQuery.kt'],
    'editor': ['feature/editor/src/main/java/dev/fusion/editor/TextOffsets.kt'],
    'vault': ['feature/vault/src/main/kotlin/dev/fusion/vault/VaultManifest.kt'],
    'pdf': ['format/pdf/src/main/java/dev/fusion/format/pdf/PdfTrailer.kt'],
    'psd': ['format/psd/src/main/kotlin/dev/fusion/psd/PsdResources.kt'],
    'hw': ['core/hw/src/main/java/dev/fusion/hw/CodecPreference.kt',
           'core/model/src/main/kotlin/dev/fusion/model/Models.kt'],
    'perf': ['core/perf/src/main/java/dev/fusion/perf/PhaseLedger.kt'],
    'browser': ['feature/browser/src/main/kotlin/dev/fusion/browser/model/RobotsRules.kt'],
}
CONSUMER = ['testing/src/r31/kotlin/dev/fusion/testing/R31Checks.kt']

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    kotlinc = env['KOTLINC']
    java = env['JAVA_HOME'] + '/bin/java'
    build = ROOT / 'testing/build/r31'
    build.mkdir(parents=True, exist_ok=True)
    jars = []
    for name, sources in GROUPS.items():
        for rel in sources:
            text = (ROOT / rel).read_text()
            assert 'import android' not in text and 'import androidx' not in text, rel
        jar = build / f'r31-{name}.jar'
        cmd = [kotlinc] + [str(ROOT / s) for s in sources] + ['-include-runtime', '-d', str(jar)]
        subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
        jars.append(jar)
    checks_jar = build / 'r31-checks.jar'
    cp = ':'.join(str(j) for j in jars)
    cmd = [kotlinc] + [str(ROOT / s) for s in CONSUMER] + ['-cp', cp, '-d', str(checks_jar)]
    subprocess.run(cmd, env=env, check=True, capture_output=True, text=True, timeout=600)
    full_cp = str(checks_jar) + ':' + cp
    cmd = [java, '-cp', full_cp, 'dev.fusion.testing.R31Checks', str(ROOT / 'tools/fixtures/r31')]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=600)
    sys.stdout.write(r.stdout)
    sys.stderr.write(r.stderr)
    if r.returncode != 0:
        raise SystemExit(f'R31 contracts FAILED (exit {r.returncode})')

if __name__ == '__main__':
    main()
