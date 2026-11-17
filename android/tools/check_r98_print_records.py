#!/usr/bin/env python3
"""Independent verification for R98 print records (`_xlnm.Print_Area`, `_xlnm.Print_Titles`).

Layers:

1. **raw OPC** — the `definedNames` block follows `<sheets>`; the entries are exactly the ones `names.tsv`
   declares, in order, with a `localSheetId` that indexes the sheet the reference names; user names come before
   the sheet records; the prefix of every record is the sheet's own name, quoted exactly when Excel would quote
   it (`'Fit Two'!`); every corner carries its `$`; and a `Print_Titles` record spells rows before columns;
2. **cross-table** — `names.tsv` and `print.tsv` must describe the same areas and titles: a sheet that declares
   an area in one and not the other is a workbook whose file and intent disagree;
3. **openpyxl 3.1.5** — the reader lifts the records out of `defined_names` and back onto the sheet
   (`print_area`, `print_title_rows`, `print_title_cols`), and no `_xlnm.*` name survives as a workbook name.

A record is a *declaration*: nothing here claims Excel or LibreOffice would paginate to those pages.

Utility: `python3 tools/check_r98_print_records.py testing/build/r98/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
NAME_FIELDS = 4
PRINT_FIELDS = 17
RECORD_NAMES = {'_xlnm.Print_Area', '_xlnm.Print_Titles'}
PLAIN_WORD = re.compile(r'^[A-Za-z0-9_.]+$')
CELL = re.compile(r'^\$[A-Z]{1,3}\$(?:[1-9][0-9]{0,6})$')
ROW_RANGE = re.compile(r'^\$[1-9][0-9]{0,6}:\$[1-9][0-9]{0,6}$')
COL_RANGE = re.compile(r'^\$[A-Z]{1,3}:\$[A-Z]{1,3}$')


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


def workbook_root(folder: Path, name: str) -> ElementTree.Element:
    with zipfile.ZipFile(folder / name) as archive:
        return ElementTree.fromstring(archive.read('xl/workbook.xml'))


def quoted(sheet_name: str) -> str:
    """The prefix Excel writes for a sheet name: bare for a plain word that does not start with a digit."""
    if PLAIN_WORD.match(sheet_name) and not sheet_name[0].isdigit():
        return sheet_name
    return "'" + sheet_name.replace("'", "''") + "'"


def verify_raw(folder: Path, names: list[list[str]], prints: list[list[str]]) -> dict[str, int]:
    root = workbook_root(folder, 'r98.xlsx')
    sheets = [sheet.get('name') for sheet in root.find(f'{S}sheets')]
    children = [child.tag.split('}')[-1] for child in root]
    require('definedNames' in children, 'the workbook has no definedNames block')
    require(children.index('definedNames') > children.index('sheets'),
            f'definedNames must follow sheets: {children}')
    entries = root.find(f'{S}definedNames').findall(f'{S}definedName')
    require(len(entries) == len(names),
            f'workbook.xml has {len(entries)} defined names, names.tsv declares {len(names)}')

    seen_record = False
    records = 0
    for entry, (name, scope, reference, is_record) in zip(entries, names):
        require(is_record in {'0', '1'}, f'{name}: is_record={is_record!r}')
        require(entry.get('name') == name, f'{name}: workbook.xml has {entry.get("name")!r} in that position')
        got_scope = entry.get('localSheetId')
        require((got_scope is None) == (scope == 'global'),
                f'{name}: localSheetId={got_scope!r}, declared {scope!r}')
        if scope != 'global':
            require(got_scope == scope, f'{name}: localSheetId={got_scope} != {scope}')
        require(entry.text == reference, f'{name}: reference {entry.text!r} != {reference!r}')
        if is_record == '1':
            require(name in RECORD_NAMES, f'{name}: not a print record name')
            records += 1
            index = int(scope)
            require(index < len(sheets), f'{name}: scope {index} is outside the workbook')
            sheet_name = sheets[index]
            prefix = quoted(sheet_name) + '!'
            for part in reference.split(','):
                require(part.startswith(prefix),
                        f'{name}: record part {part!r} does not start with {prefix!r}')
                body = part[len(prefix):]
                if name == '_xlnm.Print_Area':
                    require(CELL.match(body) or (':' in body and all(CELL.match(piece) for piece in body.split(':'))),
                            f'{name}: area {body!r} is not absolute A1 form')
                else:
                    pieces = body.split(':')
                    require(len(pieces) == 2 and (ROW_RANGE.match(body) or COL_RANGE.match(body)),
                            f'{name}: titles {body!r} is not an absolute row or column range')
            if ',' in reference:
                first = reference.split(',')[0].split('!')[-1]
                require(ROW_RANGE.match(first) is not None,
                        f'{name}: rows must be spelled before columns, got {reference!r}')
        else:
            require(not name.startswith('_xlnm.Print'),
                    f'{name}: a print record was declared as a user name')
            require(not seen_record, f'{name}: user names must be written before the sheet records')
        if is_record == '1':
            seen_record = True

    # One record of each kind per sheet, and never a record for a sheet that declares nothing.
    per_sheet: dict[int, set[str]] = {}
    for name, scope, _reference, is_record in names:
        if is_record == '1':
            per_sheet.setdefault(int(scope), set()).add(name)
    for index, kinds in per_sheet.items():
        require(len(kinds) == len([name for name, scope, _r, rec in names
                                   if rec == '1' and int(scope) == index]),
                f'sheet {index}: a record name appears twice in one scope')

    # The two tables must agree sheet by sheet.
    for row in prints:
        sheet, has, _fit, _w, _h, _first, _use, _orient, _paper, _scale, area, title_rows, title_cols, *_rest = row
        index = sheets.index(sheet)
        has_area = '_xlnm.Print_Area' in per_sheet.get(index, set())
        has_titles = '_xlnm.Print_Titles' in per_sheet.get(index, set())
        require(has_area == (area != '-'),
                f'{sheet}: print.tsv area={area!r} but the workbook {"has" if has_area else "lacks"} a record')
        require(has_titles == (title_rows != '-' or title_cols != '-'),
                f'{sheet}: print.tsv titles disagree with the workbook records')
    return {'records': records, 'sheets': len(sheets)}


def verify_openpyxl(folder: Path, names: list[list[str]], prints: list[list[str]]) -> dict[str, int]:
    import warnings

    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        book = openpyxl.load_workbook(folder / 'r98.xlsx')
    globals_declared = sorted(name for name, scope, _r, _rec in names if scope == 'global')
    globals_read = sorted(book.defined_names)
    require(globals_read == globals_declared,
            f'openpyxl sees workbook names {globals_read}, declared {globals_declared}')
    for name in globals_read:
        require(not name.startswith('_xlnm.Print'),
                f'openpyxl kept {name} as a workbook name instead of lifting it onto its sheet')
    checked = 0
    for row in prints:
        sheet_name, _has, _fit, _w, _h, _first, _use, _orient, _paper, _scale, area, title_rows, title_cols, *_ = row
        sheet = book[sheet_name]
        got = sheet.print_area or None
        if area == '-':
            require(got is None, f'openpyxl {sheet_name}: invented a print area {got!r}')
        else:
            require(got is not None and got.replace('$', '').split('!')[-1].replace("'", '') == area,
                    f'openpyxl {sheet_name}: print area {got!r} != {area!r}')
        rows_got = sheet.print_title_rows.replace('$', '') if sheet.print_title_rows else '-'
        cols_got = sheet.print_title_cols.replace('$', '') if sheet.print_title_cols else '-'
        require(rows_got == title_rows, f'openpyxl {sheet_name}: title rows {rows_got!r} != {title_rows!r}')
        require(cols_got == title_cols, f'openpyxl {sheet_name}: title cols {cols_got!r} != {title_cols!r}')
        checked += 1
    # The foreign fixture is a real package: openpyxl must read it, and it keeps four records of its own.
    require((folder / 'r98-foreign.xlsx').is_file(), 'missing fixture r98-foreign.xlsx')
    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        foreign = openpyxl.load_workbook(folder / 'r98-foreign.xlsx')
    foreign_root = workbook_root(folder, 'r98-foreign.xlsx')
    foreign_records = [entry for entry in foreign_root.find(f'{S}definedNames').findall(f'{S}definedName')
                       if entry.get('localSheetId') is not None]
    require(len(foreign_records) == 4, f'foreign fixture: {len(foreign_records)} scoped records, expected 4')
    require(foreign.worksheets[0].print_area is not None, 'foreign fixture: openpyxl found no print area')
    return {'reader_sheets': checked}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    names = table(folder / 'names.tsv', NAME_FIELDS)
    prints = table(folder / 'print.tsv', PRINT_FIELDS)
    totals = verify_raw(folder, names, prints)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, names, prints))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R98 print-record verification FAILED: {failure}')
        return 1
    message = (f'R98 print records: {totals["records"]} sheet-scoped record(s) over {totals["sheets"]} sheet(s), '
               f'scopes and quoting verified')
    if 'reader_sheets' in totals:
        message += f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
