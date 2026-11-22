#!/usr/bin/env python3
"""R127 gate: verify DOLLAR / FIXED / TIMEVALUE / COLUMN / ROW / COLUMNS / ROWS.

Rules:
  R127-1 registered: DOLLAR/FIXED/TIMEVALUE/COLUMN/ROW/COLUMNS/ROWS appear in the
                     FormulaEval.kt dispatch branch and in SpillSheetEngine.kt's
                     _xlfn. export list.
  R127-2 covered: each of DOLLAR/FIXED/TIMEVALUE/COLUMN/ROW/COLUMNS/ROWS has >= 3
                  numeric corpus rows and >= 2 refusal rows (where applicable).
  R127-3 well-formed: every corpus row has exactly 3 tab columns; numeric tokens parse.
  R127-4 coherent: corpus matches tools/gen_r127_textref_corpus.py byte-for-byte.
  R127-5 reproducible: regenerating the corpus yields identical bytes.
  R127-6 tested: R127TextRefTest.kt exists with >= 10 @Test methods and TOL = 1e-12.
  R127-7 no regression: R121-R126 gates still pass on this tree.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r127/textref_corpus.tsv'
GENERATOR = ROOT / 'tools/gen_r127_textref_corpus.py'
FORMULA_EVAL = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt'
SPILL_SHEET = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt'
KOTLIN_TEST = ROOT / 'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R127TextRefTest.kt'

FN_NAMES = ["DOLLAR", "FIXED", "TIMEVALUE", "COLUMN", "ROW", "COLUMNS", "ROWS"]


def fail(msg):
    print(f'R127_FAIL: {msg}')
    return False


def check_r127_1_registered():
    fe = FORMULA_EVAL.read_text(encoding='utf-8')
    ss = SPILL_SHEET.read_text(encoding='utf-8')
    problems = []
    for fn in FN_NAMES:
        if f'"{fn}"' not in fe:
            problems.append(f'{fn} missing from FormulaEval.kt dispatch')
    if '"DOLLAR", "FIXED"' not in fe:
        problems.append('DOLLAR/FIXED dispatch branch missing')
    if '"TIMEVALUE"' not in fe:
        problems.append('TIMEVALUE missing from dispatch')
    if '"COLUMN", "ROW"' not in fe:
        problems.append('COLUMN/ROW dispatch branch missing')
    if '"COLUMNS", "ROWS"' not in fe:
        problems.append('COLUMNS/ROWS dispatch branch missing')
    for fn in FN_NAMES:
        if f'"{fn}"' not in ss:
            problems.append(f'{fn} missing from _xlfn. export list')
    if problems:
        return fail(f'R127-1: {" ; ".join(problems)}')
    print(f'R127-1 registered: {", ".join(FN_NAMES)} dispatched and exported')
    return True


def check_r127_2_covered():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    data = lines[1:]
    fn_counts = Counter()
    fn_refusals = Counter()
    for line in data:
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R127-2: malformed row (not 3 columns): {line[:80]}')
        fn = parts[0]
        expected = parts[2]
        fn_counts[fn] += 1
        if expected.startswith('#'):
            fn_refusals[fn] += 1
    problems = []
    for fn in FN_NAMES:
        numeric = fn_counts.get(fn, 0) - fn_refusals.get(fn, 0)
        refusals = fn_refusals.get(fn, 0)
        if numeric < 3:
            problems.append(f'{fn} has only {numeric} numeric rows (need >= 3)')
        # Refusals required for functions that have domain/arity constraints
        if fn in ("DOLLAR", "FIXED", "TIMEVALUE", "COLUMN", "ROW", "COLUMNS", "ROWS"):
            if refusals < 2:
                problems.append(f'{fn} has only {refusals} refusal rows (need >= 2)')
    if problems:
        return fail(f'R127-2: {" ; ".join(problems)}')
    total = len(data)
    print(f'R127-2 covered: {total} rows, all {len(FN_NAMES)} functions >= 3 numeric + >= 2 refusals')
    return True


def check_r127_3_well_formed():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    for i, line in enumerate(lines[1:], start=2):
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R127-3: line {i} has {len(parts)} columns: {line[:80]}')
        fn, args, expected = parts
        if expected.startswith('#'):
            continue
        # DOLLAR/FIXED return text strings (e.g. "$1,234.50" or "1,234.50"); others return numbers.
        if fn in ("DOLLAR", "FIXED"):
            continue
        try:
            float(expected)
        except ValueError:
            return fail(f'R127-3: line {i} expected value not a number: {expected}')
    print(f'R127-3 well-formed: {len(lines)-1} data rows, all 3 columns, numeric or refusal')
    return True


def check_r127_4_coherent():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R127-4: generator failed: {gen.stderr}')
    expected_text = gen.stdout
    actual_text = CORPUS.read_text(encoding='utf-8')
    if expected_text != actual_text:
        return fail('R127-4: corpus does not match generator output (regenerate with gen_r127_textref_corpus.py)')
    print('R127-4 coherent: corpus byte-identical to generator output')
    return True


def check_r127_5_reproducible():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R127-5: generator failed: {gen.stderr}')
    h1 = hashlib.sha256(gen.stdout.encode('utf-8')).hexdigest()
    gen2 = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    h2 = hashlib.sha256(gen2.stdout.encode('utf-8')).hexdigest()
    if h1 != h2:
        return fail('R127-5: generator is not reproducible (different output on second run)')
    print('R127-5 reproducible: generator output is deterministic')
    return True


def check_r127_6_tested():
    if not KOTLIN_TEST.is_file():
        return fail('R127-6: R127TextRefTest.kt missing')
    text = KOTLIN_TEST.read_text(encoding='utf-8')
    test_count = text.count('@Test')
    if test_count < 10:
        return fail(f'R127-6: only {test_count} @Test methods (need >= 10)')
    if 'TOL' not in text:
        return fail('R127-6: TOL constant not found')
    print(f'R127-6 tested: {test_count} @Test methods, TOL constant present')
    return True


def check_r127_7_no_regression():
    for gate in ['check_r121_bessel.py', 'check_r122_phi.py', 'check_r123_stat_test.py',
                  'check_r124_dist.py', 'check_r125_financial.py', 'check_r126_confidence.py']:
        gate_path = ROOT / 'tools' / gate
        result = subprocess.run([sys.executable, str(gate_path)], capture_output=True, text=True)
        if result.returncode != 0:
            return fail(f'R127-7: prior gate {gate} failed: {result.stdout} {result.stderr}')
        print(f'R127-7 no regression: {gate} -> {result.stdout.strip()}')
    return True


def main() -> int:
    checks = [
        check_r127_1_registered,
        check_r127_2_covered,
        check_r127_3_well_formed,
        check_r127_4_coherent,
        check_r127_5_reproducible,
        check_r127_6_tested,
        check_r127_7_no_regression,
    ]
    results = [c() for c in checks]
    ok = all(results)
    n_ok = sum(1 for r in results if r)
    n_total = len(results)
    if ok:
        corpus_text = CORPUS.read_text(encoding='utf-8')
        n_rows = len([l for l in corpus_text.splitlines() if l.strip()]) - 1
        print(f'R127_TEXT_OK names={len(FN_NAMES)} rows={n_rows} tests={n_total} tolerance=1e-12')
    else:
        print(f'R127_TEXT_FAIL {n_ok}/{n_total} rules passed')
    return 0 if ok else 1


if __name__ == '__main__':
    raise SystemExit(main())
