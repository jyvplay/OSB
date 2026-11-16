#!/usr/bin/env python3
"""Independent verification for R94 print layout (`<printOptions>`, `<pageMargins>`, `<pageSetup>`).

Two layers:

1. **raw OPC** — a sheet that declares no print layout must carry none of the three elements (and its bytes stay
   as they were); a sheet that declares one must carry them in `CT_Worksheet` order (`printOptions`,
   `pageMargins`, `pageSetup`), with `printOptions` holding only the switches set to `true` (in schema order) and
   `pageMargins` holding **all six** required attributes as plain decimals, and `pageSetup` carrying only the
   declared `paperSize`/`scale`/`orientation` in schema order;
2. **openpyxl 3.1.5** — `worksheet.page_margins`, `worksheet.print_options` and `worksheet.page_setup` must agree,
   including the defaults openpyxl invents for a sheet that declares nothing.

A print record is a *record*: it says nothing about what a printer, a print preview or a PDF exporter will do
with the page. Host-only.

Utility: `python3 tools/check_r94_sheet_print.py testing/build/r94/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import re
import sys
import warnings
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
FIELDS = 13
FLAG_ORDER = ['horizontalCentered', 'verticalCentered', 'headings', 'gridLines']
MARGIN_ORDER = ['left', 'right', 'top', 'bottom', 'header', 'footer']
SETUP_ORDER = ['paperSize', 'scale', 'orientation']
DEFAULT_MARGINS = {'left': 0.75, 'right': 0.75, 'top': 1.0, 'bottom': 1.0, 'header': 0.5, 'footer': 0.5}
DECIMAL = re.compile(r'^\d+(\.\d+)?$')
ORIENTATIONS = {'portrait', 'landscape'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path, fields: int) -> list[list[str]]:
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


def sheet_names(folder: Path, name: str) -> set[str]:
    with zipfile.ZipFile(folder / name) as archive:
        book = ElementTree.fromstring(archive.read('xl/workbook.xml'))
    return {sheet.get('name') for sheet in book.find(f'{S}sheets')}


def parts_of(folder: Path, name: str) -> dict[str, str]:
    with zipfile.ZipFile(folder / name) as archive:
        book = ElementTree.fromstring(archive.read('xl/workbook.xml'))
        rels = ElementTree.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
    targets = {item.get('Id'): item.get('Target') for item in rels}
    result = {}
    for sheet in book.find(f'{S}sheets'):
        target = (targets[sheet.get(f'{R}id')] or '').lstrip('/')
        result[sheet.get('name')] = target if target.startswith('xl/') else f'xl/{target}'
    return result


def verify_raw(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    declared_files = {row[0] for row in rows}
    on_disk = {path.name for path in folder.glob('*.xlsx')}
    require(declared_files == on_disk,
            f'the intent table and the folder disagree: declared {sorted(declared_files)}, '
            f'present {sorted(on_disk)}')
    # Every sheet of every declared workbook must be described: a dropped row would otherwise leave a sheet
    # unchecked while the file set still agrees.
    for name in sorted(declared_files):
        described = {row[1] for row in rows if row[0] == name}
        present = sheet_names(folder, name)
        require(described == present,
                f'{name}: the intent table describes {sorted(described)}, the workbook has {sorted(present)}')
    with_print = 0
    without = 0
    for name, sheet_name, has, flags_field, left, right, top, bottom, header, footer, orientation, paper, scale in rows:
        require(has in {'0', '1'}, f'{name}:{sheet_name} has={has!r}')
        part = parts_of(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        with zipfile.ZipFile(folder / name) as archive:
            raw = archive.read(part).decode('utf-8')
        root = ElementTree.fromstring(raw)
        nodes = {child.tag.split('}')[-1]: child for child in root}
        present = [element for element in ('printOptions', 'pageMargins', 'pageSetup') if element in nodes]
        if has == '0':
            require(not present, f'{name}:{sheet_name} declares no print layout but carries {present}')
            without += 1
            continue
        require('pageMargins' in nodes,
                f'{name}:{sheet_name} declares a print layout without the required <pageMargins>')
        for earlier, later in (('printOptions', 'pageMargins'), ('pageMargins', 'pageSetup'),
                               ('printOptions', 'pageSetup')):
            if earlier in nodes and later in nodes:
                require(list(nodes).index(earlier) < list(nodes).index(later),
                        f'{name}:{sheet_name} puts <{later}> before <{earlier}>')

        flags = [] if flags_field == '-' else flags_field.split(',')
        require(flags == [item for item in FLAG_ORDER if item in flags],
                f'{name}:{sheet_name} printOptions flags {flags} are not in schema order')
        if flags:
            require('printOptions' in nodes, f'{name}:{sheet_name} declares switches but no <printOptions>')
            node = nodes['printOptions']
            require(list(node.attrib) == flags,
                    f'{name}:{sheet_name} printOptions attributes {list(node.attrib)} != {flags}')
            require(all(value in {'1', 'true'} for value in node.attrib.values()),
                    f'{name}:{sheet_name} a printOptions switch is false')
        else:
            require('printOptions' not in nodes, f'{name}:{sheet_name} wrote an empty <printOptions>')

        node = nodes['pageMargins']
        require(list(node.attrib) == MARGIN_ORDER,
                f'{name}:{sheet_name} pageMargins attributes {list(node.attrib)} != {MARGIN_ORDER}')
        for attribute, value in zip(MARGIN_ORDER, (left, right, top, bottom, header, footer)):
            text = node.get(attribute)
            require(DECIMAL.match(text or '') is not None,
                    f'{name}:{sheet_name} margin {attribute}={text!r} is not a plain decimal')
            require(abs(float(text) - float(value)) < 1e-9,
                    f'{name}:{sheet_name} margin {attribute}={text} != {value}')

        setup = [item for item, value in zip(SETUP_ORDER, (paper, scale, orientation)) if value != '-']
        require(setup == [item for item in SETUP_ORDER if item in setup],
                f'{name}:{sheet_name} pageSetup attributes {setup} are not in schema order')
        if setup:
            require('pageSetup' in nodes, f'{name}:{sheet_name} declares pageSetup attributes but no <pageSetup>')
            node = nodes['pageSetup']
            require(list(node.attrib) == setup,
                    f'{name}:{sheet_name} pageSetup attributes {list(node.attrib)} != {setup}')
            for attribute, value in (('paperSize', paper), ('scale', scale), ('orientation', orientation)):
                if value != '-':
                    require(node.get(attribute) == value,
                            f'{name}:{sheet_name} {attribute}={node.get(attribute)!r} != {value!r}')
            if orientation != '-':
                require(orientation in ORIENTATIONS, f'{name}:{sheet_name} orientation {orientation!r}')
        else:
            require('pageSetup' not in nodes, f'{name}:{sheet_name} wrote an empty <pageSetup>')
        with_print += 1
    return {'print_sheets': with_print, 'plain_sheets': without}


def verify_openpyxl(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    checked = 0
    for name, sheet_name, has, flags_field, left, right, top, bottom, header, footer, orientation, paper, scale in rows:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            book = openpyxl.load_workbook(folder / name)
        sheet = book[sheet_name]
        margins = sheet.page_margins
        declared = dict(zip(MARGIN_ORDER, (left, right, top, bottom, header, footer)))
        want = DEFAULT_MARGINS if has == '0' else {key: float(value) for key, value in declared.items()}
        for attribute in MARGIN_ORDER:
            got = float(getattr(margins, attribute))
            require(abs(got - want[attribute]) < 1e-9,
                    f'openpyxl {name}:{sheet_name} margin {attribute}={got} != {want[attribute]}')
        flags = set() if has == '0' or flags_field == '-' else set(flags_field.split(','))
        for attribute in FLAG_ORDER:
            got = bool(getattr(sheet.print_options, attribute))
            require(got == (attribute in flags),
                    f'openpyxl {name}:{sheet_name} printOptions {attribute}={got}, declared {attribute in flags}')
        setup = sheet.page_setup
        expected = {'orientation': None, 'paperSize': None, 'scale': None} if has == '0' else {
            'orientation': None if orientation == '-' else orientation,
            'paperSize': None if paper == '-' else int(paper),
            'scale': None if scale == '-' else int(scale),
        }
        for attribute, value in expected.items():
            got = getattr(setup, attribute)
            require(got == value, f'openpyxl {name}:{sheet_name} {attribute}={got!r} != {value!r}')
        checked += 1
    return {'reader_sheets': checked}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    rows = intent_table(folder / 'print.tsv', FIELDS)
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
        print(f'R94 print-layout verification FAILED: {failure}')
        return 1
    message = (f'R94 print layout: {totals["print_sheets"]} sheet(s) with printOptions/pageMargins/pageSetup in '
               f'order, {totals["plain_sheets"]} sheet(s) with no print record')
    if 'reader_sheets' in totals:
        message += f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
