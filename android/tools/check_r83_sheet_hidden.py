#!/usr/bin/env python3
"""Independent verification for the R83 hidden-dimension workbooks.

`XlsxStyledBook` may declare `hiddenRows` / `hiddenColumns`, which become `hidden="1"` on `<row>`
(inside `sheetData`) and `<col>` (inside `cols`). This verifier opens every workbook twice:

1. **raw OPC** — every worksheet part is parsed directly: the row set is exactly the declared cell
   rows united with the hidden-only rows (a hidden row never invents cells), `hidden="1"` appears on
   exactly the declared rows and columns, `<col min=max>` entries are ascending and unique, a width
   is present exactly where declared, and no other column attribute is introduced.
2. **openpyxl 3.1.5** — an independent implementation exposing `row_dimensions[n].hidden`,
   `column_dimensions[c].hidden`, `max_row` and the occupied cells.

Host-only. openpyxl is a reader, not Excel: agreement here is package-structure evidence, never
recalculation or rendering.
"""
from __future__ import annotations

import argparse
import sys
import warnings
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
INTENT_FIELDS = 8
TOLERANCE = 1e-9


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def ints(text: str) -> list[int]:
    return [] if text == '-' else [int(value) for value in text.split(',')]


def widths(text: str) -> dict[int, float]:
    if text == '-':
        return {}
    result = {}
    for item in text.split(','):
        key, _, value = item.partition(':')
        result[int(key)] = float(value)
    return result


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        table.setdefault(fields[0], []).append({
            'sheet': int(fields[1]),
            'name': fields[2],
            'hidden_rows': ints(fields[3]),
            'hidden_columns': ints(fields[4]),
            'widths': widths(fields[5]),
            'cell_rows': ints(fields[6]),
            'cells': int(fields[7]),
        })
    require(table, 'intent table is empty')
    return table


def column_label(index: int) -> str:
    label = ''
    while index > 0:
        index, remainder = divmod(index - 1, 26)
        label = chr(ord('A') + remainder) + label
    return label


def check_part(part: str, intent: dict) -> None:
    root = ElementTree.fromstring(part)
    data = root.find(f'{S}sheetData')
    require(data is not None, f'{intent["name"]}: worksheet has no sheetData')
    rows = [child for child in data if child.tag == f'{S}row']
    numbers = []
    for row in rows:
        raw = row.get('r')
        require(raw is not None, f'{intent["name"]}: a row has no r attribute')
        numbers.append(int(raw))
    require(numbers == sorted(numbers), f'{intent["name"]}: rows are not in ascending order: {numbers}')
    require(len(numbers) == len(set(numbers)), f'{intent["name"]}: duplicate row numbers: {numbers}')
    expected_rows = set(intent['cell_rows']) | set(intent['hidden_rows'])
    require(set(numbers) == expected_rows,
            f'{intent["name"]}: row set {sorted(numbers)} != declared {sorted(expected_rows)}')
    hidden = set(intent['hidden_rows'])
    for row, number in zip(rows, numbers):
        if number in hidden:
            require(row.get('hidden') == '1',
                    f'{intent["name"]}: row {number} is declared hidden but carries hidden={row.get("hidden")!r}')
        else:
            require(row.get('hidden') is None,
                    f'{intent["name"]}: row {number} carries hidden but is not declared hidden')

    columns = root.find(f'{S}cols')
    declared_hidden = set(intent['hidden_columns'])
    declared_widths = intent['widths']
    if columns is None:
        require(not declared_hidden and not declared_widths,
                f'{intent["name"]}: columns are declared but the sheet has no <cols>')
        return
    entries = [child for child in columns if child.tag == f'{S}col']
    mins = []
    for column in entries:
        raw = column.get('min')
        require(raw is not None, f'{intent["name"]}: a <col> has no min')
        mins.append(int(raw))
        require(column.get('max') == raw,
                f'{intent["name"]}: <col min={raw}> declares max={column.get("max")} but this writer emits '
                f'one column per entry')
        number = int(raw)
        if number in declared_hidden:
            require(column.get('hidden') == '1',
                    f'{intent["name"]}: column {number} is declared hidden but carries no hidden attribute')
        else:
            require(column.get('hidden') is None,
                    f'{intent["name"]}: column {number} carries hidden but is not declared hidden')
        width = declared_widths.get(number)
        if width is None:
            require(column.get('width') is None,
                    f'{intent["name"]}: column {number} declares a width that the intent does not')
            require(column.get('customWidth') is None,
                    f'{intent["name"]}: column {number} declares customWidth without a width')
        else:
            require(column.get('width') is not None,
                    f'{intent["name"]}: column {number} is missing its declared width')
            require(abs(float(column.get('width')) - width) <= TOLERANCE,
                    f'{intent["name"]}: column {number} width {column.get("width")} != declared {width}')
            require(column.get('customWidth') == '1',
                    f'{intent["name"]}: column {number} width without customWidth="1"')
        extra = set(column.attrib) - {'min', 'max', 'width', 'customWidth', 'hidden'}
        require(not extra, f'{intent["name"]}: column {number} carries undeclared attributes {sorted(extra)}')
    require(mins == sorted(mins), f'{intent["name"]}: <col> entries are not ascending: {mins}')
    require(len(mins) == len(set(mins)), f'{intent["name"]}: duplicate <col> entries: {mins}')
    require(set(mins) == set(declared_hidden) | set(declared_widths),
            f'{intent["name"]}: <col> set {mins} != declared {sorted(set(declared_hidden) | set(declared_widths))}')


