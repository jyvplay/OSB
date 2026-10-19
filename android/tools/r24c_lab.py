#!/usr/bin/env python3
"""No-Studio entry point. Host kernels are NOT an APK or an Android emulator."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from bootstrap_host_tools import environment

ROOT = Path(__file__).resolve().parents[1]
CLASSES = 'dev.fusion.shell.R24TabletopDeviceTest,dev.fusion.shell.R24LocalMediaDeviceTest,dev.fusion.shell.R24MapAndServiceDeviceTest'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['doctor', 'host', 'android', 'device'])
    p.add_argument('--tools', type=Path)
    p.add_argument('--suite', choices=['continuation', 'full', 'privacy'], default='continuation')
    p.add_argument('--disposable', action='store_true')
    a = p.parse_args()
    env = environment(a.tools.expanduser().resolve()) if a.tools else os.environ.copy()
    def run(command):
        subprocess.run(command, cwd=ROOT, env=env, check=True)
    if a.mode == 'doctor':
        run([sys.executable, 'tools/r24_lab.py', 'doctor'] + (['--tools', str(a.tools)] if a.tools else []))
        return
    if a.mode == 'host':
        run([sys.executable, 'tools/check_project.py'])
        run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tools', '-p', 'test_*.py'])
        run([sys.executable, 'tools/natural_earth_asset.py', '--check'])
        run([sys.executable, 'tools/run_r24c_contracts.py'])
        run([sys.executable, 'tools/run_r24_contracts.py'])
        return
    if a.tools:
        raise SystemExit('Host compiler cache is an old JRE, not an Android JDK/SDK; no native run attempted.')
    java_home = os.environ.get('JAVA_HOME', '')
    if not java_home or not Path(java_home, 'bin', 'javac.exe' if os.name == 'nt' else 'javac').is_file() or not (os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')):
        raise SystemExit('Actual maintained full JDK17 and Android SDK required. Run host mode without Studio; it does not produce an APK.')
    gradle = 'gradlew.bat' if os.name == 'nt' else './gradlew'
    if a.mode == 'android':
        run([gradle, '--no-daemon', ':app:assembleProDebug', ':app:assemblePlayDebug', ':privacy:assembleDebug', ':testing:r24cContracts', ':app:lintProDebug', ':app:lintPlayDebug', ':privacy:lintDebug'])
        return
    if not a.disposable:
        raise SystemExit('Device tests reset test-owned data. Pass --disposable only for an expendable AVD.')
    if a.suite == 'privacy':
        run([gradle, ':privacy:pixel6Api34DebugAndroidTest'])
    else:
        args = ['-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true']
        args += ['-Pandroid.testInstrumentationRunnerArguments.class=' + CLASSES] if a.suite == 'continuation' else ['-Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest']
        run([gradle, ':app:pixel6Api34ProDebugAndroidTest', *args])


if __name__ == '__main__': main()
