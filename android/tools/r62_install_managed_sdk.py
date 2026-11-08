#!/usr/bin/env python3
"""Prepare a disposable CI SDK before tests; retries installs, never device cases.

Metadata and required files are evidence of installation only, not a checksum,
boot, Android behavior, or upstream-authenticity guarantee. No SDK files are
removed, no shell is used and a failed or incomplete install remains a failure.
"""
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

IMAGE = 'system-images;android-34;google_apis;x86_64'
EMULATOR = 'emulator'
ATTEMPTS = 2
INSTALL_TIMEOUT_SECONDS = 360
PAUSE_SECONDS = 10


def manifest_matches(path: Path, package: str) -> bool:
    try:
        # Android SDK package.xml is local trusted setup metadata, not Office input.
        root = ET.parse(path).getroot()
        return any(node.tag.rsplit('}', 1)[-1] == 'localPackage' and node.get('path') == package
                   for node in root.iter())
    except (OSError, ET.ParseError):
        return False


def installed(root: Path) -> bool:
    image = root / 'system-images/android-34/google_apis/x86_64'
    emulator = root / 'emulator'
    return (manifest_matches(image / 'package.xml', IMAGE)
            and (image / 'system.img').is_file()
            and manifest_matches(emulator / 'package.xml', EMULATOR)
            and (emulator / 'emulator').is_file()
            and os.access(emulator / 'emulator', os.X_OK))


def ensure(root: Path, invoke=subprocess.run, pause=time.sleep) -> str:
    if installed(root):
        return 'already-installed-metadata-only'
    manager = root / 'cmdline-tools/latest/bin/sdkmanager'
    if not manager.is_file() or not os.access(manager, os.X_OK):
        raise RuntimeError('SDK manager is missing or not executable')
    for attempt in range(1, ATTEMPTS + 1):
        print(f'Preparing disposable API34 SDK components: install attempt {attempt}/{ATTEMPTS}.', flush=True)
        try:
            result = invoke([str(manager), '--install', EMULATOR, IMAGE], check=False,
                            timeout=INSTALL_TIMEOUT_SECONDS)
            status = result.returncode
        except subprocess.TimeoutExpired as exc:
            # A timed-out installer may have spawned children or left partial state.
            # Do not issue a second install against an uncertain SDK directory.
            raise RuntimeError('SDK manager timed out; install state is unknown and was not retried') from exc
        if status == 0 and installed(root):
            return 'installed-metadata-only'
        print(f'Install attempt {attempt} incomplete or failed (exit={status}); no device test ran.', flush=True)
        if attempt < ATTEMPTS:
            pause(PAUSE_SECONDS)
    raise RuntimeError('API34 managed SDK components remain incomplete after bounded install attempts')


def main() -> int:
    sdk = os.environ.get('ANDROID_HOME')
    if not sdk:
        print('ANDROID_HOME is required and must match the SDK used by Gradle.', file=sys.stderr)
        return 1
    try:
        result = ensure(Path(sdk))
    except (RuntimeError, OSError) as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f'API34 SDK preparation: {result}; boot and tests remain separate required gates.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
