#!/usr/bin/env python3
"""Run the R74 Writer hyperlink/page-break contracts and their independent verification.

The Kotlin side executes the production DOCX writer on the host JVM. The Python side opens the
same bytes with python-docx and inspects the raw OPC package. Neither is Android or Word
rendering evidence.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

MAIN = 'dev.fusion.testing.R74WriterLinkChecks'


def compile_checks(env: dict[str, str], jar: Path) -> None:
    sources = sorted((ROOT / 'format/archive/src/main/kotlin/dev/fusion/archive').glob('*.kt'))
    sources += sorted((ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml').glob('*.kt'))
    sources.append(ROOT / 'testing/src/r74writer/kotlin/dev/fusion/testing/R74WriterLinkChecks.kt')
    jar.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)


def main() -> int:
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    artifacts = ROOT / 'testing/build/r74writer/artifacts'
    artifacts.mkdir(parents=True, exist_ok=True)
    jar = ROOT / 'testing/build/r74writer/r74-checks.jar'
    compile_checks(env, jar)
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', str(jar), MAIN, str(artifacts)],
                   check=True, env=env, timeout=300)
    subprocess.run([sys.executable, str(ROOT / 'tools/check_r74_writer_links.py'), str(artifacts)],
                   check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
