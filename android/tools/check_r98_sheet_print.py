#!/usr/bin/env python3
"""Independent verification for R98 fit-to-page and page numbering.

Two layers, both mandatory when openpyxl is present:

1. **raw OPC** — a sheet that fits to page must carry `<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>`, and a
   sheet that does not must carry no `<sheetPr>` unless it also has a tab colour; `pageSetup` may hold
   `fitToWidth`, `fitToHeight`, `firstPageNumber` and `useFirstPageNumber` only when the sheet's flag is on;
   element order inside the sheet part is `sheetPr … printOptions, pageMargins, pageSetup`; and a workbook that
   declares no layout at all keeps exactly the bytes it had before this feature;
2. **openpyxl 3.1.5** — `worksheet.page_setup.fitToWidth/fitToHeight/firstPageNumber/useFirstPageNumber`,
   `worksheet.sheet_properties.pageSetUpPr.fitToPage` and `worksheet.print_area`/`print_title_rows`/
   `print_title_cols` must agree with the probe's intent, including the empty shapes openpyxl reports for a
   sheet that declares nothing.

The invariant this lane exists to protect: fit counts are inert in Excel unless `pageSetUpPr@fitToPage` is on, so
a workbook that carries one without the other is a workbook that lies about its own pagination. Both directions
are checked — no counts without the flag, and the flag honoured when it is present.

Host-only. Nothing here paginates a page, and no printer, PDF exporter or spreadsheet engine is involved.

Utility: `python3 tools/check_r98_sheet_print.py testing/build/r98/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
FIELDS = 17
SETUP_ORDER = ['paperSize', 'scale', 'firstPageNumber', 'fitToWidth', 'fitToHeight', 'orientation',
               'useFirstPageNumber']
ORIENTATIONS = {'portrait', 'landscape'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_rows(path: Path) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows = []
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def parse_row(row: list[str]) -> dict[str, object]:
    (sheet, has, fit, width, height, first, use_first, orientation, paper, scale,
     area, title_rows, title_cols, has_options, has_margins, has_setup, has_tab) = row
    for name, value in (('has', has), ('fitToPage', fit), ('useFirstPageNumber', use_first),
                        ('hasOptions', has_options), ('hasMargins', has_margins),
                        ('hasSetup', has_setup), ('hasTab', has_tab)):
        require(value in {'0', '1'}, f'{sheet}: {name}={value!r} is not 0/1')
    ints = {}
    for name, value in (('fitToWidth', width), ('fitToHeight', height), ('firstPageNumber', first),
                        ('paperSize', paper), ('scale', scale)):
        ints[name] = None if value == '-' else int(value)
    return {
        'sheet': sheet, 'has': has == '1', 'fitToPage': fit == '1',
        'fitToWidth': ints['fitToWidth'], 'fitToHeight': ints['fitToHeight'],
        'firstPageNumber': ints['firstPageNumber'], 'useFirstPageNumber': use_first == '1',
        'orientation': None if orientation == '-' else orientation,
        'paperSize': ints['paperSize'], 'scale': ints['scale'],
        'area': None if area == '-' else area,
        'titles': (None if title_rows == '-' else title_rows,
                   None if title_cols == '-' else title_cols),
        'hasOptions': has_options == '1', 'hasMargins': has_margins == '1',
        'hasSetup': has_setup == '1', 'hasTab': has_tab == '1',
    }


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


def sheet_root(folder: Path, name: str, part: str) -> ElementTree.Element:
    with zipfile.ZipFile(folder / name) as archive:
        return ElementTree.fromstring(archive.read(part))


def verify_raw(folder: Path, rows: list[dict[str, object]]) -> dict[str, int]:
    declared = {row['sheet'] for row in rows}
    parts = sheet_parts(folder, 'r98.xlsx')
    require(declared == set(parts), f'print.tsv describes {sorted(declared)}, the workbook has {sorted(parts)}')
    fit_sheets = 0
    plain_sheets = 0
    for row in rows:
        sheet = row['sheet']
        root = sheet_root(folder, 'r98.xlsx', parts[sheet])
        elements = [child.tag.split('}')[-1] for child in root]
        sheet_pr = root.find(f'{S}sheetPr')
        wants_counts = row['fitToWidth'] is not None or row['fitToHeight'] is not None
        if wants_counts:
            require(row['fitToPage'],
                    f'{sheet}: fit counts without pageSetUpPr@fitToPage — inert in Excel, refused by the model')
        if row['hasTab'] or row['fitToPage']:
            require(sheet_pr is not None, f'{sheet}: declares a tab colour or fit flag but has no <sheetPr>')
        else:
            require(sheet_pr is None, f'{sheet}: has no tab colour and no fit flag, yet carries a <sheetPr>')
        if sheet_pr is not None:
            children = [child.tag.split('}')[-1] for child in sheet_pr]
            expected_children = (['tabColor'] if row['hasTab'] else []) + \
                                (['pageSetUpPr'] if row['fitToPage'] else [])
            require(children == expected_children,
                    f'{sheet}: sheetPr children {children} != {expected_children} (CT_SheetPr order)')
            if row['fitToPage']:
                flag = sheet_pr.find(f'{S}pageSetUpPr')
                require(flag.get('fitToPage') in {'1', 'true'},
                        f'{sheet}: pageSetUpPr does not ask to fit to page: {flag.attrib}')
        setup = root.find(f'{S}pageSetup')
        if row['hasSetup']:
            require(setup is not None, f'{sheet}: declares pageSetup attributes but has no <pageSetup>')
            names = [key for key in setup.attrib]
            order = [key for key in SETUP_ORDER if key in names]
            require(names == order, f'{sheet}: pageSetup attributes {names} are not in schema order {order}')
            for name, value in (('paperSize', row['paperSize']), ('scale', row['scale']),
                                ('firstPageNumber', row['firstPageNumber']),
                                ('fitToWidth', row['fitToWidth']), ('fitToHeight', row['fitToHeight'])):
                if value is None:
                    require(name not in setup.attrib, f'{sheet}: {name} was written without being declared')
                else:
                    require(setup.get(name) == str(value),
                            f'{sheet}: {name}={setup.get(name)!r} != {value!r}')
            if row['useFirstPageNumber']:
                require(setup.get('useFirstPageNumber') in {'1', 'true'},
                        f'{sheet}: useFirstPageNumber was declared but not written')
            else:
                require('useFirstPageNumber' not in setup.attrib,
                        f'{sheet}: useFirstPageNumber was written without being declared')
            if row['orientation'] is not None:
                require(setup.get('orientation') in ORIENTATIONS, f'{sheet}: orientation {setup.get("orientation")!r}')
                require(setup.get('orientation') == row['orientation'], f'{sheet}: orientation mismatch')
        else:
            require(setup is None, f'{sheet}: has no pageSetup attributes yet carries a <pageSetup>')
        require(('printOptions' in elements) == row['hasOptions'],
                f'{sheet}: printOptions presence disagrees with the intent')
        require(('pageMargins' in elements) == row['hasMargins'],
                f'{sheet}: pageMargins presence disagrees with the intent')
        order = [name for name in ('sheetPr', 'printOptions', 'pageMargins', 'pageSetup') if name in elements]
        require(order == [name for name in elements if name in
                          ('sheetPr', 'printOptions', 'pageMargins', 'pageSetup')],
                f'{sheet}: CT_Worksheet order is wrong: {elements}')
        if row['fitToPage']:
            fit_sheets += 1
        else:
            plain_sheets += 1

    # A workbook that declares nothing must not have grown any of it.
    with zipfile.ZipFile(folder / 'r98-plain.xlsx') as archive:
        plain = archive.read('xl/worksheets/sheet1.xml').decode('utf-8')
        plain_book = archive.read('xl/workbook.xml').decode('utf-8')
    for needle in ('sheetPr', 'pageSetup', 'printOptions', 'pageMargins', 'definedName'):
        require(needle not in plain and needle not in plain_book,
                f'the no-layout workbook gained {needle}')
    return {'fit_sheets': fit_sheets, 'plain_sheets': plain_sheets}


def verify_openpyxl(folder: Path, rows: list[dict[str, object]]) -> dict[str, int]:
    import warnings

    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    checked = 0
    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        book = openpyxl.load_workbook(folder / 'r98.xlsx')
    for row in rows:
        sheet = book[row['sheet']]
        properties = sheet.sheet_properties.pageSetUpPr
        flag = None if properties is None else properties.fitToPage
        require((flag is True) == row['fitToPage'],
                f'openpyxl {row["sheet"]}: fitToPage={flag!r}, declared {row["fitToPage"]}')
        setup = sheet.page_setup
        for name in ('fitToWidth', 'fitToHeight', 'firstPageNumber', 'paperSize', 'scale'):
            got = getattr(setup, name)
            want = row[name]
            require(got == want, f'openpyxl {row["sheet"]}: {name}={got!r} != {want!r}')
        if row['useFirstPageNumber']:
            require(bool(setup.useFirstPageNumber), f'openpyxl {row["sheet"]}: useFirstPageNumber is false')
        else:
            require(not setup.useFirstPageNumber, f'openpyxl {row["sheet"]}: useFirstPageNumber was invented')
        if row['orientation'] is not None:
            require(setup.orientation == row['orientation'],
                    f'openpyxl {row["sheet"]}: orientation={setup.orientation!r}')
        # openpyxl exposes the two records through the sheet, with the same normalisation Excel uses.
        area = sheet.print_area or None
        if row['area'] is None:
            require(area is None, f'openpyxl {row["sheet"]}: invented a print area {area!r}')
        else:
            require(area is not None, f'openpyxl {row["sheet"]}: lost the print area {row["area"]!r}')
            require(area.replace('$', '').split('!')[-1].replace("'", '') == row['area'],
                    f'openpyxl {row["sheet"]}: print area {area!r} != {row["area"]!r}')
        want_rows, want_cols = row['titles']
        got_rows = sheet.print_title_rows.replace('$', '') if sheet.print_title_rows else None
        got_cols = sheet.print_title_cols.replace('$', '') if sheet.print_title_cols else None
        require(got_rows == want_rows, f'openpyxl {row["sheet"]}: title rows {got_rows!r} != {want_rows!r}')
        require(got_cols == want_cols, f'openpyxl {row["sheet"]}: title cols {got_cols!r} != {want_cols!r}')
        checked += 1
    return {'reader_sheets': checked}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    rows = [parse_row(row) for row in intent_rows(folder / 'print.tsv')]
    totals = verify_raw(folder, rows)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, rows))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R98 fit-to-page verification FAILED: {failure}')
        return 1
    message = (f'R98 fit to page: {totals["fit_sheets"]} sheet(s) with pageSetUpPr@fitToPage and its counts, '
               f'{totals["plain_sheets"]} without')
    if 'reader_sheets' in totals:
        message += f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
