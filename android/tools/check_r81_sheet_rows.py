#!/usr/bin/env python3
"""Independent verification for the R81 spreadsheet row-height workbooks.

The production writer (`XlsxStyledBook`) emits real `.xlsx` files whose sheets may declare row
heights (`<row ht customHeight="1">`), including rows that carry no cells. This verifier opens every
workbook twice:

1. **raw OPC** — every worksheet part is parsed directly: each `<row>` has an `r`, rows are in
   ascending order, `ht` appears exactly on the declared rows and is always accompanied by
   `customHeight="1"`, the height is a finite decimal inside 0..409 points, and the row set is
   exactly the declared cells' rows united with the height-only rows (a height never invents a row).
2. **openpyxl 3.1.5** — an independent implementation that reads `ht` into
   `RowDimension.height`/`customHeight` and exposes cells, frozen panes and column widths.

Host-only. openpyxl is a reader, not Excel: agreement here is package-structure evidence, not
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
INTENT_FIELDS = 9
MAX_HEIGHT = 409.0
TOLERANCE = 1e-9


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def pairs(text: str) -> dict[int, float]:
    if text == '-':
        return {}
    result: dict[int, float] = {}
    for item in text.split(','):
        key, _, value = item.partition(':')
        result[int(key)] = float(value)
    return result


def ints(text: str) -> list[int]:
    return [] if text == '-' else [int(value) for value in text.split(',')]


def width_pairs(text: str) -> dict[int, float]:
    return pairs(text)


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
            'index': int(fields[1]),
            'name': fields[2],
            'heights': pairs(fields[3]),
            'cell_rows': ints(fields[4]),
            'cells': int(fields[5]),
            'max_cell_row': int(fields[6]),
            'freeze': fields[7],
            'widths': width_pairs(fields[8]),
        })
    require(table, 'intent table is empty')
    for name, sheets in table.items():
        require([entry['index'] for entry in sheets] == list(range(len(sheets))),
                f'{name}: sheet indices are not 0..{len(sheets) - 1}')
    return table


def column_label(index: int) -> str:
    label = ''
    while index > 0:
        index, remainder = divmod(index - 1, 26)
        label = chr(ord('A') + remainder) + label
    return label


def check_sheet_xml(part: str, intent: dict) -> None:
    root = ElementTree.fromstring(part)
    data = root.find(f'{S}sheetData')
    require(data is not None, f'{intent["name"]}: worksheet has no sheetData')
    rows = [child for child in data if child.tag == f'{S}row']
    numbers = []
    for row in rows:
        raw = row.get('r')
        require(raw is not None, f'{intent["name"]}: a row has no r attribute')
        numbers.append(int(raw))
    # Ordering and uniqueness are properties of the row sequence, so they are checked before the
    # per-row attributes: a shuffled sheet must not be reported as a stray height instead.
    require(numbers == sorted(numbers), f'{intent["name"]}: rows are not in ascending order: {numbers}')
    require(len(numbers) == len(set(numbers)), f'{intent["name"]}: duplicate row numbers: {numbers}')
    for row, number in zip(rows, numbers):
        height = row.get('ht')
        custom = row.get('customHeight')
        declared = intent['heights'].get(number)
        if declared is None:
            require(height is None,
                    f'{intent["name"]}: row {number} carries ht={height} but no height was declared')
            require(custom is None,
                    f'{intent["name"]}: row {number} carries customHeight={custom} without a height')
            continue
        require(height is not None, f'{intent["name"]}: row {number} is missing its declared height')
        measured = float(height)
        require(abs(measured - declared) <= TOLERANCE,
                f'{intent["name"]}: row {number} height {measured} != declared {declared}')
        require(0.0 < measured <= MAX_HEIGHT,
                f'{intent["name"]}: row {number} height {measured} outside 0..{MAX_HEIGHT} points')
        require(custom == '1',
                f'{intent["name"]}: row {number} declares a height without customHeight="1"')
    expected = set(intent['cell_rows']) | set(intent['heights'])
    require(set(numbers) == expected,
            f'{intent["name"]}: row set {sorted(numbers)} != declared {sorted(expected)}')
    columns = root.find(f'{S}cols')
    declared_widths = {}
    if columns is not None:
        for column in columns:
            if column.tag != f'{S}col':
                continue
            declared_widths[int(column.get('min'))] = float(column.get('width'))
    require(declared_widths == intent['widths'],
            f'{intent["name"]}: column widths {declared_widths} != declared {intent["widths"]}')


def check_with_openpyxl(path: Path, intent: dict) -> None:
    import openpyxl  # imported lazily so the raw-OPC checks can run without it

    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        workbook = openpyxl.load_workbook(str(path))
    require(len(workbook.worksheets) == len(intent),
            f'{path.name}: openpyxl sees {len(workbook.worksheets)} sheets, declared {len(intent)}')
    for sheet, expected in zip(workbook.worksheets, intent):
        require(sheet.title == expected['name'],
                f'{path.name}: sheet title {sheet.title!r} != declared {expected["name"]!r}')
        measured = {}
        for number, dimension in sheet.row_dimensions.items():
            if dimension.height is not None:
                measured[int(number)] = (float(dimension.height), bool(dimension.customHeight))
        require(set(measured) == set(expected['heights']),
                f'{path.name}/{sheet.title}: openpyxl heights on rows {sorted(measured)} != declared '
                f'{sorted(expected["heights"])}')
        for number, declared in expected['heights'].items():
            height, custom = measured[number]
            require(abs(height - declared) <= TOLERANCE,
                    f'{path.name}/{sheet.title}: openpyxl row {number} height {height} != {declared}')
            require(custom, f'{path.name}/{sheet.title}: openpyxl row {number} is not customHeight')
        occupied = sum(1 for row in sheet.iter_rows() for cell in row if cell.value is not None)
        require(occupied == expected['cells'],
                f'{path.name}/{sheet.title}: openpyxl counts {occupied} non-empty cells, declared '
                f'{expected["cells"]}')
        require(sheet.max_row == expected['max_cell_row'],
                f'{path.name}/{sheet.title}: openpyxl max_row {sheet.max_row} != declared '
                f'{expected["max_cell_row"]} (a height must not invent a row)')
        if expected['freeze'] == '-':
            require(sheet.freeze_panes is None,
                    f'{path.name}/{sheet.title}: unexpected frozen pane {sheet.freeze_panes}')
        else:
            rows, _, columns = expected['freeze'].partition('/')
            target = f'{column_label(int(columns) + 1)}{int(rows) + 1}'
            require(sheet.freeze_panes == target,
                    f'{path.name}/{sheet.title}: frozen pane {sheet.freeze_panes} != {target}')
        for column, width in expected['widths'].items():
            actual = sheet.column_dimensions[column_label(column)].width
            require(actual is not None and abs(float(actual) - width) <= TOLERANCE,
                    f'{path.name}/{sheet.title}: column {column} width {actual} != declared {width}')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'rows.tsv')
    sheets = 0
    for name, expected in intent.items():
        path = directory / f'{name}.xlsx'
        require(path.is_file(), f'missing workbook {path}')
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            for entry in expected:
                part = f'xl/worksheets/sheet{entry["index"] + 1}.xml'
                require(part in names, f'{path.name}: missing {part}')
                check_sheet_xml(archive.read(part).decode('utf-8'), entry)
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
            print(f'R81 SHEET ROW CHECK FAILED: openpyxl unavailable: {error}', file=sys.stderr)
            return 1
        print(f'openpyxl unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R81 SHEET ROW CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R81 spreadsheet rows: {report['workbooks']} workbooks and {report['sheets']} sheets verified "
        "with openpyxl 3.1.5 and raw OPC inspection; heights never invent rows. Not Excel "
        "recalculation or rendering evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
