#!/usr/bin/env python3
"""Independent verification for the R89 SpreadsheetML cell font sizes (`CT_Font <sz val>`).

`XlsxStyledBook.CellStyle.fontSize` is written into the shared `<fonts>` table and referenced through
`<cellXfs><xf fontId>`. This verifier opens every workbook twice:

1. **raw OPC** — `xl/styles.xml` is parsed into font records, each cell's `s=` index is resolved through
   `cellXfs`, and the resolved font must carry the declared `<sz val>`, `<b/>` and `<color rgb>`; a cell
   that declares no size must resolve to the workbook default of 11 pt, and two cells with the same style
   must share one font record;
2. **openpyxl 3.1.5** — `cell.font.sz`, `cell.font.b` and `cell.font.color.rgb` are read through
   openpyxl's own object model, which models font size directly.

Host-only. openpyxl is a reader, not Excel. No recalculation, layout or rendering is involved.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = 8
DEFAULT_POINTS = 11.0


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
        size = font.find(f'{S}sz')
        colour = font.find(f'{S}color')
        records.append({
            'size': float(size.get('val')) if size is not None and size.get('val') else DEFAULT_POINTS,
            'bold': font.find(f'{S}b') is not None,
            'italic': font.find(f'{S}i') is not None,
            'colour': (colour.get('rgb') or '')[-6:].upper() if colour is not None else None,
        })
    return records


def xf_font_ids(root: ElementTree.Element) -> list[int]:
    xfs = root.find(f'{S}cellXfs')
    require(xfs is not None, 'missing xl/styles.xml <cellXfs>')
    return [int(xf.get('fontId') or 0) for xf in xfs.findall(f'{S}xf')]


def verify_raw(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    on_disk = {path.stem for path in folder.glob('*.xlsx')}
    require(on_disk == set(table), f'fonts.tsv names {sorted(table)} but the folder holds {sorted(on_disk)}')
    workbooks = cells = sized = 0
    for name, rows in table.items():
        with zipfile.ZipFile(folder / f'{name}.xlsx') as archive:
            styles = ElementTree.fromstring(archive.read('xl/styles.xml'))
            fonts = font_records(styles)
            font_ids = xf_font_ids(styles)
            workbook = ElementTree.fromstring(archive.read('xl/workbook.xml'))
            order = [sheet.get('name') for sheet in workbook.find(f'{S}sheets').findall(f'{S}sheet')]
            sheets: dict[str, ElementTree.Element] = {}
            for index, sheet_name in enumerate(order, start=1):
                sheets[sheet_name] = ElementTree.fromstring(
                    archive.read(f'xl/worksheets/sheet{index}.xml'))
        declared_sheets = []
        for row in rows:
            if row[1] not in declared_sheets:
                declared_sheets.append(row[1])
        require(declared_sheets == order,
                f'{name}: intent table sheets {declared_sheets} != workbook sheets {order}')
        for row in rows:
            _, sheet_name, ref, bold, italic, colour, size, _fill = row
            where = f'{name}:{sheet_name}!{ref}'
            cell = None
            for candidate in sheets[sheet_name].iter(f'{S}c'):
                if candidate.get('r') == ref:
                    cell = candidate
                    break
            require(cell is not None, f'{where}: cell missing from the worksheet')
            style_index = int(cell.get('s') or 0)
            require(style_index < len(font_ids), f'{where}: style index {style_index} outside cellXfs')
            font = fonts[font_ids[style_index]]
            expected_size = DEFAULT_POINTS if size == '-' else float(size)
            require(abs(float(font['size']) - expected_size) < 1e-9,
                    f'{where}: <sz val> resolved to {font["size"]}, declared {expected_size}')
            require(bool(font['bold']) == (bold == 'true'), f'{where}: <b/> mismatch')
            require(bool(font['italic']) == (italic == 'true'), f'{where}: <i/> mismatch')
            if colour == '-':
                require(font['colour'] is None, f'{where}: undeclared colour {font["colour"]!r}')
            else:
                require(font['colour'] == colour, f'{where}: colour {font["colour"]!r} != {colour!r}')
            cells += 1
            if size != '-':
                sized += 1
        workbooks += 1
    return {'workbooks': workbooks, 'cells': cells, 'sized': sized}


def verify_openpyxl(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    import warnings

    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    workbooks = cells = 0
    for name, rows in table.items():
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')  # "Workbook contains no default style" is expected
            book = openpyxl.load_workbook(folder / f'{name}.xlsx')
        for row in rows:
            _, sheet_name, ref, bold, italic, colour, size, _fill = row
            where = f'openpyxl {name}:{sheet_name}!{ref}'
            cell = book[sheet_name][ref]
            expected_size = DEFAULT_POINTS if size == '-' else float(size)
            require(cell.font.sz is not None and abs(float(cell.font.sz) - expected_size) < 1e-9,
                    f'{where}: openpyxl reports size {cell.font.sz}, declared {expected_size}')
            require(bool(cell.font.b) == (bold == 'true'), f'{where}: openpyxl bold mismatch')
            require(bool(cell.font.i) == (italic == 'true'), f'{where}: openpyxl italic mismatch')
            actual = None if cell.font.color is None or cell.font.color.rgb is None else str(cell.font.color.rgb)[-6:]
            if colour == '-':
                require(actual is None, f'{where}: openpyxl reports colour {actual!r}')
            else:
                require(actual == colour, f'{where}: openpyxl reports {actual!r} != {colour!r}')
            cells += 1
        workbooks += 1
    return {'workbooks': workbooks, 'cells': cells}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    table = intent_table(folder / 'fonts.tsv')
    totals = verify_raw(folder, table)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        reader = verify_openpyxl(folder, table)
        totals['reader_cells'] = reader['cells']
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R89 sheet font verification FAILED: {failure}')
        return 1
    message = (f'R89 independent ZIP/XML checks: {totals["workbooks"]} workbooks, {totals["cells"]} cells '
               f'({totals["sized"]} with a declared size) verified')
    if 'reader_cells' in totals:
        message += f'; openpyxl agreed on {totals["reader_cells"]} cells through its Font object'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
