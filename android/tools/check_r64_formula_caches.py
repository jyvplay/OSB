#!/usr/bin/env python3
"""Differentially inspect copied-formula XLSX caches; Formualizer is not Excel."""
from pathlib import Path
import math

import check_r64_sheet_copy_reference as sheet

ERRORS = {'Div': '#DIV/0!', 'Value': '#VALUE!', 'Num': '#NUM!', 'Name': '#NAME?',
          'Ref': '#REF!', 'NA': '#N/A', 'Null': '#NULL!'}
# The independent engine refuses this reversed mixed-anchor range in four outputs.
# The app's bounded engine normalizes it; 5 is also the direct finite COUNT oracle
# for this fixed sheet. Do not count the refusal as engine agreement or Excel proof.
REVERSED_REFERENCE_REFUSALS = {f'result-{i:03}.xlsx' for i in range(224, 228)}
REVERSED_FORMULA = '=COUNT($A3:D$2)+$C$3'


def equal_typed(actual, reference):
    if isinstance(reference, dict) and reference.get('type') == 'Error':
        reference = ERRORS.get(reference.get('kind'), reference)
    if type(actual) in (int, float) and type(reference) in (int, float):
        return math.isclose(actual, reference, rel_tol=1e-12, abs_tol=1e-12)
    return type(actual) is type(reference) and actual == reference


def check(folder=sheet.ARTIFACTS):
    import openpyxl
    import formualizer
    folder = Path(folder)
    # Independently establish the fixed input/policy matrix and actual formula XML first.
    sheet.check(folder)
    matched = 0; refused = set()
    for path in sorted(folder.glob('result-*.xlsx')):
        formulas = openpyxl.load_workbook(path, data_only=False)
        caches = openpyxl.load_workbook(path, data_only=True)
        try:
            actual = formulas.active; cached = caches.active
            reference = formualizer.Workbook(); reference.add_sheet('Sheet1')
            for cell in actual._cells.values():
                if cell.data_type != 'f' and cell.value is not None:
                    reference.set_value('Sheet1', cell.row, cell.column, cell.value)
            for cell in actual._cells.values():
                if cell.data_type == 'f': reference.set_formula('Sheet1', cell.row, cell.column, cell.value)
            for cell in actual._cells.values():
                if cell.data_type != 'f': continue
                observed = cached[cell.coordinate].value
                try:
                    independently_computed = reference.evaluate_cell('Sheet1', cell.row, cell.column)
                except Exception as exc:
                    if not (path.name in REVERSED_REFERENCE_REFUSALS and cell.coordinate == 'O52' and
                            cell.value == REVERSED_FORMULA and type(exc).__name__ == 'ExcelEvaluationError' and str(exc) == '#REF!' and
                            type(observed) in (int, float) and observed == 5):
                        raise ValueError(f'Unexpected reference refusal: {path.name}/{cell.coordinate}') from exc
                    refused.add(path.name)
                    continue
                if not equal_typed(observed, independently_computed):
                    raise ValueError(f'Cached result differs from independent evaluation: {path.name}/{cell.coordinate}')
                matched += 1
        finally:
            formulas.close(); caches.close()
    if matched != 486 or refused != REVERSED_REFERENCE_REFUSALS:
        raise ValueError('Differential corpus or known reference-refusal set changed')
    return matched, len(refused)


if __name__ == '__main__':
    matches, refusals = check()
    print(f'R64 copied-formula caches: {matches} typed Formualizer matches; {refusals} reversed-range reference refusals (manual fixed-grid value checked); no Excel claim')