def check_with_openpyxl(path: Path, intent: list[dict]) -> None:
    import openpyxl  # imported lazily so the raw-OPC checks can run without it

    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        workbook = openpyxl.load_workbook(str(path))
    require(len(workbook.worksheets) == len(intent),
            f'{path.name}: openpyxl sees {len(workbook.worksheets)} sheets, declared {len(intent)}')
    for sheet, expected in zip(workbook.worksheets, intent):
        require(sheet.title == expected['name'],
                f'{path.name}: sheet title {sheet.title!r} != declared {expected["name"]!r}')
        hidden_rows = {int(number) for number, dimension in sheet.row_dimensions.items()
                       if dimension.hidden}
        require(hidden_rows == set(expected['hidden_rows']),
                f'{path.name}/{sheet.title}: openpyxl hidden rows {sorted(hidden_rows)} != declared '
                f'{sorted(expected["hidden_rows"])}')
        expected_hidden_labels = {column_label(index) for index in expected['hidden_columns']}
        actual_hidden_labels = set()
        for letter, dimension in sheet.column_dimensions.items():
            if dimension.hidden:
                actual_hidden_labels.add(letter)
        require(actual_hidden_labels == expected_hidden_labels,
                f'{path.name}/{sheet.title}: openpyxl hidden columns {sorted(actual_hidden_labels)} != '
                f'declared {sorted(expected_hidden_labels)}')
        occupied = sum(1 for row in sheet.iter_rows() for cell in row if cell.value is not None)
        require(occupied == expected['cells'],
                f'{path.name}/{sheet.title}: openpyxl counts {occupied} non-empty cells, declared '
                f'{expected["cells"]}')
        require(sheet.max_row == (max(expected['cell_rows']) if expected['cell_rows'] else 1),
                f'{path.name}/{sheet.title}: openpyxl max_row {sheet.max_row} != declared '
                f'{max(expected["cell_rows"]) if expected["cell_rows"] else 1} (hiding a row must not '
                f'invent cells)')
        for index, width in expected['widths'].items():
            actual = sheet.column_dimensions[column_label(index)].width
            require(actual is not None and abs(float(actual) - width) <= TOLERANCE,
                    f'{path.name}/{sheet.title}: column {column_label(index)} width {actual} != {width}')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'hidden.tsv')
    sheets = 0
    for name, expected in intent.items():
        path = directory / f'{name}.xlsx'
        require(path.is_file(), f'missing workbook {path}')
        with zipfile.ZipFile(path) as archive:
            for entry in expected:
                part = f'xl/worksheets/sheet{entry["sheet"] + 1}.xml'
                require(part in archive.namelist(), f'{path.name}: missing {part}')
                check_part(archive.read(part).decode('utf-8'), entry)
        check_with_openpyxl(path, expected)
        sheets += len(expected)
    extra = {path.stem for path in directory.glob('*.xlsx')} - set(intent)
    require(not extra, f'unexpected workbooks without a declared intent: {sorted(extra)}')
    return {'workbooks': len(intent), 'sheets': sheets}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true',
                        help='fail instead of downgrading when openpyxl is unavailable')
    args = parser.parse_args(argv)
    try:
        import openpyxl  # noqa: F401
    except Exception as error:
        if args.require_openpyxl:
            print(f'R83 SHEET HIDDEN CHECK FAILED: openpyxl unavailable: {error}', file=sys.stderr)
            return 1
        print(f'openpyxl unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R83 SHEET HIDDEN CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R83 hidden dimensions: {report['workbooks']} workbooks and {report['sheets']} sheets verified "
        "with openpyxl 3.1.5 and raw OPC inspection; hidden rows never invent cells. Not Excel "
        "recalculation or rendering evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
