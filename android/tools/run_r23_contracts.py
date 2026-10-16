#!/usr/bin/env python3
"""Compile actual production privacy kernels separately, then a public consumer. No SDK/stubs required."""
import hashlib
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    out = ROOT / 'testing/build/r23'
    out.mkdir(parents=True, exist_ok=True)
    result = out / 'result.txt'
    result.unlink(missing_ok=True)
    compiler = os.environ.get('KOTLINC', 'kotlinc')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
    versions = []
    for command in ([compiler, '-version'], [java, '-version']):
        p = subprocess.run(command, text=True, capture_output=True, check=True)
        versions.append(p.stdout + p.stderr)
    sources = [ROOT / 'core/security/src/main/kotlin/dev/fusion/security' / f'{name}.kt'
               for name in ('TouchAdmission', 'VolatileDigits', 'LocalExposure')]
    jars = []
    for source in sources:
        jar = out / (source.stem + '.jar')
        subprocess.run([compiler, '-jvm-target', '17', '-module-name', source.stem, str(source), '-d', str(jar)], check=True)
        jars.append(jar)
    checks = sorted((ROOT / 'testing/src/r23/kotlin').rglob('*.kt'))
    jar = out / 'checks.jar'
    cp = os.pathsep.join(map(str, jars))
    subprocess.run([compiler, '-jvm-target', '17', '-classpath', cp, *map(str, checks), '-include-runtime', '-d', str(jar)], check=True)
    p = subprocess.run([java, '-cp', str(jar) + os.pathsep + cp, 'dev.fusion.testing.R23Checks'], capture_output=True, text=True)
    print(p.stdout + p.stderr, end='')
    if p.returncode:
        (out / 'failure.txt').write_text(p.stdout + p.stderr)
    p.check_returncode()
    files = sources + checks + jars + [jar, Path(__file__).resolve()]
    result.write_text(p.stdout + ''.join(versions) + '\nReal separately compiled Kotlin; no Android/hardware/security-certification claim.\n' +
                      ''.join(hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + str(f.relative_to(ROOT)) + '\n' for f in files))


if __name__ == '__main__':
    main()
