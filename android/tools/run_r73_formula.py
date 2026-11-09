#!/usr/bin/env python3
"""Run the R73 formula/oracle differential on the host JVM; this is not Android execution.

Three independent artifacts are produced and compared:
  * cases.tsv/expected.tsv from tools/r73_formula_cases.py (reimplemented SpreadsheetML semantics)
  * candidate.txt from the Kotlin engine through R73FormulaProbe
  * an optional third-engine differential through tools/check_r73_formula_reference.py
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

PROBE = 'dev.fusion.testing.R73FormulaProbe'


def compile_probe(env: dict[str, str], jar: Path) -> None:
    sources = sorted((ROOT / 'format/archive/src/main/kotlin/dev/fusion/archive').glob('*.kt'))
    sources += sorted((ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml').glob('*.kt'))
    sources.append(ROOT / 'testing/src/r73formula/kotlin/dev/fusion/testing/R73FormulaProbe.kt')
    jar.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-Werror', '-include-runtime',
                    *map(str, sources), '-d', str(jar)], check=True, env=env)


def main() -> int:
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    out = ROOT / 'testing/build/r73formula'
    out.mkdir(parents=True, exist_ok=True)
    python = sys.executable
    if not (out / 'cases.tsv').is_file():
        subprocess.run([python, str(ROOT / 'tools/r73_formula_cases.py'), 'build', str(out)], check=True)
    jar = out / 'r73-probe.jar'
    compile_probe(env, jar)
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    subprocess.run([java, '-Dfile.encoding=UTF-8', '-Xmx512m', '-cp', str(jar), PROBE, 'read', str(out)],
                   check=True, env=env, timeout=300)
    subprocess.run([python, str(ROOT / 'tools/r73_formula_cases.py'), 'verify', str(out)], check=True)
    subprocess.run([python, str(ROOT / 'tools/check_r73_formula_reference.py'), str(out)], check=True)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
