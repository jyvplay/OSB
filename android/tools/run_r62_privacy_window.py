#!/usr/bin/env python3
"""Compile and execute the real test-only window classifier; no Android environment is faked."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / 'privacy/src/androidTest/java/dev/fusion/privacy/PrivacyWindowClassifier.kt',
    ROOT / 'testing/src/r62privacy/kotlin/dev/fusion/testing/PrivacyWindowChecks.kt',
]


def main():
    out = ROOT / 'testing/build/r62privacy'
    out.mkdir(parents=True, exist_ok=True)
    receipt = out / 'result.json'
    receipt.unlink(missing_ok=True)
    jar = out / 'checks.jar'
    compiler = os.environ.get('KOTLINC', 'kotlinc')
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    subprocess.run([compiler, '-jvm-target', '17', *map(str, SOURCES), '-include-runtime', '-d', str(jar)], check=True)
    result = subprocess.run([java, '-cp', str(jar), 'dev.fusion.testing.PrivacyWindowChecks'], check=True, capture_output=True, text=True)
    print(result.stdout, end='')
    receipt.write_text(json.dumps({
        'scope': 'Real host classifier only; no Android windows or privacy acceptance observed.',
        'output': result.stdout,
        'sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in SOURCES},
    }, indent=2) + '\n')


if __name__ == '__main__': main()
