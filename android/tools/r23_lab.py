#!/usr/bin/env python3
"""R23 real SDK-free contracts or real SDK builds. No mock APK, web app, root or simulated device."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from bootstrap_host_tools import DEFAULT, environment

ROOT = Path(__file__).resolve().parents[1]
MEMORY_CLASSES = ','.join('dev.fusion.shell.' + name for name in [
    'R22PlatformDeviceTest', 'R22BoundaryDeviceTest', 'R22XlsxUiDeviceTest',
    'R23MemoryTransportDeviceTest', 'FormatIsolationDeviceTest', 'R21CsvUiDeviceTest', 'R18OwnedSheetDeviceTest'])


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['doctor', 'host', 'android', 'device'])
    p.add_argument('--suite', choices=['privacy', 'memory', 'full'], default='privacy', help='Actual device suite; memory includes retained FD/picker regressions')
    p.add_argument('--tools', type=Path, help='Explicit host-test cache, not for Android/Gradle')
    p.add_argument('--disposable', action='store_true', help='Consent to run tests on a disposable managed Android device')
    a = p.parse_args()
    if a.tools and a.mode in ['android', 'device']:
        raise SystemExit('Host cache contains a JRE, not a JDK. Configure maintained JDK17 and Android SDK separately.')
    env = environment(a.tools.expanduser().resolve()) if a.tools else os.environ.copy()
    java = str(Path(env['JAVA_HOME']) / 'bin/java') if env.get('JAVA_HOME') else shutil.which('java')
    compiler = shutil.which(env.get('KOTLINC', 'kotlinc'))
    def run(command): subprocess.run(command, cwd=ROOT, env=env, check=True)
    if a.mode == 'doctor':
        print(json.dumps({'java': java, 'kotlinc': compiler, 'javac': (str(Path(env['JAVA_HOME']) / 'bin/javac') if Path(env.get('JAVA_HOME', '/nonexistent'), 'bin/javac').is_file() else None) if env.get('JAVA_HOME') else shutil.which('javac'),
                          'ANDROID_HOME': env.get('ANDROID_HOME'), 'ANDROID_SDK_ROOT': env.get('ANDROID_SDK_ROOT'),
                          'adb': shutil.which('adb'), 'gh': shutil.which('gh'), 'default_optional_cache': str(DEFAULT),
                          'scope': 'Availability only; a printed path does not establish an installed JDK/SDK or successful test.'}, indent=2))
        return
    if a.mode == 'host':
        if not java or not Path(java).is_file() or not compiler:
            raise SystemExit('Need real Java17 runtime and Kotlin2.2.21. Use bootstrap_host_tools.py explicitly or configure JAVA_HOME/KOTLINC.')
        for script in ['check_project.py', 'run_r23_contracts.py', 'run_r22_contracts.py', 'run_r22_interop.py', 'run_r21_contracts.py']:
            run([sys.executable, 'tools/' + script])
        run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tools', '-p', 'test_*.py', '-v'])
        print('Host pipeline completed. This does NOT compile Android or verify hardware anonymity.')
        return
    sdk = env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
    if not sdk or not Path(sdk).is_dir(): raise SystemExit('Real Android SDK required; no SDK installed or execution inferred.')
    if not env.get('JAVA_HOME') or not Path(env['JAVA_HOME'], 'bin/javac' + ('.exe' if os.name == 'nt' else '')).is_file():
        raise SystemExit('Real JDK17 JAVA_HOME (with javac) required; host-cache JRE is insufficient.')
    gradle = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
    if a.mode == 'android':
        run([gradle, ':app:assembleProDebug', ':app:assemblePlayDebug', ':privacy:assembleDebug', ':privacy:lintDebug', ':testing:r23Contracts'])
    else:
        if not a.disposable: raise SystemExit('Refuse device tests without --disposable consent. Use disposable AVD/data only.')
        if a.suite == 'privacy':
            run([gradle, ':privacy:pixel6Api34DebugAndroidTest'])
        else:
            command = [gradle, ':app:pixel6Api34ProDebugAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true']
            if a.suite == 'memory': command.append('-Pandroid.testInstrumentationRunnerArguments.class=' + MEMORY_CLASSES)
            else: command.append('-Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest') # Dedicated manual capture requires its own run/font arguments; same exclusion as actual CI.
            run(command)


if __name__ == '__main__': main()
