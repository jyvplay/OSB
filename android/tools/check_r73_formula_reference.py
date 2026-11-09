#!/usr/bin/env python3
"""Second, third-party differential for R73 formulas using formualizer 0.13.0.

Formualizer is a separate formula engine (Rust/Arrow), not Excel or LibreOffice.
Cases it cannot represent (date objects, unsupported functions) are counted as
unsupported, never as matches. Declared differences are enumerated explicitly so
a new disagreement cannot slip into the "known" bucket.
"""
from __future__ import annotations

import argparse
import datetime as dt
import importlib.metadata
import sys
from pathlib import Path

NUMBERS = {
    'A1': 3.0, 'A2': -2.0, 'B1': 1.0, 'B2': 2.0, 'B3': 2.0, 'B4': 3.0, 'B5': 3.0, 'B6': 3.0,
    'C1': 10.0, 'C2': 20.0, 'C3': 30.0, 'C4': 40.0, 'C5': 50.0, 'C6': 60.0,
    'E1': 44927.0, 'E2': 44928.0, 'E3': 44957.0, 'E4': 43831.0,
    'E5': 44927.0, 'E6': 44562.0, 'E7': 44197.0, 'E8': 45292.0,
    'F1': 0.75, 'F2': 0.5, 'F3': 0.0000001,
}
TEXTS = {
    'A3': '5', 'A6': 'Hello', 'A7': '  spaced  ', 'A8': '1,234.5',
    'A9': '2023-01-01', 'A10': '50%', 'A11': '(12)',
    'D1': 'a', 'D2': 'b', 'D3': 'b', 'D4': 'c', 'D5': 'c',
    'H1': 'apple', 'H2': 'Apple pie', 'H3': 'a*b', 'H4': 'a?c',
}
BOOLS = {'A4': True, 'G1': False}
EPOCH = dt.date(1899, 12, 30)

# Formula families formualizer 0.13.0 does not evaluate the way SpreadsheetML defines them,
# or represents as date/time objects rather than serial numbers.
SKIP_ID_PREFIXES = (
    'value-', 'text-case-', 'text-x-', 'text-nested', 'text-text-value', 'text-at-format',
    'datevalue-', 'time-', 'weeknum-', 'weeknum-x-', 'days-fractional',
    'norm-inv-x', 'composition-text',
    'grid-',  # dynamic-array results: formualizer reports only the top-left cell
)
DECLARED_DIFFERENCES = {
    # formualizer 0.13.0 limitations confirmed by direct probe on 2026-10-29:
    'roundup-zero-digits',   # formualizer rejects the single-argument ROUNDUP form
    'type-error',            # formualizer propagates the error instead of returning type 16
    'binom-inv-p0',          # degenerate p=0: CDF is 1 at k=0, so 0 is the smallest k
    # SpreadsheetML/XLSX behaviour we implement deliberately and formualizer does not:
    'log-base-one',          # Excel documents #DIV/0! for LOG with base 1
    'replace-beyond',        # REPLACE past the end appends the new text
    'search-empty',          # SEARCH with empty needle returns start_num
    'text-bad-format',       # TEXT refuses unimplemented fraction formats with #VALUE!
    'lookup-shape-mismatch', # LOOKUP requires equal-shape vectors and yields #N/A otherwise
    'yearfrac-multi',        # basis 1 across years uses the average-year denominator
    'agg-stdev',             # AGGREGATE function 8 is STDEV.S; formualizer returns the population value
    'agg-varp',              # AGGREGATE function 10 is VAR.P; formualizer returns the sample value
    'isref-range-multi',     # ISREF over a multi-cell range is FALSE in SpreadsheetML
}


def reference_value(module, formula: str, cells: dict[str, tuple[str, object]]) -> object:
    workbook = module.Workbook()
    workbook.add_sheet('Sheet1')
    for ref, (kind, value) in cells.items():
        column = ord(ref[0]) - ord('A') + 1
        row = int(ref[1:])
        workbook.set_value('Sheet1', row, column, value)
    workbook.set_formula('Sheet1', 1, 20, formula)
    return workbook.evaluate_cell('Sheet1', 1, 20)


