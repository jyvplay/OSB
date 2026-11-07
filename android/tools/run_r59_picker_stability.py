#!/usr/bin/env python3
"""Compile the actual test-only picker stability gate on a host JVM; not a device result."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    out = ROOT / 'testing/build/r59-picker'
    out.mkdir(parents=True, exist_ok=True)
    jar = out / 'r59-picker.jar'
    jar.unlink(missing_ok=True)
    sources = [ROOT / 'app/src/androidTest/java/dev/fusion/shell/PickerRootStability.kt',
               ROOT / 'testing/src/r59picker/kotlin/dev/fusion/shell/R59PickerStabilityChecks.kt']
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime', *map(str, sources), '-d', str(jar)],
                   env=env, check=True)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-cp', str(jar), 'dev.fusion.shell.R59PickerStabilityChecks'],
                   env=env, check=True, timeout=30)


if __name__ == '__main__':
    main()
