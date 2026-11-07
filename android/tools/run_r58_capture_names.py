#!/usr/bin/env python3
"""Run the Android capture helper's pure filename policy on the host; no screenshot claim."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    out = ROOT / 'testing/build/r58/capture-names.jar'
    out.parent.mkdir(parents=True, exist_ok=True)
    sources = [ROOT / 'app/src/androidTest/java/dev/fusion/shell/NativeCaptureNames.kt',
               ROOT / 'testing/src/r58capture/kotlin/dev/fusion/shell/R58CaptureNameChecks.kt']
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(out)], check=True, env=env)
    subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-cp', str(out),
                    'dev.fusion.shell.R58CaptureNameChecks'], check=True, env=env)


if __name__ == '__main__':
    main()
