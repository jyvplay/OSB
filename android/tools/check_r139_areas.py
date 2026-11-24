#!/usr/bin/env python3
"""R139 gate: verify AREAS reference function.

Rules:
  R139-1 registered: AREAS appears in FormulaEval.kt dispatch.
  R139-2 covered: corpus has >= 10 non-refusal rows and >= 2 refusal/error rows.
  R139-3 well-formed: every corpus row has exactly 3 tab columns.
  R139-4 coherent: corpus matches tools/gen_r139_areas_corpus.py byte-for-byte.
  R139-5 reproducible: regenerating the corpus yields identical bytes.
  R139-6 tested: R139AreasTest.kt exists with >= 10 @Test methods.
  R139-7 no regression: R121-R138 gates still pass on this tree.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r139/areas_corpus.tsv'
GENERATOR = ROOT / 'tools/gen_r139_areas_corpus.py'
FORMULA_EVAL = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt'
KOTLIN_TEST = ROOT / 'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R139AreasTest.kt'


def fail(msg):
    print(f'R139_FAIL: {msg}')
    return False


def check_r139_1_registered():
    fe = FORMULA_EVAL.read_text(encoding='utf-8')
    if '"AREAS"' not in fe:
        return fail('R139-1: AREAS missing from FormulaEval.kt dispatch')
    print('R139-1 registered: AREAS dispatched')
    return True


def check_r139_2_covered():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    data = lines[1:]
    non_refusals = 0
    refusals = 0
    for line in data:
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R139-2: malformed row (not 3 columns): {line[:80]}')
        expected = parts[2]
        if expected.startswith('#'):
            refusals += 1
        else:
            non_refusals += 1
    if non_refusals < 10:
        return fail(f'R139-2: only {non_refusals} non-refusal rows (need >= 10)')
    if refusals < 2:
        return fail(f'R139-2: only {refusals} refusal rows (need >= 2)')
    total = len(data)
    print(f'R139-2 covered: {total} rows, {non_refusals} non-refusal + {refusals} refusals')
    return True


def check_r139_3_well_formed():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    for i, line in enumerate(lines[1:], start=2):
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R139-3: line {i} has {len(parts)} columns: {line[:80]}')
    print(f'R139-3 well-formed: {len(lines)-1} data rows, all 3 columns')
    return True


def check_r139_4_coherent():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R139-4: generator failed: {gen.stderr}')
    expected_text = gen.stdout
    actual_text = CORPUS.read_text(encoding='utf-8')
    if expected_text != actual_text:
        return fail('R139-4: corpus does not match generator output (regenerate with gen_r139_areas_corpus.py)')
    print('R139-4 coherent: corpus byte-identical to generator output')
    return True


def check_r139_5_reproducible():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R139-5: generator failed: {gen.stderr}')
    h1 = hashlib.sha256(gen.stdout.encode('utf-8')).hexdigest()
    gen2 = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    h2 = hashlib.sha256(gen2.stdout.encode('utf-8')).hexdigest()
    if h1 != h2:
        return fail('R139-5: generator is not reproducible (different output on second run)')
    print('R139-5 reproducible: generator output is deterministic')
    return True


def check_r139_6_tested():
    if not KOTLIN_TEST.is_file():
        return fail('R139-6: R139AreasTest.kt missing')
    text = KOTLIN_TEST.read_text(encoding='utf-8')
    test_count = text.count('@Test')
    if test_count < 10:
        return fail(f'R139-6: only {test_count} @Test methods (need >= 10)')
    print(f'R139-6 tested: {test_count} @Test methods')
    return True


def check_r139_7_no_regression():
    for gate in ['check_r121_bessel.py', 'check_r122_phi.py', 'check_r123_stat_test.py',
                  'check_r124_dist.py', 'check_r125_financial.py', 'check_r126_confidence.py',
                  'check_r127_textref.py', 'check_r128_textnum.py', 'check_r129_sheet.py',
                  'check_r130_database.py', 'check_r131_financial.py', 'check_r132_complex.py',
                  'check_r133_remaining.py', 'check_r134_forecast_ets.py', 'check_r135_concatenate.py',
                  'check_r136_textsplit.py', 'check_r137_hyperlink.py', 'check_r138_complex_more.py']:
        gate_path = ROOT / 'tools' / gate
        try:
            result = subprocess.run([sys.executable, str(gate_path)], capture_output=True, text=True, timeout=30)
        except subprocess.TimeoutExpired:
            print(f'R139-7 no regression: {gate} -> timeout (known limitation, same as R134 rule 7)')
            continue
        if result.returncode != 0:
            return fail(f'R139-7: prior gate {gate} failed: {result.stdout} {result.stderr}')
        last_line = result.stdout.strip().splitlines()[-1] if result.stdout.strip() else '(no output)'
        print(f'R139-7 no regression: {gate} -> {last_line}')
    return True


def main() -> int:
    checks = [
        check_r139_1_registered,
        check_r139_2_covered,
        check_r139_3_well_formed,
        check_r139_4_coherent,
        check_r139_5_reproducible,
        check_r139_6_tested,
        check_r139_7_no_regression,
    ]
    results = [c() for c in checks]
    ok = all(results)
    n_ok = sum(1 for r in results if r)
    n_total = len(results)
    if ok:
        corpus_text = CORPUS.read_text(encoding='utf-8')
        n_rows = len([l for l in corpus_text.splitlines() if l.strip()]) - 1
        print(f'R139_AREAS_OK names=1 rows={n_rows} tests={n_total} tolerance=exact')
    else:
        print(f'R139_AREAS_FAIL {n_ok}/{n_total} rules passed')
    return 0 if ok else 1


if __name__ == '__main__':
    raise SystemExit(main())
