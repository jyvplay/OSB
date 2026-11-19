#!/usr/bin/env python3
"""R110 mutants: put each repaired site back and require the boundary gate to catch it.

The repair is two `val` lines per site. A repair that no gate can see is a claim, not a fix, so each site is reverted
here on its own, the gate is run against the reverted file, and the run must fail with the cross-module smart-cast
diagnostic. The file is restored byte-for-byte after each mutant (SHA-256 checked), so a run leaves no trace.

Committed with the round so the claim is reproducible from a checkout: it needs only the product jar that
`python3 tools/run_r110_checks.py --skip-lanes` writes and the pinned host kotlinc.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

PROBE = ROOT / 'testing/src/r93/kotlin/dev/fusion/testing/R93Checks.kt'
PRODUCT_JAR = ROOT / 'testing/build/r110/product.jar'
GATE = ROOT / 'tools/check_r110_probe_boundaries.py'

SITE_ONE_FIXED = r'''            for (column in filter.columns) {
                val values = column.values
                val custom = column.custom
                val kind: String
                val payload: String
                if (values != null) {
                    kind = "values"
                    payload = values.joinToString("\u001f")
                } else {
                    kind = "custom"
                    payload = (custom ?: emptyList()).joinToString("\u001f") { "${it.operator.token}=${it.value}" }
                }
                val join = when {
                    custom == null || custom.size == 1 -> "-"
'''

SITE_ONE_PRE_FIX = r'''            for (column in filter.columns) {
                val kind: String
                val payload: String
                if (column.values != null) {
                    kind = "values"
                    payload = column.values.joinToString("\u001f")
                } else {
                    kind = "custom"
                    payload = (column.custom ?: emptyList()).joinToString("\u001f") { "${it.operator.token}=${it.value}" }
                }
                val join = when {
                    column.custom == null || column.custom.size == 1 -> "-"
'''

SITE_TWO_FIXED = r'''            val values = column.values
            val custom = column.custom
            val id = "C${index + 1}"
            val kind = if (values != null) "values" else "custom"
            val payload = if (values != null) {
                values.joinToString("\u001f")
            } else {
                (custom ?: emptyList()).joinToString("\u001f") { "${it.operator.token}=${it.value}" }
            }
            val join = when {
                custom == null || custom.size == 1 -> "-"
'''

SITE_TWO_PRE_FIX = r'''            val id = "C${index + 1}"
            val kind = if (column.values != null) "values" else "custom"
            val payload = if (column.values != null) {
                column.values.joinToString("\u001f")
            } else {
                (column.custom ?: emptyList()).joinToString("\u001f") { "${it.operator.token}=${it.value}" }
            }
            val join = when {
                column.custom == null || column.custom.size == 1 -> "-"
'''

MUTANTS = [
    ('the first site reads the smart-cast property again', SITE_ONE_FIXED, SITE_ONE_PRE_FIX),
    ('the second site reads the smart-cast property again', SITE_TWO_FIXED, SITE_TWO_PRE_FIX),
]


def digest(text: str) -> str:
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def main() -> int:
    if not PRODUCT_JAR.is_file():
        raise SystemExit('run python3 tools/run_r110_checks.py --skip-lanes first (it builds the product jar)')
    original = PROBE.read_text(encoding='utf-8')
    before = digest(original)
    survived = []
    rows = []
    try:
        for name, fixed, pre_fix in MUTANTS:
            found = original.count(fixed)
            if found != 1:
                raise SystemExit(f'mutant {name!r}: the repaired text occurs {found} time(s), expected 1')
            PROBE.write_text(original.replace(fixed, pre_fix, 1), encoding='utf-8')
            completed = subprocess.run(
                [sys.executable, str(GATE), '--jar', str(PRODUCT_JAR), '--only', 'r93AutoFilter'],
                capture_output=True,
                text=True,
            )
            message = ''
            for line in (completed.stdout + completed.stderr).splitlines():
                if 'smart cast' in line:
                    message = line.split('smart cast', 1)[1].strip()
                    message = 'smart cast ' + message
                    break
            if completed.returncode == 0:
                print(f'SURVIVED {name}')
                survived.append(name)
                rows.append((name, 'SURVIVED'))
            else:
                print(f'caught   {name}: {message[:150]}')
                rows.append((name, message or 'the gate failed without a smart-cast diagnostic'))
            PROBE.write_text(original, encoding='utf-8')
            if digest(PROBE.read_text(encoding='utf-8')) != before:
                raise SystemExit(f'the probe was not restored after {name!r}')
    finally:
        PROBE.write_text(original, encoding='utf-8')
    after = digest(PROBE.read_text(encoding='utf-8'))
    if after != before:
        raise SystemExit('the probe file changed')
    out = ROOT / 'testing/build/r110'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'mutants.tsv').write_text(''.join(f'{name}\t{message}\n' for name, message in rows), encoding='utf-8')
    caught = len(MUTANTS) - len(survived)
    print(f'R110 mutants: {caught}/{len(MUTANTS)} caught, probe sha256 {after[:16]} restored')
    return 1 if survived else 0


if __name__ == '__main__':
    raise SystemExit(main())
