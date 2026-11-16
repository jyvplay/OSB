#!/usr/bin/env python3
"""Independent verification for R92 worksheet protection (`<sheetProtection>`, per-cell `<protection>`).

Two layers again:

1. **raw OPC** — the `<sheetProtection>` element must sit after `<sheetData>`, carry `sheet="1"`, carry all
   fifteen action attributes in the schema's order (and the `password` attribute only when a hash is
   declared), and every cell's `xf` must carry a `<protection>` child exactly when the intent table declares
   a non-default `locked`/`hidden` value, with `applyProtection="1"` on that `xf`;
2. **openpyxl 3.1.5** — `worksheet.protection` (all fifteen flags plus the password string) and
   `cell.protection.locked` / `.hidden` must agree.

The schema's fifteen flags do **not** share a default: eleven default to blocked and four (objects, scenarios,
selectLockedCells, selectUnlockedCells) default to allowed. Because the writer emits all fifteen explicitly,
this verifier can require every attribute to be present, which is what makes the byte layer meaningful.

Host-only. openpyxl is a reader, not Excel: no cell was protected against anything by running this.
"""
from __future__ import annotations

import argparse
import sys
import warnings
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = 19
CELL_FIELDS = 5
ACTIONS = ['objects', 'scenarios', 'formatCells', 'formatColumns', 'formatRows', 'insertColumns', 'insertRows',
           'insertHyperlinks', 'deleteColumns', 'deleteRows', 'selectLockedCells', 'sort', 'autoFilter',
           'pivotTables', 'selectUnlockedCells']
# Schema order: password, sheet, then the fifteen flags.
SHEET_ORDER = ['password', 'sheet'] + ACTIONS
DEFAULT_LOCKED = True      # CT_CellProtection locked defaults to true
DEFAULT_HIDDEN = False


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path, fields: int) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows = []
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'{path.name} line {number}: {len(parts)} fields, expected {fields}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def sheet_xml(folder: Path, name: str) -> tuple[ElementTree.Element, str]:
    with zipfile.ZipFile(folder / name) as archive:
        raw = archive.read('xl/worksheets/sheet1.xml').decode('utf-8')
    try:
        return ElementTree.fromstring(raw), raw
    except ElementTree.ParseError as error:
        raise Fail(f'{name}: xl/worksheets/sheet1.xml is not well-formed: {error}') from error


