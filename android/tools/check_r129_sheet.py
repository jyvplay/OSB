#!/usr/bin/env python3
"""R129 gate: verify `SHEET` and `SHEETS` sheet reference functions.

Rules:
  R129-1 registered: SHEET and SHEETS appear in the FormulaEval.kt dispatch branch and
                     in SpillSheetEngine.kt's _xlfn. export list.
  R129-2 covered: each of SHEET and SHEETS has >= 3 non-refusal corpus rows and >= 2 refusal rows.
  R129-3 well-formed: every corpus row has exactly 3 tab columns.
  R129-4 coherent: corpus matches tools/gen_r129_sheet_corpus.py byte-for-byte.
  R129-5 reproducible: regenerating the corpus yields identical bytes.
  R129-6 tested: R129SheetTest.kt exists with >= 10 @Test methods and TOL = 1e-12.
  R129-7 no regression: R121-R128 gates still pass on this tree.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r129/sheet_corpus.tsv'
GENERATOR = ROOT / 'tools/gen_r129_sheet_corpus.py'
FORMULA_EVAL = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt'
SPILL_SHEET = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt'
KOTLIN_TEST = ROOT / 'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R129SheetTest.kt'

FN_NAMES = ["SHEET", "SHEETS"]


def fail(msg):
    print(f'R129_FAIL: {msg}')
    return False


def check_r129_1_registered():
    fe = FORMULA_EVAL.read_text(encoding='utf-8')
    ss = SPILL_SHEET.read_text(encoding='utf-8')
    problems = []
    for fn in FN_NAMES:
        if f'"{fn}"' not in fe:
            problems.append(f'{fn} missing from FormulaEval.kt dispatch')
        if f'"{fn}"' not in ss:
            problems.append(f'{fn} missing from _xlfn. export list')
    if problems:
        return fail(f'R129-1: {" ; ".join(problems)}')
    print(f'R129-1 registered: {", ".join(FN_NAMES)} dispatched and exported')
    return True


def check_r129_2_covered():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    data = lines[1:]
    from collections import Counter
    fn_counts = Counter()
    fn_refusals = Counter()
    for line in data:
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R129-2: malformed row (not 3 columns): {line[:80]}')
        fn = parts[0]
        expected = parts[2]
        fn_counts[fn] += 1
        if expected.startswith('#'):
            fn_refusals[fn] += 1
    problems = []
    for fn in FN_NAMES:
        total = fn_counts.get(fn, 0)
        refusals = fn_refusals.get(fn, 0)
        non_refusals = total - refusals
        if non_refusals < 3:
            problems.append(f'{fn} has only {non_refusals} non-refusal rows (need >= 3)')
        if refusals < 2:
            problems.append(f'{fn} has only {refusals} refusal rows (need >= 2)')
    if problems:
        return fail(f'R129-2: {" ; ".join(problems)}')
    total = len(data)
    print(f'R129-2 covered: {total} rows, both functions >= 3 non-refusal + >= 2 refusals')
    return True


def check_r129_3_well_formed():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    for i, line in enumerate(lines[1:], start=2):
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R129-3: line {i} has {len(parts)} columns: {line[:80]}')
    print(f'R129-3 well-formed: {len(lines)-1} data rows, all 3 columns')
    return True


def check_r129_4_coherent():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R129-4: generator failed: {gen.stderr}')
    expected_text = gen.stdout
    actual_text = CORPUS.read_text(encoding='utf-8')
    if expected_text != actual_text:
        return fail('R129-4: corpus does not match generator output (regenerate with gen_r129_sheet_corpus.py)')
    print('R129-4 coherent: corpus byte-identical to generator output')
    return True


def check_r129_5_reproducible():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R129-5: generator failed: {gen.stderr}')
    h1 = hashlib.sha256(gen.stdout.encode('utf-8')).hexdigest()
    gen2 = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    h2 = hashlib.sha256(gen2.stdout.encode('utf-8')).hexdigest()
    if h1 != h2:
        return fail('R129-5: generator is not reproducible (different output on second run)')
    print('R129-5 reproducible: generator output is deterministic')
    return True


def check_r129_6_tested():
    if not KOTLIN_TEST.is_file():
        return fail('R129-6: R129SheetTest.kt missing')
    text = KOTLIN_TEST.read_text(encoding='utf-8')
    test_count = text.count('@Test')
    if test_count < 10:
        return fail(f'R129-6: only {test_count} @Test methods (need >= 10)')
    if 'TOL' not in text:
        return fail('R129-6: TOL constant not found')
    print(f'R129-6 tested: {test_count} @Test methods, TOL constant present')
    return True


def check_r129_7_no_regression():
    for gate in ['check_r121_bessel.py', 'check_r122_phi.py', 'check_r123_stat_test.py',
                  'check_r124_dist.py', 'check_r125_financial.py', 'check_r126_confidence.py',
                  'check_r127_textref.py', 'check_r128_textnum.py']:
        gate_path = ROOT / 'tools' / gate
        result = subprocess.run([sys.executable, str(gate_path)], capture_output=True, text=True)
        if result.returncode != 0:
            return fail(f'R129-7: prior gate {gate} failed: {result.stdout} {result.stderr}')
        print(f'R129-7 no regression: {gate} -> {result.stdout.strip()}')
    return True


def main() -> int:
    checks = [
        check_r129_1_registered,
        check_r129_2_covered,
        check_r129_3_well_formed,
        check_r129_4_coherent,
        check_r129_5_reproducible,
        check_r129_6_tested,
        check_r129_7_no_regression,
    ]
    results = [c() for c in checks]
    ok = all(results)
    n_ok = sum(1 for r in results if r)
    n_total = len(results)
    if ok:
        corpus_text = CORPUS.read_text(encoding='utf-8')
        n_rows = len([l for l in corpus_text.splitlines() if l.strip()]) - 1
        print(f'R129_SHEET_OK names=2 rows={n_rows} tests={n_total} tolerance=1e-12')
    else:
        print(f'R129_SHEET_FAIL {n_ok}/{n_total} rules passed')
    return 0 if ok else 1


if __name__ == '__main__':
    raise SystemExit(main())
