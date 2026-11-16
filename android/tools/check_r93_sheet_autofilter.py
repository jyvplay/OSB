#!/usr/bin/env python3
"""Independent verification for R93 AutoFilter (`<autoFilter>`, `filterColumn`, `filters`, `customFilters`).

Two layers:

1. **raw OPC** — the `<autoFilter>` element must be absent exactly when the intent table declares no filter,
   must sit after `<sheetData>` inside the worksheet, must carry the declared `ref`, and each of its
   `filterColumn` children must appear in declaration order with a zero-based `colId` inside the range width,
   the schema's attribute order (`colId`, `hiddenButton`, `showButton`), the button attributes written only
   when they differ from their defaults (`hiddenButton` `false`, `showButton` `true`), and exactly one of
   `<filters>` / `<customFilters>` holding the declared values or criteria in order — with `and` written on a
   two-criterion `<customFilters>` and never on a one-criterion one;
2. **openpyxl 3.1.5** — `worksheet.auto_filter` (`ref`, `filterColumn`, `Filters.filter`,
   `CustomFilters.customFilter`, the join flag and both button flags) must agree.

Everything here is a *record*. Writing `<autoFilter>` hides no rows: Excel stores the hidden row flags in
`<row hidden="1"/>`, which this writer never sets. openpyxl is also a reader, not Excel — nothing was filtered
by running this.

Host-only. Utility: `python3 tools/check_r93_sheet_autofilter.py testing/build/r93/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import sys
import warnings
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
FIELDS = 9
APPLIED_FIELDS = 6  # …plus the typed table the semantics checker re-evaluates
UNIT = '\u001f'
COLUMN_ORDER = ['colId', 'hiddenButton', 'showButton']
# CT_Worksheet child sequence (ECMA-376 Part 1 §18.3.1.99), which is a *sequence*: Excel reports a part whose
# children are out of order as damaged. Only the elements this writer can emit are listed, and an element that
# is missing from this list fails the check rather than being silently accepted.
WORKSHEET_ORDER = [
    'sheetPr', 'dimension', 'sheetViews', 'sheetFormatPr', 'cols', 'sheetData', 'sheetCalcPr',
    'sheetProtection', 'protectedRanges', 'scenarios', 'autoFilter', 'sortState', 'dataConsolidate',
    'customSheetViews', 'mergeCells', 'phoneticPr', 'conditionalFormatting', 'dataValidations', 'hyperlinks',
    'printOptions', 'pageMargins', 'pageSetup', 'headerFooter', 'rowBreaks', 'colBreaks',
    'customProperties', 'cellWatches', 'ignoredErrors', 'smartTags', 'drawing', 'legacyDrawing',
    'legacyDrawingHF', 'picture', 'oleObjects', 'controls', 'webPublishItems', 'tableParts', 'extLst',
]
OPERATORS = {'equal', 'notEqual', 'greaterThan', 'greaterThanOrEqual', 'lessThan', 'lessThanOrEqual'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path, fields: int) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows = []
    # Split on newlines only: the typed table in `applied_filters.tsv` uses \x1e as a record separator, and
    # `str.splitlines()` would split a line at that character.
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'{path.name} line {number}: {len(parts)} fields, expected {fields}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def groups(sheets: list[list[str]]) -> dict[tuple[str, str], list[list[str]]]:
    grouped: dict[tuple[str, str], list[list[str]]] = {}
    for row in sheets:
        grouped.setdefault((row[0], row[1]), []).append(row)
    return grouped


def column_label_width(ref: str) -> int:
    """Columns spanned by an A1 range, computed here rather than read back from the writer."""
    def letters(part: str) -> str:
        return ''.join(ch for ch in part if ch.isalpha())

    def index(label: str) -> int:
        value = 0
        for ch in label:
            value = value * 26 + (ord(ch) - ord('A') + 1)
        return value

    pieces = ref.split(':')
    if len(pieces) == 1:
        return 1
    return index(letters(pieces[1])) - index(letters(pieces[0])) + 1


def sheet_parts(folder: Path, name: str) -> dict[str, str]:
    """Sheet name -> worksheet part, through workbook.xml and its relationships."""
    with zipfile.ZipFile(folder / name) as archive:
        book = ElementTree.fromstring(archive.read('xl/workbook.xml'))
        rels = ElementTree.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
    targets = {item.get('Id'): item.get('Target') for item in rels}
    parts = {}
    for sheet in book.find(f'{S}sheets'):
        target = targets[sheet.get(f'{R}id')]
        normalized = target.lstrip('/')
        parts[sheet.get('name')] = normalized if normalized.startswith('xl/') else f'xl/{normalized}'
    return parts


def worksheet(folder: Path, name: str, part: str) -> tuple[ElementTree.Element, str]:
    with zipfile.ZipFile(folder / name) as archive:
        raw = archive.read(part).decode('utf-8')
    try:
        return ElementTree.fromstring(raw), raw
    except ElementTree.ParseError as error:
        raise Fail(f'{name}:{part} is not well-formed: {error}') from error


def payload_columns(payload: str) -> list[str]:
    return payload.split(UNIT) if payload else []


def expected_criteria(row: list[str]) -> list[tuple[str, str]]:
    _, _, _, _, kind, payload, _, _, _ = row
    require(kind in {'values', 'custom'}, f'{row[0]}:{row[1]} unknown criterion kind {kind!r}')
    if kind == 'values':
        return [('=', value) for value in payload_columns(payload)]
    criteria = []
    for item in payload_columns(payload):
        operator, _, value = item.partition('=')
        require(operator in OPERATORS, f'{row[0]}:{row[1]} unknown operator {operator!r}')
        criteria.append((operator, value))
    return criteria


def verify_raw(folder: Path, sheets: list[list[str]]) -> dict[str, int]:
    declared = {row[0] for row in sheets}
    on_disk = {path.name for path in folder.glob('*.xlsx')}
    require(declared == on_disk,
            f'the intent table and the folder disagree: declared {sorted(declared)}, present {sorted(on_disk)}')
    filtered = 0
    columns_checked = 0
    for (name, sheet_name), rows in groups(sheets).items():
        refs = {row[2] for row in rows}
        require(len(refs) == 1, f'{name}:{sheet_name} declares several refs: {sorted(refs)}')
        ref = refs.pop()
        part = sheet_parts(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        root, raw = worksheet(folder, name, part)
        nodes = [child for child in root if child.tag == f'{S}autoFilter']
        require(len(nodes) <= 1, f'{name}:{sheet_name} carries {len(nodes)} <autoFilter> elements')
        if ref == '-':
            require(not nodes, f'{name}:{sheet_name} declares no filter but carries <autoFilter>')
            require('autoFilter' not in raw, f'{name}:{sheet_name} mentions autoFilter in raw XML')
            for row in rows:
                require(row[4] == 'none', f'{name}:{sheet_name} declares a column for an unfiltered sheet')
            continue
        require(len(nodes) == 1, f'{name}:{sheet_name} declares {ref} but carries no <autoFilter>')
        node = nodes[0]

        # Position inside the worksheet: CT_Worksheet is a sequence, so every child must be in schema order.
        order = [child.tag.split('}')[-1] for child in root]
        require('sheetData' in order, f'{name}:{sheet_name} has no <sheetData>')
        unknown = [item for item in order if item not in WORKSHEET_ORDER]
        require(not unknown, f'{name}:{sheet_name} has unmapped worksheet children {unknown}')
        ordinals = [WORKSHEET_ORDER.index(item) for item in order]
        require(ordinals == sorted(ordinals),
                f'{name}:{sheet_name} violates the CT_Worksheet sequence: {order}')
        require(order.index('autoFilter') > order.index('sheetData'),
                f'{name}:{sheet_name} puts <autoFilter> before <sheetData>')

        require(node.get('ref') == ref, f'{name}:{sheet_name} ref {node.get("ref")!r} != declared {ref!r}')
        require(list(node.attrib) == ['ref'], f'{name}:{sheet_name} autoFilter attributes {list(node.attrib)}')
        children = list(node)
        for child in children:
            require(child.tag == f'{S}filterColumn',
                    f'{name}:{sheet_name} has an unmodelled <{child.tag.split("}")[-1]}> under <autoFilter>')
        expected = [row for row in rows if row[3] != '-']
        require(len(children) == len(expected),
                f'{name}:{sheet_name} has {len(children)} filterColumn(s), declared {len(expected)}')
        if not expected:
            require(len(node) == 0 and not (node.text or '').strip(),
                    f'{name}:{sheet_name} a column-less filter must be an empty element')
        width = column_label_width(ref)
        for child, row in zip(children, expected):
            _, _, _, col_id, kind, payload, join, hidden, show = row
            col = int(col_id)
            require(child.get('colId') == col_id, f'{name}:{sheet_name} colId {child.get("colId")!r} != {col_id!r}')
            require(0 <= col < width, f'{name}:{sheet_name} colId {col} outside the {width}-column range {ref}')
            attributes = [attribute for attribute in child.attrib if attribute in COLUMN_ORDER]
            require(attributes == sorted(attributes, key=COLUMN_ORDER.index),
                    f'{name}:{sheet_name} colId {col} attribute order {attributes}')
            require(set(child.attrib) <= set(COLUMN_ORDER),
                    f'{name}:{sheet_name} colId {col} unmodelled attributes {sorted(child.attrib)}')
            for attribute, declared_value, default in (('hiddenButton', hidden, 'false'),
                                                       ('showButton', show, 'true')):
                if declared_value == default:
                    require(attribute not in child.attrib,
                            f'{name}:{sheet_name} colId {col} wrote default {attribute}={declared_value}')
                else:
                    want = '1' if declared_value == 'true' else '0'
                    require(child.get(attribute) == want,
                            f'{name}:{sheet_name} colId {col} {attribute}={child.get(attribute)!r} != {want!r}')
            require(all(isinstance(value, str) for value in (hidden, show)),
                    f'{name}:{sheet_name} colId {col} button flags are not spelled true/false')
            criteria = expected_criteria(row)
            filters = [item for item in child if item.tag == f'{S}filters']
            custom = [item for item in child if item.tag == f'{S}customFilters']
            require(len(child) == 1, f'{name}:{sheet_name} colId {col} carries {len(child)} criteria children')
            if kind == 'values':
                require(filters and not custom,
                        f'{name}:{sheet_name} colId {col} declares values but writes something else')
                entries = list(filters[0])
                require(not filters[0].attrib,
                        f'{name}:{sheet_name} colId {col} unmodelled filters attributes {sorted(filters[0].attrib)}')
                require(all(item.tag == f'{S}filter' for item in entries),
                        f'{name}:{sheet_name} colId {col} <filters> holds a non-filter child')
                got = [item.get('val') for item in entries]
                require(got == [value for _, value in criteria],
                        f'{name}:{sheet_name} colId {col} values {got} != {[v for _, v in criteria]}')
                require(all(list(item.attrib) == ['val'] for item in entries),
                        f'{name}:{sheet_name} colId {col} filter carries an extra attribute')
            else:
                require(custom and not filters,
                        f'{name}:{sheet_name} colId {col} declares criteria but writes something else')
                entries = list(custom[0])
                require(all(item.tag == f'{S}customFilter' for item in entries),
                        f'{name}:{sheet_name} colId {col} <customFilters> holds a non-criterion child')
                got = [(item.get('operator'), item.get('val')) for item in entries]
                require(got == criteria, f'{name}:{sheet_name} colId {col} criteria {got} != {criteria}')
                require(all(list(item.attrib) == ['operator', 'val'] for item in entries),
                        f'{name}:{sheet_name} colId {col} a criterion carries an extra attribute')
                require(set(custom[0].attrib) <= {'and'},
                        f'{name}:{sheet_name} colId {col} unmodelled customFilters attributes '
                        f'{sorted(custom[0].attrib)}')
                # `and` defaults to false, so the writer emits it only for a real AND join. Writing the
                # default would be noise, and a reader cannot tell an explicit `and="0"` from an absent one.
                if len(criteria) == 2 and join == 'and':
                    require(custom[0].get('and') == '1',
                            f'{name}:{sheet_name} colId {col} and={custom[0].get("and")!r} != \'1\'')
                else:
                    require('and' not in custom[0].attrib,
                            f'{name}:{sheet_name} colId {col} wrote the default join explicitly')
                    require(join == ('or' if len(criteria) == 2 else '-'),
                            f'{name}:{sheet_name} colId {col} declares join {join!r}')
            # Every <filterColumn> must be a child of this <autoFilter>, not of the worksheet.
            require(raw.count('<filterColumn') == len(expected),
                    f'{name}:{sheet_name} has {raw.count("<filterColumn")} filterColumn tags, expected {len(expected)}')
            columns_checked += 1
        filtered += 1
    return {'filtered_sheets': filtered, 'columns': columns_checked}


def verify_applied(folder: Path, applied: list[list[str]], require_openpyxl: bool) -> dict[str, int]:
    """The rows the filter hides. `<autoFilter>` alone hides nothing: this reads the `<row hidden="1">` flags."""
    hidden_total = 0
    undecided_total = 0
    for name, sheet_name, hidden_field, undecided_field, rows_field, _table in applied:
        declared_hidden = {int(item) for item in hidden_field.split(',') if item}
        declared_undecided = {int(item) for item in undecided_field.split(',') if item}
        declared_rows = [int(item) for item in rows_field.split(',') if item]
        require(declared_rows, f'{name}:{sheet_name} declares no rows')
        require(not (declared_hidden & declared_undecided),
                f'{name}:{sheet_name} declares a row both hidden and undecided')
        require(declared_hidden | declared_undecided <= set(declared_rows),
                f'{name}:{sheet_name} declares a row outside its range')
        part = sheet_parts(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        root, _ = worksheet(folder, name, part)
        hidden = set()
        for row in root.iter(f'{S}row'):
            number = int(row.get('r'))
            flag = row.get('hidden')
            require(flag in {None, '0', '1', 'true', 'false'},
                    f'{name}:{sheet_name} row {number} hidden={flag!r}')
            if flag in {'1', 'true'}:
                hidden.add(number)
        require(hidden == declared_hidden,
                f'{name}:{sheet_name} hides rows {sorted(hidden)}, declared {sorted(declared_hidden)}')
        require(not (hidden & declared_undecided),
                f'{name}:{sheet_name} hid an undecided row: {sorted(hidden & declared_undecided)}')
        require(declared_rows[0] not in hidden,
                f'{name}:{sheet_name} hid the header row {declared_rows[0]}')
        hidden_total += len(hidden)
        undecided_total += len(declared_undecided)
        if require_openpyxl:
            import openpyxl  # noqa: PLC0415

            with warnings.catch_warnings():
                warnings.simplefilter('ignore')
                book = openpyxl.load_workbook(folder / name)
            sheet = book[sheet_name]
            got = {row for row, dimension in sheet.row_dimensions.items() if dimension.hidden}
            require(got == declared_hidden,
                    f'openpyxl {name}:{sheet_name} hides {sorted(got)}, declared {sorted(declared_hidden)}')
            for row in declared_undecided:
                require(not sheet.row_dimensions[row].hidden,
                        f'openpyxl {name}:{sheet_name} row {row} is hidden although undecided')
    return {'hidden_rows': hidden_total, 'undecided_rows': undecided_total}


def verify_openpyxl(folder: Path, sheets: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    checked = 0
    columns = 0
    for (name, sheet_name), rows in groups(sheets).items():
        refs = {row[2] for row in rows}
        ref = refs.pop()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            book = openpyxl.load_workbook(folder / name)
        require(sheet_name in book.sheetnames, f'openpyxl {name}: no sheet {sheet_name!r}')
        auto = book[sheet_name].auto_filter
        if ref == '-':
            require(auto.ref is None, f'openpyxl {name}:{sheet_name} ref={auto.ref!r}, declared none')
            require(not auto.filterColumn,
                    f'openpyxl {name}:{sheet_name} has {len(auto.filterColumn)} filterColumn(s), declared none')
            checked += 1
            continue
        require(auto.ref == ref, f'openpyxl {name}:{sheet_name} ref={auto.ref!r} != {ref!r}')
        expected = [row for row in rows if row[3] != '-']
        require(len(auto.filterColumn) == len(expected),
                f'openpyxl {name}:{sheet_name} {len(auto.filterColumn)} filterColumn(s), declared {len(expected)}')
        for column, row in zip(auto.filterColumn, expected):
            _, _, _, col_id, kind, payload, join, hidden, show = row
            require(str(column.colId) == col_id,
                    f'openpyxl {name}:{sheet_name} colId {column.colId!r} != {col_id!r}')
            require(bool(column.hiddenButton) == (hidden == 'true'),
                    f'openpyxl {name}:{sheet_name} colId {col_id} hiddenButton={column.hiddenButton!r} != {hidden}')
            require(bool(column.showButton) == (show == 'true'),
                    f'openpyxl {name}:{sheet_name} colId {col_id} showButton={column.showButton!r} != {show}')
            criteria = expected_criteria(row)
            if kind == 'values':
                require(column.filters is not None and column.customFilters is None,
                        f'openpyxl {name}:{sheet_name} colId {col_id} read the wrong criterion kind')
                got = [str(value) for value in column.filters.filter]
                require(got == [value for _, value in criteria],
                        f'openpyxl {name}:{sheet_name} colId {col_id} values {got} != {[v for _, v in criteria]}')
            else:
                require(column.customFilters is not None and column.filters is None,
                        f'openpyxl {name}:{sheet_name} colId {col_id} read the wrong criterion kind')
                got = [(str(item.operator), str(item.val)) for item in column.customFilters.customFilter]
                require(got == criteria, f'openpyxl {name}:{sheet_name} colId {col_id} criteria {got} != {criteria}')
                if len(criteria) == 2:
                    joined = bool(column.customFilters._and)
                    require(joined == (join == 'and'),
                            f'openpyxl {name}:{sheet_name} colId {col_id} and={joined}, declared {join}')
                else:
                    require(not column.customFilters._and,
                            f'openpyxl {name}:{sheet_name} colId {col_id} read a join for one criterion')
            columns += 1
        checked += 1
    return {'reader_sheets': checked, 'reader_columns': columns}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    sheets = intent_table(folder / 'autofilters.tsv', FIELDS)
    applied = intent_table(folder / 'applied_filters.tsv', APPLIED_FIELDS)
    totals = verify_raw(folder, sheets)
    totals.update(verify_applied(folder, applied, require_openpyxl))
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, sheets))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R93 autoFilter verification FAILED: {failure}')
        return 1
    message = (f'R93 independent ZIP/XML checks: {totals["filtered_sheets"]} filtered sheet(s), '
               f'{totals["columns"]} filterColumn record(s) verified, {totals["hidden_rows"]} row(s) hidden '
               f'by their filter and {totals["undecided_rows"]} left visible as undecided')
    if 'reader_columns' in totals:
        message += (f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s) and '
                    f'{totals["reader_columns"]} column(s)')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
