#!/usr/bin/env python3
"""Independent verification for the R90 SpreadsheetML font decorations (`CT_Font <u/>`, `<strike/>`).

`XlsxStyledBook.CellStyle.underline` and `.strike` are written into the shared `<fonts>` table and
referenced per cell through `<cellXfs><xf fontId>`. This verifier opens every workbook twice:

1. **raw OPC** — `xl/styles.xml` is parsed into font records, each cell's `s=` index is resolved through
   `cellXfs`, and the resolved font must carry `<u/>` and `<strike/>` exactly when they are declared; the
   CT_Font order `b < i < strike < u < sz < color` must hold for the elements this writer emits;
2. **openpyxl 3.1.5** — `cell.font.u` and `cell.font.strike` are read through openpyxl's object model,
   which models both directly. Any `<u>` element reads as underlined whatever its `val`: the underline
   *style* (single/double) is not modelled and is not claimed.

Host-only. openpyxl is a reader, not Excel. No recalculation, layout or rendering is involved.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = 10
FONT_ORDER = ['b', 'i', 'strike', 'u', 'sz', 'color']


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> dict[str, list[list[str]]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[list[str]]] = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        table.setdefault(parts[0], []).append(parts)
    require(table, f'{path.name} is empty')
    return table


def font_records(root: ElementTree.Element) -> list[dict[str, object]]:
    fonts = root.find(f'{S}fonts')
    require(fonts is not None, 'missing xl/styles.xml <fonts>')
    records = []
    for font in fonts.findall(f'{S}font'):
        colour = font.find(f'{S}color')
        records.append({
            'bold': font.find(f'{S}b') is not None,
            'italic': font.find(f'{S}i') is not None,
            'underline': font.find(f'{S}u') is not None,
            'strike': font.find(f'{S}strike') is not None,
            'colour': (colour.get('rgb') or '')[-6:].upper() if colour is not None else None,
            'order': [child.tag.replace(S, '') for child in font],
        })
    return records


def xf_font_ids(root: ElementTree.Element) -> list[int]:
    xfs = root.find(f'{S}cellXfs')
    require(xfs is not None, 'missing xl/styles.xml <cellXfs>')
    return [int(xf.get('fontId') or 0) for xf in xfs.findall(f'{S}xf')]


def cells_by_sheet(folder: Path, name: str) -> tuple[dict[str, list[dict]], dict[str, ElementTree.Element]]:
    with zipfile.ZipFile(folder / f'{name}.xlsx') as archive:
        try:
            if 'xl/styles.xml' not in archive.namelist():
                raise Fail(f'{name}.xlsx has no xl/styles.xml part')
        except zipfile.BadZipFile as error:
            raise Fail(f'{name}.xlsx is not a ZIP package: {error}') from error
        styles = ElementTree.fromstring(archive.read('xl/styles.xml'))
        fonts = font_records(styles)
        font_ids = xf_font_ids(styles)
        workbook = ElementTree.fromstring(archive.read('xl/workbook.xml'))
        order = [sheet.get('name') for sheet in workbook.find(f'{S}sheets').findall(f'{S}sheet')]
        sheets = {}
        for index, sheet_name in enumerate(order, start=1):
            sheets[sheet_name] = ElementTree.fromstring(archive.read(f'xl/worksheets/sheet{index}.xml'))
    resolved: dict[str, list[dict]] = {}
    for sheet_name, sheet in sheets.items():
        cells = []
        for cell in sheet.iter(f'{S}c'):
            style_index = int(cell.get('s') or 0)
            require(style_index < len(font_ids), f'{name}:{sheet_name}: style {style_index} outside cellXfs')
            cells.append({'ref': cell.get('r'), 'font': fonts[font_ids[style_index]]})
        resolved[sheet_name] = cells
    return resolved, sheets


def verify_raw(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    on_disk = {path.stem for path in folder.glob('*.xlsx')}
    require(on_disk == set(table),
            f'decorations_xlsx.tsv names {sorted(table)} but the folder holds {sorted(on_disk)}')
    workbooks = cells = decorated = 0
    for name, rows in table.items():
        resolved, _ = cells_by_sheet(folder, name)
        for row in rows:
            _, sheet_name, ref, bold, italic, underline, strike, colour, _size, _fill = row
            where = f'{name}:{sheet_name}!{ref}'
            require(sheet_name in resolved, f'{where}: sheet missing from the workbook')
            entry = next((c for c in resolved[sheet_name] if c['ref'] == ref), None)
            require(entry is not None, f'{where}: cell missing from the worksheet')
            font = entry['font']
            require(bool(font['underline']) == (underline == 'true'), f'{where}: <u/> mismatch')
            require(bool(font['strike']) == (strike == 'true'), f'{where}: <strike/> mismatch')
            require(bool(font['bold']) == (bold == 'true'), f'{where}: <b/> mismatch')
            require(bool(font['italic']) == (italic == 'true'), f'{where}: <i/> mismatch')
            if colour == '-':
                require(font['colour'] is None, f'{where}: undeclared colour {font["colour"]!r}')
            else:
                require(font['colour'] == colour, f'{where}: colour {font["colour"]!r} != {colour!r}')
            order = [element for element in font['order'] if element in FONT_ORDER]
            require(order == sorted(order, key=FONT_ORDER.index),
                    f'{where}: CT_Font order violated: {font["order"]}')
            cells += 1
            if underline == 'true' or strike == 'true':
                decorated += 1
        workbooks += 1
    return {'workbooks': workbooks, 'cells': cells, 'decorated': decorated}


def verify_openpyxl(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    import warnings

    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    workbooks = cells = 0
    for name, rows in table.items():
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')  # "Workbook contains no default style" is expected
            book = openpyxl.load_workbook(folder / f'{name}.xlsx')
        for row in rows:
            _, sheet_name, ref, bold, italic, underline, strike, colour, _size, _fill = row
            where = f'openpyxl {name}:{sheet_name}!{ref}'
            cell = book[sheet_name][ref]
            require(bool(cell.font.u) == (underline == 'true'),
                    f'{where}: openpyxl reports underline={cell.font.u!r}')
            require(bool(cell.font.strike) == (strike == 'true'),
                    f'{where}: openpyxl reports strike={cell.font.strike!r}')
            require(bool(cell.font.b) == (bold == 'true'), f'{where}: openpyxl bold mismatch')
            require(bool(cell.font.i) == (italic == 'true'), f'{where}: openpyxl italic mismatch')
            actual = None if cell.font.color is None or cell.font.color.rgb is None else str(cell.font.color.rgb)[-6:]
            if colour == '-':
                require(actual is None, f'{where}: openpyxl reports colour {actual!r}')
            else:
                require(actual == colour, f'{where}: openpyxl reports {actual!r} != {colour!r}')
            cells += 1
        workbooks += 1
    return {'reader_workbooks': workbooks, 'reader_cells': cells}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    table = intent_table(folder / 'decorations_xlsx.tsv')
    totals = verify_raw(folder, table)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, table))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R90 sheet decoration verification FAILED: {failure}')
        return 1
    message = (f'R90 independent ZIP/XML checks: {totals["workbooks"]} workbooks, {totals["cells"]} cells '
               f'({totals["decorated"]} with a declared decoration) verified')
    if 'reader_cells' in totals:
        message += f'; openpyxl agreed on {totals["reader_cells"]} cells through its Font object'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
