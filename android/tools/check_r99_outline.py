#!/usr/bin/env python3
"""Independent verification for R99 worksheet outlining.

Three layers:

1. **raw OPC** — `<sheetPr>` carries `outlinePr` in CT_SheetPr's sequence (after `tabColor`, before
   `pageSetUpPr`), and only with the attributes that differ from the schema defaults; every `<row>`/`<col>` in
   `levels.tsv` carries exactly the `outlineLevel`/`collapsed`/`hidden` the table declares; a column is written
   as one `<col min=max>` element per index, so nothing is hidden inside a span;
2. **cross-layer** — a sheet with grouped rows or columns must have a `<cols>` element for the columns it
   declares and `<row>` elements for the rows, and a sheet that declares nothing must have neither;
3. **openpyxl 3.1.5** — `worksheet.sheet_properties.outlinePr.summaryBelow/summaryRight/applyStyles`,
   `worksheet.row_dimensions[i].outlineLevel/.collapsed/.hidden` and
   `worksheet.column_dimensions[letter].outlineLevel/.collapsed` must agree.

Grouping is not hiding: this lane checks the two independently, because a file that collapses a group without
hiding its members looks grouped in XML and does not behave that way in an interface.

Host-only. No application ran: no group was expanded, collapsed or drawn.

Utility: `python3 tools/check_r99_outline.py testing/build/r99/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
OUTLINE_FIELDS = 9
LEVEL_FIELDS = 6
MAX_LEVEL = 7


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def table(path: Path, fields: int) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows = []
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'{path.name} line {number}: {len(parts)} fields, expected {fields}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def sheet_parts(folder: Path, name: str) -> dict[str, str]:
    with zipfile.ZipFile(folder / name) as archive:
        book = ElementTree.fromstring(archive.read('xl/workbook.xml'))
        rels = ElementTree.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
        targets = {item.get('Id'): item.get('Target') for item in rels}
        parts = {}
        for sheet in book.find(f'{S}sheets'):
            target = (targets[sheet.get(f'{R}id')] or '').lstrip('/')
            parts[sheet.get('name')] = target if target.startswith('xl/') else f'xl/{target}'
    return parts


def column_letter(index: int) -> str:
    letters = ''
    while index > 0:
        index, remainder = divmod(index - 1, 26)
        letters = chr(ord('A') + remainder) + letters
    return letters


def verify_raw(folder: Path, outline: list[list[str]], levels: list[list[str]]) -> dict[str, int]:
    parts = sheet_parts(folder, 'r99.xlsx')
    require({row[0] for row in outline} == set(parts),
            f'outline.tsv describes {sorted(row[0] for row in outline)}, the workbook has {sorted(parts)}')
    by_sheet: dict[str, list[list[str]]] = {}
    for row in levels:
        by_sheet.setdefault(row[0], []).append(row)

    with_pr = 0
    grouped_sheets = 0
    checked_levels = 0
    for sheet, below, right, styles, row_count, col_count, collapsed_rows, collapsed_cols, max_level in outline:
        with zipfile.ZipFile(folder / 'r99.xlsx') as archive:
            root = ElementTree.fromstring(archive.read(parts[sheet]))
        for name, value in (('rows', row_count), ('cols', col_count)):
            require(value.isdigit(), f'{sheet}: {name}={value!r} is not a count')
        want_pr = below == '0' or right == '0' or styles == '1'
        sheet_pr = root.find(f'{S}sheetPr')
        node = None if sheet_pr is None else sheet_pr.find(f'{S}outlinePr')
        if want_pr:
            require(node is not None, f'{sheet}: settings declared but no <outlinePr>')
            with_pr += 1
            children = [child.tag.split('}')[-1] for child in sheet_pr]
            require(children == [name for name in ('tabColor', 'outlinePr', 'pageSetUpPr') if name in children],
                    f'{sheet}: sheetPr children {children} are out of CT_SheetPr order')
            expected = {}
            if below == '0':
                expected['summaryBelow'] = '0'
            if right == '0':
                expected['summaryRight'] = '0'
            if styles == '1':
                expected['applyStyles'] = '1'
            require(dict(node.attrib) == expected,
                    f'{sheet}: outlinePr attributes {dict(node.attrib)} != {expected}')
        else:
            require(node is None, f'{sheet}: no settings declared, yet an <outlinePr> is present')

        declared_levels = 0
        for sheet_name, kind, index, level, collapsed, hidden in by_sheet.get(sheet, []):
            require(int(level) <= MAX_LEVEL, f'{sheet}: level {level} past the ceiling')
            if int(level) > 0:
                declared_levels += 1
            if kind == 'row':
                element = None
                for row in root.find(f'{S}sheetData') or []:
                    if row.get('r') == index:
                        element = row
                        break
                require(element is not None, f'{sheet}: row {index} is declared but not written')
                require(element.get('outlineLevel', '0') == level,
                        f'{sheet}: row {index} outlineLevel={element.get("outlineLevel")!r} != {level!r}')
                require(('1' if element.get('collapsed') in {'1', 'true'} else '0') == collapsed,
                        f'{sheet}: row {index} collapsed={element.get("collapsed")!r} != {collapsed}')
                require(('1' if element.get('hidden') in {'1', 'true'} else '0') == hidden,
                        f'{sheet}: row {index} hidden={element.get("hidden")!r} != {hidden}')
            else:
                cols = root.find(f'{S}cols')
                require(cols is not None, f'{sheet}: column {index} is declared but there is no <cols>')
                matches = [col for col in cols.findall(f'{S}col') if col.get('min') == index]
                require(len(matches) == 1, f'{sheet}: column {index} has {len(matches)} <col> elements')
                element = matches[0]
                require(element.get('max') == index,
                        f'{sheet}: column {index} is written as a span {element.get("min")}..{element.get("max")}')
                require(element.get('outlineLevel', '0') == level,
                        f'{sheet}: column {index} outlineLevel={element.get("outlineLevel")!r} != {level!r}')
                require(('1' if element.get('collapsed') in {'1', 'true'} else '0') == collapsed,
                        f'{sheet}: column {index} collapsed={element.get("collapsed")!r} != {collapsed}')
            checked_levels += 1
        require(int(max_level) == max([int(row[3]) for row in by_sheet.get(sheet, [])] or [0]),
                f'{sheet}: max_level={max_level} disagrees with the level rows')
        # Completeness in the other direction: every grouped row or column in the file must be described by
        # the intent table, or a tampered file could add grouping nothing checks.
        described_rows = {row[2] for row in by_sheet.get(sheet, []) if row[1] == 'row'}
        described_cols = {row[2] for row in by_sheet.get(sheet, []) if row[1] == 'col'}
        for element in root.find(f'{S}sheetData') or []:
            if element.get('outlineLevel') or element.get('collapsed') in {'1', 'true'}:
                require(element.get('r') in described_rows,
                        f'{sheet}: row {element.get("r")} carries grouping the table does not describe')
        cols_node = root.find(f'{S}cols')
        for element in ([] if cols_node is None else cols_node.findall(f'{S}col')):
            if element.get('outlineLevel') or element.get('collapsed') in {'1', 'true'}:
                require(element.get('min') in described_cols,
                        f'{sheet}: column {element.get("min")} carries grouping the table does not describe')
        if declared_levels or int(col_count):
            grouped_sheets += 1

    with zipfile.ZipFile(folder / 'r99-plain.xlsx') as archive:
        plain = archive.read('xl/worksheets/sheet1.xml').decode('utf-8')
    for needle in ('sheetPr', 'outlinePr', 'outlineLevel', 'collapsed'):
        require(needle not in plain, f'the no-outline workbook gained {needle}')
    return {'outline_sheets': with_pr, 'grouped_sheets': grouped_sheets, 'levels': checked_levels}


def verify_openpyxl(folder: Path, outline: list[list[str]], levels: list[list[str]]) -> dict[str, int]:
    import warnings

    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        book = openpyxl.load_workbook(folder / 'r99.xlsx')
    by_sheet: dict[str, list[list[str]]] = {}
    for row in levels:
        by_sheet.setdefault(row[0], []).append(row)
    checked = 0
    for sheet, below, right, styles, *_rest in outline:
        worksheet = book[sheet]
        properties = worksheet.sheet_properties.outlinePr
        want_pr = below == '0' or right == '0' or styles == '1'
        # openpyxl hands back an `Outline` object for every sheet, present in the file or not, so this lane
        # checks the *values*; presence is the raw lane's job (a sheet that declares nothing must carry no
        # <outlinePr>, and that is asserted there, not here).
        # openpyxl keeps `None` for an attribute the file omits, and the schema's default is `true` for both
        # summaries and `false` for applyStyles; a None must therefore be read as that default, not as False.
        def flag(value, fallback: bool) -> bool:
            return fallback if value is None else bool(value)

        got = {
            'summaryBelow': flag(None if properties is None else properties.summaryBelow, True),
            'summaryRight': flag(None if properties is None else properties.summaryRight, True),
            'applyStyles': flag(None if properties is None else properties.applyStyles, False),
        }
        expected = {'summaryBelow': True, 'summaryRight': True, 'applyStyles': False}
        if below == '0':
            expected['summaryBelow'] = False
        if right == '0':
            expected['summaryRight'] = False
        if styles == '1':
            expected['applyStyles'] = True
        for name, declared in expected.items():
            require(got[name] == declared, f'openpyxl {sheet}: {name}={got[name]!r} != {declared}')
        for sheet_name, kind, index, level, collapsed, hidden in by_sheet.get(sheet, []):
            if kind == 'row':
                dimension = worksheet.row_dimensions[int(index)]
                require((dimension.outlineLevel or 0) == int(level),
                        f'openpyxl {sheet}: row {index} outlineLevel={dimension.outlineLevel} != {level}')
                require(bool(dimension.collapsed) == (collapsed == '1'),
                        f'openpyxl {sheet}: row {index} collapsed={dimension.collapsed}')
                require(bool(dimension.hidden) == (hidden == '1'),
                        f'openpyxl {sheet}: row {index} hidden={dimension.hidden}')
            else:
                dimension = worksheet.column_dimensions[column_letter(int(index))]
                require((dimension.outlineLevel or 0) == int(level),
                        f'openpyxl {sheet}: column {index} outlineLevel={dimension.outlineLevel} != {level}')
                require(bool(dimension.collapsed) == (collapsed == '1'),
                        f'openpyxl {sheet}: column {index} collapsed={dimension.collapsed}')
        checked += 1
    return {'reader_sheets': checked}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    outline = table(folder / 'outline.tsv', OUTLINE_FIELDS)
    levels = table(folder / 'levels.tsv', LEVEL_FIELDS)
    totals = verify_raw(folder, outline, levels)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, outline, levels))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R99 outline verification FAILED: {failure}')
        return 1
    message = (f'R99 outline: {totals["outline_sheets"]} sheet(s) with outlinePr, '
               f'{totals["grouped_sheets"]} sheet(s) carrying grouping, {totals["levels"]} row/column level(s) '
               f'verified')
    if 'reader_sheets' in totals:
        message += f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