def verify_raw(folder: Path, sheets: list[list[str]], cells: list[list[str]]) -> dict[str, int]:
    declared = {row[0] for row in sheets}
    on_disk = {path.name for path in folder.glob('*.xlsx')}
    require(declared == on_disk,
            f'the intent table and the folder disagree: declared {sorted(declared)}, present {sorted(on_disk)}')
    protected_sheets = 0
    for row in sheets:
        name, sheet_name, protected, password, *flags = row
        require(len(flags) == len(ACTIONS), f'{name}: {len(flags)} flag columns')
        root, raw = sheet_xml(folder, name)
        node = root.find(f'{S}sheetProtection')
        if protected == '0':
            require(node is None, f'{name}:{sheet_name} declares no protection but carries <sheetProtection>')
            continue
        require(node is not None, f'{name}:{sheet_name} declares protection but carries no <sheetProtection>')
        order = [attribute for attribute in node.attrib if attribute in SHEET_ORDER]
        require(order == sorted(order, key=SHEET_ORDER.index),
                f'{name}: CT_SheetProtection attribute order violated: {list(node.attrib)}')
        require(node.get('sheet') == '1', f'{name}: sheet="{node.get("sheet")}" does not activate protection')
        if password == '-':
            require('password' not in node.attrib, f'{name}: an undeclared password attribute was written')
        else:
            require(node.get('password') == password,
                    f'{name}: password {node.get("password")!r} != declared {password!r}')
        for action, flag in zip(ACTIONS, flags):
            require(action in node.attrib, f'{name}: {action} was not written explicitly')
            require(node.get(action) == flag, f'{name}: {action}={node.get(action)!r} != declared {flag!r}')
        require(raw.index('<sheetProtection') > raw.index('</sheetData>'),
                f'{name}: sheetProtection must follow sheetData')
        require(raw.index('<sheetProtection') < raw.index('</worksheet>'),
                f'{name}: sheetProtection must be inside the worksheet')
        protected_sheets += 1

    checked = 0
    for name, sheet_name, ref, locked, hidden in cells:
        with zipfile.ZipFile(folder / name) as archive:
            styles = ElementTree.fromstring(archive.read('xl/styles.xml'))
            sheet = ElementTree.fromstring(archive.read('xl/worksheets/sheet1.xml'))
        cell = next((c for c in sheet.iter(f'{S}c') if c.get('r') == ref), None)
        require(cell is not None, f'{name}:{sheet_name}!{ref} is missing from the worksheet')
        xfs = styles.find(f'{S}cellXfs').findall(f'{S}xf')
        style_index = int(cell.get('s') or 0)
        require(style_index < len(xfs), f'{name}:{sheet_name}!{ref} points at xf {style_index}')
        xf = xfs[style_index]
        protection = xf.find(f'{S}protection')
        if locked == '-' and hidden == '-':
            require(protection is None,
                    f'{name}:{sheet_name}!{ref} carries a protection element it did not declare')
            require(xf.get('applyProtection') is None,
                    f'{name}:{sheet_name}!{ref} declares applyProtection without a protection element')
        else:
            require(protection is not None, f'{name}:{sheet_name}!{ref} declares protection but has no <protection>')
            require(xf.get('applyProtection') == '1',
                    f'{name}:{sheet_name}!{ref} has <protection> without applyProtection="1"')
            for attribute, declared in (('locked', locked), ('hidden', hidden)):
                if declared == '-':
                    require(protection.get(attribute) is None,
                            f'{name}:{sheet_name}!{ref} wrote an undeclared {attribute}')
                else:
                    require(protection.get(attribute) == ('1' if declared == 'true' else '0'),
                            f'{name}:{sheet_name}!{ref} {attribute}={protection.get(attribute)!r} != {declared}')
        checked += 1
    return {'protected_sheets': protected_sheets, 'cells': checked}


def verify_openpyxl(folder: Path, sheets: list[list[str]], cells: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    checked_sheets = 0
    checked_cells = 0
    for row in sheets:
        name, sheet_name, protected, password, *flags = row
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            book = openpyxl.load_workbook(folder / name)
        protection = book[sheet_name].protection
        expected_sheet = protected == '1'
        require(bool(protection.sheet) == expected_sheet,
                f'openpyxl {name}:{sheet_name} sheet={protection.sheet!r}, declared {expected_sheet}')
        if expected_sheet:
            want = None if password == '-' else password
            require(protection.password == want,
                    f'openpyxl {name}:{sheet_name} password={protection.password!r} != {want!r}')
            for action, flag in zip(ACTIONS, flags):
                got = bool(getattr(protection, action))
                require(got == (flag == '1'),
                        f'openpyxl {name}:{sheet_name} {action}={got}, declared {flag}')
        checked_sheets += 1

    for name, sheet_name, ref, locked, hidden in cells:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            book = openpyxl.load_workbook(folder / name)
        cell = book[sheet_name][ref]
        for attribute, declared, default in (('locked', locked, DEFAULT_LOCKED), ('hidden', hidden, DEFAULT_HIDDEN)):
            got = bool(getattr(cell.protection, attribute))
            want = default if declared == '-' else (declared == 'true')
            require(got == want, f'openpyxl {name}:{sheet_name}!{ref} {attribute}={got}, declared {declared}')
        checked_cells += 1
    return {'reader_sheets': checked_sheets, 'reader_cells': checked_cells}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    sheets = intent_table(folder / 'protections.tsv', FIELDS)
    cells = intent_table(folder / 'cell_protection.tsv', CELL_FIELDS)
    totals = verify_raw(folder, sheets, cells)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, sheets, cells))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R92 sheet-protection verification FAILED: {failure}')
        return 1
    message = (f'R92 independent ZIP/XML checks: {totals["protected_sheets"]} protected sheet(s) with all '
               f'fifteen action flags explicit, {totals["cells"]} cell protection record(s) verified')
    if 'reader_cells' in totals:
        message += (f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s) and '
                    f'{totals["reader_cells"]} cell(s)')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
