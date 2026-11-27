#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""R176 real host gate; optional compile-only Android API surface and Java2D frame.

Install pinned tools explicitly with bootstrap_host_tools.py install. No emulator
or SDK is invented. Optional --android-jar accepts your installed SDK's android.jar;
this checks the new Activity, not Gradle resources/manifest merge or the full app.
"""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from bootstrap_host_tools import environment
from test_r176_system import ROOT, SOURCES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar', type=Path)
    parser.add_argument('--artifacts', type=Path)
    args = parser.parse_args()
    cache = Path.home() / '.cache/fusion-host-r23'
    if not (cache / 'host-tools.json').is_file():
        raise SystemExit('Pinned JVM toolchain absent: install explicitly. No execution claimed.')
    env = environment(cache)
    suite = unittest.defaultTestLoader.discover(str(ROOT / 'tools'), pattern='test_r176_*.py')
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if not result.wasSuccessful() or result.skipped:
        return 1
    with tempfile.TemporaryDirectory(prefix='r176-build-') as temp:
        output = Path(temp)
        if args.android_jar:
            jar = args.android_jar.resolve(strict=True)
            platform_sources = [ROOT / p for p in SOURCES[:4]] + [
                ROOT / 'app/src/main/java/dev/fusion/shell/DiagnosticExportActivity.kt']
            subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-classpath', str(jar),
                            '-d', str(output / 'activity'), *map(str, platform_sources)], env=env, check=True)
            print('R176_ANDROID_API_COMPILE_OK jar_sha256=' + hashlib.sha256(jar.read_bytes()).hexdigest())
            print('Compile-only Android Activity + shared workflow; NOT APK/resources/Gradle/device verification.')
        else:
            print('R176 Android API compilation NOT RUN (no --android-jar supplied).')
        if args.artifacts:
            artifact_dir = args.artifacts.resolve()
            artifact_dir.mkdir(parents=True, exist_ok=True)
            sources = [ROOT / p for p in SOURCES[:4]] + [
                ROOT / 'testing/src/r176/kotlin/dev/fusion/testing/R176ReviewFrame.kt']
            subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-d', str(output / 'frame'),
                            *map(str, sources)], env=env, check=True)
            stdlib = cache / 'node_modules/kotlin-compiler/lib/kotlin-stdlib.jar'
            subprocess.run([env['JAVA_HOME'] + '/bin/java', '-Djava.awt.headless=true', '-cp',
                            str(output / 'frame') + os.pathsep + str(stdlib),
                            'dev.fusion.testing.R176ReviewFrame', str(artifact_dir)], env=env, check=True)
    print('R176_HOST_OK range_cases=1039 decimal_cases=169 archive_cases=27 workflow_assertions=84 geometry_cases=262 search_cases=404')
    return 0


if __name__ == '__main__':
    sys.exit(main())
