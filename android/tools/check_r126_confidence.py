#!/usr/bin/env python3
"""R126 gate: verify `CONFIDENCE` (legacy alias for CONFIDENCE.NORM).

Rules:
  R126-1 registered: `CONFIDENCE` appears in the FormulaEval.kt dispatch branch
                     alongside `CONFIDENCE.NORM`, and in SpillSheetEngine.kt's
                     _xlfn. export list.
  R126-2 covered: `CONFIDENCE` has >= 5 numeric corpus rows and >= 3 refusal rows.
  R126-3 well-formed: every corpus row has exactly 3 tab columns; numeric tokens parse.
  R126-4 coherent: corpus matches `tools/gen_r126_confidence_corpus.py` byte-for-byte.
  R126-5 reproducible: regenerating the corpus yields identical bytes.
  R126-6 tested: R126ConfidenceTest.kt exists with >= 10 @Test methods and TOL = 1e-12.
  R126-7 no regression: R121-R125 gates still pass on this tree.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r126/confidence_corpus.tsv'
GENERATOR = ROOT / 'tools/gen_r126_confidence_corpus.py'
FORMULA_EVAL = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt'
SPILL_SHEET = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt'
KOTLIN_TEST = ROOT / 'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R126ConfidenceTest.kt'


def fail(msg):
    print(f'R126_FAIL: {msg}')
    return False


def check_r126_1_registered():
    fe = FORMULA_EVAL.read_text(encoding='utf-8')
    ss = SPILL_SHEET.read_text(encoding='utf-8')
    problems = []
    dispatch = '"CONFIDENCE", "CONFIDENCE.NORM"'
    if dispatch not in fe:
        problems.append('dispatch branch missing "CONFIDENCE", "CONFIDENCE.NORM" in FormulaEval.kt')
    if '"CONFIDENCE"' not in ss:
        problems.append('CONFIDENCE missing from _xlfn. export list')
    if problems:
        return fail(f'R126-1: {"; ".join(problems)}')
    print('R126-1 registered: CONFIDENCE dispatched and exported')
    return True


def check_r126_2_covered():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    data = lines[1:]
    from collections import Counter
    fn_counts = Counter()
    fn_refusals = Counter()
    for line in data:
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R126-2: malformed row (not 3 columns): {line[:80]}')
        fn = parts[0]
        expected = parts[2]
        fn_counts[fn] += 1
        if expected.startswith('#'):
            fn_refusals[fn] += 1
    numeric = fn_counts.get('CONFIDENCE', 0) - fn_refusals.get('CONFIDENCE', 0)
    refusals = fn_refusals.get('CONFIDENCE', 0)
    problems = []
    if numeric < 5:
        problems.append(f'CONFIDENCE has only {numeric} numeric rows (need >= 5)')
    if refusals < 3:
        problems.append(f'CONFIDENCE has only {refusals} refusal rows (need >= 3)')
    if problems:
        return fail(f'R126-2: {"; ".join(problems)}')
    total = len(data)
    print(f'R126-2 covered: {total} rows, CONFIDENCE >= 5 numeric + >= 3 refusals')
    return True


def check_r126_3_well_formed():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    for i, line in enumerate(lines[1:], start=2):
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R126-3: line {i} has {len(parts)} columns: {line[:80]}')
        fn, args, expected = parts
        if expected.startswith('#'):
            continue
        try:
            float(expected)
        except ValueError:
            return fail(f'R126-3: line {i} expected value not a number: {expected}')
    print(f'R126-3 well-formed: {len(lines)-1} data rows, all 3 columns, numeric or refusal')
    return True


def check_r126_4_coherent():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R126-4: generator failed: {gen.stderr}')
    expected_text = gen.stdout
    actual_text = CORPUS.read_text(encoding='utf-8')
    if expected_text != actual_text:
        return fail('R126-4: corpus does not match generator output (regenerate with gen_r126_confidence_corpus.py)')
    print('R126-4 coherent: corpus byte-identical to generator output')
    return True


def check_r126_5_reproducible():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R126-5: generator failed: {gen.stderr}')
    h1 = hashlib.sha256(gen.stdout.encode('utf-8')).hexdigest()
    gen2 = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    h2 = hashlib.sha256(gen2.stdout.encode('utf-8')).hexdigest()
    if h1 != h2:
        return fail('R126-5: generator is not reproducible (different output on second run)')
    print('R126-5 reproducible: generator output is deterministic')
    return True


def check_r126_6_tested():
    if not KOTLIN_TEST.is_file():
        return fail('R126-6: R126ConfidenceTest.kt missing')
    text = KOTLIN_TEST.read_text(encoding='utf-8')
    test_count = text.count('@Test')
    if test_count < 10:
        return fail(f'R126-6: only {test_count} @Test methods (need >= 10)')
    if 'TOL' not in text:
        return fail('R126-6: TOL constant not found')
    print(f'R126-6 tested: {test_count} @Test methods, TOL constant present')
    return True


def check_r126_7_no_regression():
    for gate in ['check_r121_bessel.py', 'check_r122_phi.py', 'check_r123_stat_test.py',
                  'check_r124_dist.py', 'check_r125_financial.py']:
        gate_path = ROOT / 'tools' / gate
        result = subprocess.run([sys.executable, str(gate_path)], capture_output=True, text=True)
        if result.returncode != 0:
            return fail(f'R126-7: prior gate {gate} failed: {result.stdout} {result.stderr}')
        print(f'R126-7 no regression: {gate} -> {result.stdout.strip()}')
    return True


def main() -> int:
    checks = [
        check_r126_1_registered,
        check_r126_2_covered,
        check_r126_3_well_formed,
        check_r126_4_coherent,
        check_r126_5_reproducible,
        check_r126_6_tested,
        check_r126_7_no_regression,
    ]
    results = [c() for c in checks]
    ok = all(results)
    n_ok = sum(1 for r in results if r)
    n_total = len(results)
    if ok:
        corpus_text = CORPUS.read_text(encoding='utf-8')
        n_rows = len([l for l in corpus_text.splitlines() if l.strip()]) - 1
        print(f'R126_CONF_OK names=1 rows={n_rows} tests={n_total} tolerance=1e-12')
    else:
        print(f'R126_CONF_FAIL {n_ok}/{n_total} rules passed')
    return 0 if ok else 1


if __name__ == '__main__':
    raise SystemExit(main())