def normalize(value) -> tuple[str, str] | None:
    if isinstance(value, bool):
        return ('B', 'true' if value else 'false')
    if isinstance(value, (int, float)):
        return ('N', repr(float(value)))
    if isinstance(value, str):
        return ('T', value)
    if isinstance(value, dt.datetime) or isinstance(value, dt.date):
        return ('DATE', value.isoformat())
    if isinstance(value, dict):
        kind = value.get('type')
        if kind == 'Error':
            literals = {'Div': '#DIV/0!', 'Value': '#VALUE!', 'Num': '#NUM!', 'Name': '#NAME?',
                        'Ref': '#REF!', 'NA': '#N/A', 'Na': '#N/A', 'Null': '#NULL!'}
            literal = literals.get(value.get('kind', ''))
            return None if literal is None else ('E', literal)
        if kind in ('Text', 'Boolean', 'Number'):
            inner = value.get('value')
            return normalize(inner)
        return None
    return None


def observations(path: Path) -> dict[str, str]:
    seen = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line:
            continue
        fields = line.split('|')
        if len(fields) != 3:
            raise ValueError('malformed observation ' + line)
        seen[fields[0]] = fields[1]
    return seen


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--require-reference', action='store_true')
    args = parser.parse_args()
    try:
        import formualizer
    except ImportError:
        if args.require_reference:
            raise
        print('Formualizer unavailable: R73 differential was NOT run.')
        return 0
    version = importlib.metadata.version('formualizer')
    if args.require_reference and version != '0.13.0':
        raise SystemExit('unexpected formualizer version ' + version)

    cells: dict[str, tuple[str, object]] = {}
    for ref, value in NUMBERS.items():
        cells[ref] = ('n', value)
    for ref, value in TEXTS.items():
        cells[ref] = ('s', value)
    for ref, value in BOOLS.items():
        cells[ref] = ('b', value)

    cases = [line.split('\t') for line in (args.directory / 'cases.tsv').read_text(encoding='utf-8').splitlines() if line]
    seen = observations(args.directory / 'candidate.txt')
    compared = 0
    compared_ids = set()
    unsupported = 0
    differences = set()
    unexpected = []
    for case_id, formula in cases:
        if case_id.startswith(SKIP_ID_PREFIXES):
            unsupported += 1
            continue
        try:
            raw = reference_value(formualizer, formula, cells)
        except Exception:
            unsupported += 1
            continue
        normalized = normalize(raw)
        if normalized is None or normalized[0] == 'DATE':
            unsupported += 1
            continue
        compared += 1
        compared_ids.add(case_id)
        kind, value = normalized
        actual = seen.get(case_id, '')
        if kind == 'N':
            ok = actual.startswith('N:') and abs(float(actual[2:]) - float(value)) <= 1e-9 * max(1.0, abs(float(value)))
        elif kind in ('T', 'B', 'E'):
            ok = actual == f'{kind}:{value}'
        else:
            ok = False
        if ok:
            continue
        if case_id in DECLARED_DIFFERENCES:
            differences.add(case_id)
            continue
        unexpected.append((case_id, formula, actual, normalized))
    expected_differences = DECLARED_DIFFERENCES.intersection(compared_ids)
    if differences != expected_differences:
        missing = expected_differences.difference(differences)
        raise SystemExit('declared formualizer differences no longer reproduce: ' + ', '.join(sorted(missing)))
    if unexpected:
        for row in unexpected[:20]:
            print('DIFFERENCE', row)
        raise SystemExit(f'{len(unexpected)} unexpected formualizer differences')
    print(f'R73 formualizer differential: {compared} compared, {len(differences)} declared differences, '
          f'{unsupported} unsupported/not-representable; 0 unexpected. Not Excel/LibreOffice evidence.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
