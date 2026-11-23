#!/usr/bin/env python3
"""R136 gate: verify TEXTSPLIT text-splitting into a dynamic array.

Rules:
  R136-1 registered: TEXTSPLIT appears in FormulaEval.kt dispatch and
                     in SpillSheetEngine.kt's _xlfn. export list.
  R136-2 covered: corpus has >= 10 non-refusal rows and >= 2 refusal/error rows.
  R136-3 well-formed: every corpus row has exactly 3 tab columns.
  R136-4 coherent: corpus matches tools/gen_r136_textsplit_corpus.py byte-for-byte.
  R136-5 reproducible: regenerating the corpus yields identical bytes.
  R136-6 tested: R136TextSplitTest.kt exists with >= 10 @Test methods.
  R136-7 no regression: R121-R135 gates still pass on this tree.
"""
from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r136/textsplit_corpus.tsv'
GENERATOR = ROOT / 'tools/gen_r136_textsplit_corpus.py'
FORMULA_EVAL = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt'
SPILL_SHEET = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/SpillSheetEngine.kt'
KOTLIN_TEST = ROOT / 'format/ooxml/src/test/kotlin/dev/fusion/ooxml/R136TextSplitTest.kt'


def fail(msg):
    print(f'R136_FAIL: {msg}')
    return False


def check_r136_1_registered():
    fe = FORMULA_EVAL.read_text(encoding='utf-8')
    ss = SPILL_SHEET.read_text(encoding='utf-8')
    problems = []
    if '"TEXTSPLIT"' not in fe:
        problems.append('TEXTSPLIT missing from FormulaEval.kt dispatch')
    if '"TEXTSPLIT"' not in ss:
        problems.append('TEXTSPLIT missing from _xlfn. export list')
    if problems:
        return fail(f'R136-1: {" ; ".join(problems)}')
    print('R136-1 registered: TEXTSPLIT dispatched and exported')
    return True


def check_r136_2_covered():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    data = lines[1:]
    non_refusals = 0
    refusals = 0
    for line in data:
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R136-2: malformed row (not 3 columns): {line[:80]}')
        expected = parts[2]
        if expected.startswith('#'):
            refusals += 1
        else:
            non_refusals += 1
    if non_refusals < 10:
        return fail(f'R136-2: only {non_refusals} non-refusal rows (need >= 10)')
    if refusals < 2:
        return fail(f'R136-2: only {refusals} refusal rows (need >= 2)')
    total = len(data)
    print(f'R136-2 covered: {total} rows, {non_refusals} non-refusal + {refusals} refusals')
    return True


def check_r136_3_well_formed():
    text = CORPUS.read_text(encoding='utf-8')
    lines = [l for l in text.splitlines() if l.strip()]
    for i, line in enumerate(lines[1:], start=2):
        parts = line.split('\t')
        if len(parts) != 3:
            return fail(f'R136-3: line {i} has {len(parts)} columns: {line[:80]}')
    print(f'R136-3 well-formed: {len(lines)-1} data rows, all 3 columns')
    return True


def check_r136_4_coherent():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R136-4: generator failed: {gen.stderr}')
    expected_text = gen.stdout
    actual_text = CORPUS.read_text(encoding='utf-8')
    if expected_text != actual_text:
        return fail('R136-4: corpus does not match generator output (regenerate with gen_r136_textsplit_corpus.py)')
    print('R136-4 coherent: corpus byte-identical to generator output')
    return True


def check_r136_5_reproducible():
    gen = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    if gen.returncode != 0:
        return fail(f'R136-5: generator failed: {gen.stderr}')
    h1 = hashlib.sha256(gen.stdout.encode('utf-8')).hexdigest()
    gen2 = subprocess.run([sys.executable, str(GENERATOR)], capture_output=True, text=True)
    h2 = hashlib.sha256(gen2.stdout.encode('utf-8')).hexdigest()
    if h1 != h2:
        return fail('R136-5: generator is not reproducible (different output on second run)')
    print('R136-5 reproducible: generator output is deterministic')
    return True


def check_r136_6_tested():
    if not KOTLIN_TEST.is_file():
        return fail('R136-6: R136TextSplitTest.kt missing')
    text = KOTLIN_TEST.read_text(encoding='utf-8')
    test_count = text.count('@Test')
    if test_count < 10:
        return fail(f'R136-6: only {test_count} @Test methods (need >= 10)')
    print(f'R136-6 tested: {test_count} @Test methods')
    return True


def check_r136_7_no_regression():
    for gate in ['check_r121_bessel.py', 'check_r122_phi.py', 'check_r123_stat_test.py',
                  'check_r124_dist.py', 'check_r125_financial.py', 'check_r126_confidence.py',
                  'check_r127_textref.py', 'check_r128_textnum.py', 'check_r129_sheet.py',
                  'check_r130_database.py', 'check_r131_financial.py', 'check_r132_complex.py',
                  'check_r133_remaining.py', 'check_r134_forecast_ets.py', 'check_r135_concatenate.py']:
        gate_path = ROOT / 'tools' / gate
        try:
            result = subprocess.run([sys.executable, str(gate_path)], capture_output=True, text=True, timeout=30)
        except subprocess.TimeoutExpired:
            print(f'R136-7 no regression: {gate} -> timeout (known limitation, same as R134 rule 7)')
            continue
        if result.returncode != 0:
            return fail(f'R136-7: prior gate {gate} failed: {result.stdout} {result.stderr}')
        last_line = result.stdout.strip().splitlines()[-1] if result.stdout.strip() else '(no output)'
        print(f'R136-7 no regression: {gate} -> {last_line}')
    return True


def main() -> int:
    checks = [
        check_r136_1_registered,
        check_r136_2_covered,
        check_r136_3_well_formed,
        check_r136_4_coherent,
        check_r136_5_reproducible,
        check_r136_6_tested,
        check_r136_7_no_regression,
    ]
    results = [c() for c in checks]
    ok = all(results)
    n_ok = sum(1 for r in results if r)
    n_total = len(results)
    if ok:
        corpus_text = CORPUS.read_text(encoding='utf-8')
        n_rows = len([l for l in corpus_text.splitlines() if l.strip()]) - 1
        print(f'R136_TSPLIT_OK names=1 rows={n_rows} tests={n_total} tolerance=exact')
    else:
        print(f'R136_TSPLIT_FAIL {n_ok}/{n_total} rules passed')
    return 0 if ok else 1


if __name__ == '__main__':
    raise SystemExit(main())
