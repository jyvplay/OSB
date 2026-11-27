#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Build and run the real offline diagnostic export CLI; no Android SDK needed.

python3 tools/diagnostic_export.py app,version records.txt
python3 tools/diagnostic_export.py app,version records.txt REVIEW_SHA256 new.zip
Requires the pinned host tools installed by bootstrap_host_tools.py install.
No input or archive is stored by this wrapper. Output destinations must be NEW.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from bootstrap_host_tools import environment

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ROOT / 'privacy/src/main/java/dev/fusion/privacy/DiagnosticExportAllowlist.kt',
    ROOT / 'privacy/src/main/java/dev/fusion/privacy/DiagnosticExportSession.kt',
    ROOT / 'tools/diagnostics/DiagnosticExportCli.kt',
]


def main():
    env = environment(Path.home() / '.cache/fusion-host-r23')
    with tempfile.TemporaryDirectory(prefix='fusion-diagnostics-') as temp:
        build = subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-d', temp,
                                *map(str, SOURCES)], env=env)
        if build.returncode:
            return build.returncode
        stdlib = Path(env['KOTLINC']).parent.parent / 'lib/kotlin-stdlib.jar'
        return subprocess.run([env['JAVA_HOME'] + '/bin/java', '-cp',
                               temp + os.pathsep + str(stdlib),
                               'dev.fusion.tools.DiagnosticExportCli', *sys.argv[1:]], env=env).returncode


if __name__ == '__main__':
    sys.exit(main())
