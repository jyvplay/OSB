#!/usr/bin/env python3
"""Independent verification for the R88 sheet-view gridline packages (`CT_SheetView/@showGridLines`).

`XlsxStyledBook.StyledSheet.showGridLines` is tri-state: `null` leaves Excel's default (gridlines shown)
unwritten, `false` writes `showGridLines="0"` and `true` writes `showGridLines="1"`. This verifier opens
every workbook twice:

1. **raw OPC** — `xl/worksheets/sheetN.xml` must contain no `sheetViews` element when the intent table
   says `-`; otherwise exactly one `sheetView` element must carry the declared `showGridLines` value, in
   the same element as `zoomScale` when both are declared.
2. **openpyxl 3.1.5** — `worksheet.sheet_view.showGridLines` is independently tri-state
   (`None` for "not written", `False` for `"0"`, `True` for `"1"`), and `sheet_view.zoomScale` and
   `sheet_state` must agree with the same table.

Host-only. openpyxl is a reader, not Excel: agreement here is package-structure evidence, never rendering.
The rendered consequences are checked by the exceljs lane, which models the same attribute.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = 7


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'bad intent row ({len(parts)} fields): {line!r}')
        name, index, sheet, grid, zoom, freeze, state = parts
        table.setdefault(name, []).append({
            'index': int(index), 'sheet': sheet,
            'grid': {'-': None, 'false': False, 'true': True}[grid],
            'zoom': None if zoom == '-' else int(zoom),
            'freeze': int(freeze), 'state': state,
        })
    return table


def verify_raw(folder: Path, table: dict[str, list[dict]]) -> dict[str, int]:
    workbooks = sheets = suppressed = 0
    for name, rows in table.items():
        path = folder / f'{name}.xlsx'
        require(path.is_file(), f'missing workbook {path}')
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
        for row in rows:
            part = f'xl/worksheets/sheet{row["index"] + 1}.xml'
            require(part in names, f'{name}: missing {part}')
            with zipfile.ZipFile(path) as archive:
                root = ElementTree.fromstring(archive.read(part))
            views = root.find(f'{S}sheetViews')
            where = f'{name}:{row["sheet"]}'
            if row['grid'] is None and row['zoom'] is None and not row['freeze']:
                require(views is None, f'{where}: a sheet with no view option must not emit sheetViews')
                sheets += 1
                continue
            require(views is not None, f'{where}: missing sheetViews')
            elements = views.findall(f'{S}sheetView')
            require(len(elements) == 1, f'{where}: {len(elements)} sheetView elements, expected 1')
            view = elements[0]
            require(view.get('workbookViewId') == '0', f'{where}: workbookViewId must be "0"')
            attribute = view.get('showGridLines')
            expected = row['grid']
            if expected is None:
                require(attribute is None, f'{where}: unexpected showGridLines={attribute!r}')
            else:
                require(attribute == ('1' if expected else '0'),
                        f'{where}: showGridLines={attribute!r}, expected {"1" if expected else "0"!r}')
                suppressed += 0 if expected else 1
            if row['zoom'] is None:
                require(view.get('zoomScale') is None, f'{where}: unexpected zoomScale')
            else:
                require(view.get('zoomScale') == str(row['zoom']),
                        f'{where}: zoomScale={view.get("zoomScale")!r}, expected {row["zoom"]}')
            pane = view.find(f'{S}pane')
            if row['freeze']:
                require(pane is not None and pane.get('state') == 'frozen',
                        f'{where}: a frozen sheet needs a frozen pane')
                require(pane.get('ySplit') == str(row['freeze']),
                        f'{where}: ySplit={pane.get("ySplit")!r}, expected {row["freeze"]}')
            else:
                require(pane is None, f'{where}: unexpected pane on an unfrozen sheet')
            sheets += 1
        workbooks += 1
    return {'workbooks': workbooks, 'sheets': sheets, 'grid_off': suppressed}


def verify_openpyxl(folder: Path, table: dict[str, list[dict]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    workbooks = sheets = 0
    for name, rows in table.items():
        import warnings

        with warnings.catch_warnings():
            warnings.simplefilter('ignore')  # "Workbook contains no default style" is expected
            book = openpyxl.load_workbook(folder / f'{name}.xlsx')
        require([sheet.title for sheet in book.worksheets] == [row['sheet'] for row in rows],
                f'{name}: openpyxl sheet order mismatch')
        for row, sheet in zip(rows, book.worksheets):
            where = f'openpyxl {name}:{row["sheet"]}'
            require(sheet.sheet_view.showGridLines == row['grid'],
                    f'{where}: showGridLines={sheet.sheet_view.showGridLines!r}, '
                    f'expected {row["grid"]!r}')
            require(sheet.sheet_view.zoomScale == row['zoom'],
                    f'{where}: zoomScale={sheet.sheet_view.zoomScale!r}, expected {row["zoom"]!r}')
            require(sheet.sheet_state == row['state'],
                    f'{where}: state={sheet.sheet_state!r}, expected {row["state"]!r}')
            sheets += 1
        workbooks += 1
    return {'workbooks': workbooks, 'sheets': sheets}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    table = intent_table(folder / 'grid.tsv')
    require(table, 'the intent table is empty')
    # A verifier that only walks the table would silently ignore a workbook the table forgot.
    on_disk = {path.stem for path in folder.glob('*.xlsx')}
    require(on_disk == set(table),
            f'grid.tsv names {sorted(table)} but the folder holds {sorted(on_disk)}')
    totals = verify_raw(folder, table)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        reader = verify_openpyxl(folder, table)
        totals['reader_workbooks'] = reader['workbooks']
        totals['reader_sheets'] = reader['sheets']
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R88 sheet gridline verification FAILED: {failure}')
        return 1
    message = (f'R88 independent ZIP/XML checks: {totals["workbooks"]} workbooks, {totals["sheets"]} '
               f'sheets ({totals["grid_off"]} with gridlines suppressed) verified')
    if 'reader_workbooks' in totals:
        message += (f'; openpyxl agreed on {totals["reader_sheets"]} sheets in '
                    f'{totals["reader_workbooks"]} workbooks')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
