#!/usr/bin/env python3
"""Explicit, non-root SDK-FREE test-tool install into a user cache; never modifies the Android app.
Requires Python3.11+, npm and Linux x86_64. Downloads public PyPI/npm packages only on `install`.
The pinned jdk4py package is an OLD JRE17.0.9, NOT a JDK or a supported production runtime.
Use a maintained vendor JDK17 for Android/Gradle on your laptop; this JRE is for disposable host tests only.
The npm Kotlin distribution is a registry mirror, not newly independently authenticated upstream binaries.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shlex
import shutil
import subprocess
import sys

DEFAULT = Path.home() / '.cache/fusion-host-r23'
KOTLIN = '2.2.21'
INTEGRITY = 'sha512-nY7ifXZsAfCTJFagHmDQuJRJSr1W/WBUrJnLTfq78/g8u+LMM97pAqspg3ANDFLBH8+de1XdRtofpaLEr3kZiQ=='
PACKAGES = {
    'jdk4py==17.0.9.2': 'd9e5235e110182b9291ea09da413309b19dcf12d104d9aa3624abe210e593d7c',
    'openpyxl==3.1.5': '5282c12b107bffeef825f4617dc029afaf41d0ea60823bbb665ef3079dc79de2',
    'et-xmlfile==2.0.0': '7a91720bc756843502c3b7504c77b8fe44217c85c537d85037f0f536151b2caa',
    'XlsxWriter==3.2.5': '4f4824234e1eaf9d95df9a8fe974585ff91d0f5e3d3f12ace5b71e443c1c6abd'
}


def environment(directory):
    env = os.environ.copy()
    env['JAVA_HOME'] = str(directory / 'python/jdk4py/java-runtime')
    env['KOTLINC'] = str(directory / 'node_modules/kotlin-compiler/bin/kotlinc')
    env['PYTHONPATH'] = str(directory / 'python') + (os.pathsep + env['PYTHONPATH'] if env.get('PYTHONPATH') else '')
    return env


def doctor(directory):
    env = environment(directory)
    return {'directory': str(directory), 'java': Path(env['JAVA_HOME'], 'bin/java').is_file(),
            'javac': Path(env['JAVA_HOME'], 'bin/javac').is_file(), 'kotlinc': Path(env['KOTLINC']).is_file(),
            'npm': shutil.which('npm'), 'python': sys.version.split()[0],
            'scope': 'Tool availability only. Cache JRE is not JDK; no Android SDK/APK/device execution inferred.'}


def install(directory):
    if platform.system() != 'Linux' or platform.machine() not in ('x86_64', 'AMD64'):
        raise SystemExit('This cache bootstrap is tested for Linux x86_64 only. Install maintained JDK17/Kotlin2.2.21 manually on other hosts.')
    if not shutil.which('npm'):
        raise SystemExit('npm required for the pinned compiler registry package; no install attempted.')
    repo = Path(__file__).resolve().parents[2]
    if directory == repo or repo in directory.parents:
        raise SystemExit('Keep host binaries outside the repository; choose a user cache directory.')
    directory.mkdir(parents=True, exist_ok=True)
    # No package lifecycle scripts; npm validates its fetched tarball integrity.
    subprocess.run(['npm', 'install', '--prefix', str(directory), '--ignore-scripts', '--no-audit', '--no-fund',
                    '--save-exact', f'kotlin-compiler@{KOTLIN}'], check=True)
    lock = json.loads((directory / 'package-lock.json').read_text())
    entry = lock['packages']['node_modules/kotlin-compiler']
    if entry['version'] != KOTLIN or entry['integrity'] != INTEGRITY:
        raise SystemExit('Unexpected compiler mirror identity. Refuse to execute it.')
    requirements = directory / 'requirements-host.lock'
    requirements.write_text(''.join(name + ' --hash=sha256:' + digest + '\n' for name, digest in PACKAGES.items()))
    subprocess.run([sys.executable, '-m', 'pip', 'install', '--upgrade', '--only-binary=:all:', '--require-hashes',
                    '--target', str(directory / 'python'), '--report', str(directory / 'python-install.json'),
                    '-r', str(requirements)], check=True)
    env = environment(directory)
    records = []
    for cmd in ([str(Path(env['JAVA_HOME']) / 'bin/java'), '-version'], [env['KOTLINC'], '-version']):
        p = subprocess.run(cmd, env=env, capture_output=True, text=True, check=True)
        records.append(p.stdout + p.stderr)
    files = [directory / 'node_modules/kotlin-compiler/lib/kotlin-compiler.jar', Path(env['JAVA_HOME']) / 'bin/java']
    receipt = {'tool_scope': 'Host-only JRE, Kotlin compiler mirror and optional Office reference readers; NOT bundled in any APK',
               'compiler_integrity': INTEGRITY, 'versions': records, 'python_packages': PACKAGES,
               'files': {str(p.relative_to(directory)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
               'limits': 'Old JRE for disposable tests only. PyPI/npm transport and maintainers trusted. No independent upstream provenance proof.'}
    (directory / 'host-tools.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['doctor', 'install', 'env'])
    p.add_argument('--directory', type=Path, default=DEFAULT)
    a = p.parse_args(); directory = a.directory.expanduser().resolve()
    if a.mode == 'install': install(directory)
    elif a.mode == 'doctor': print(json.dumps(doctor(directory), indent=2))
    else:
        if not doctor(directory)['java'] or not doctor(directory)['kotlinc']:
            raise SystemExit('Cache incomplete. Run install explicitly, or configure maintained JAVA_HOME/KOTLINC manually.')
        print('# POSIX shell only; this environment is for standalone Kotlin host tests, NOT Gradle/Android.')
        env = environment(directory)
        for key in ['JAVA_HOME', 'KOTLINC', 'PYTHONPATH']: print('export ' + key + '=' + shlex.quote(env[key]))


if __name__ == '__main__': main()
