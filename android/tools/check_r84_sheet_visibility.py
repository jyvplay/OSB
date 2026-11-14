#!/usr/bin/env python3
"""Independent verification for the R84 workbook sheet-visibility feature.

`XlsxStyledBook.StyledSheet` may declare `visibility`, which becomes `state="hidden"` or
`state="veryHidden"` on the `<sheet>` element of `xl/workbook.xml`; a visible sheet writes no `state`
attribute at all, because `visible` is the schema default. This verifier opens every workbook twice:

1. **raw OPC** — `xl/workbook.xml` is parsed directly: the `<sheet>` elements must appear in the declared
   order with the declared names, `sheetId`/`r:id` must keep their one-based numbering, `state` must be
   present exactly when the sheet is not visible and must carry exactly the declared token, no other
   attribute may appear, and a workbook must keep at least one visible sheet.
2. **openpyxl 3.1.5** — an independent implementation exposing `wb.sheetnames`, `ws.sheet_state` and the
   cell values of a hidden sheet (a hidden sheet is still a real sheet, and dropping its data would be a
   different feature).

Host-only. openpyxl is a reader, not Excel: agreement here is package-structure evidence, never tab-strip
behaviour, navigation, or a visual result.
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
INTENT_FIELDS = 5
TOKENS = {'visible', 'hidden', 'veryHidden'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        require(fields[3] in TOKENS, f'intent line {index} declares unknown state {fields[3]!r}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'name': fields[2],
            'state': fields[3],
            'cells': int(fields[4]),
        })
    require(table, 'intent table is empty')
    for name, sheets in table.items():
        require([entry['index'] for entry in sheets] == list(range(len(sheets))),
                f'{name}: sheet indices are not 0..{len(sheets) - 1}')
        require(any(entry['state'] == 'visible' for entry in sheets),
                f'{name}: intent declares no visible sheet, which this writer must refuse to produce')
    return table


def check_part(part: str, intent: list[dict], workbook: str) -> None:
    root = ElementTree.fromstring(part)
    views = root.find(f'{S}bookViews')
    visible = [entry['index'] for entry in intent if entry['state'] == 'visible']
    if len(visible) == len(intent):
        require(views is None,
                f'{workbook}: a workbook that hides nothing must not emit <bookViews>; that would change the '
                f'bytes of every legacy workbook')
    else:
        require(views is not None,
                f'{workbook}: a workbook with a hidden sheet must declare <bookViews> so the active tab is a '
                f'visible sheet')
        view = views.find(f'{S}workbookView')
        require(view is not None, f'{workbook}: <bookViews> carries no <workbookView>')
        require(view.get('activeTab') == str(visible[0]),
                f'{workbook}: activeTab {view.get("activeTab")!r} != first visible sheet index {visible[0]}')
        attributes = {key.split('}')[-1] if key.startswith('{') else key for key in view.attrib}
        require(attributes <= {'activeTab'},
                f'{workbook}: <workbookView> carries undeclared attributes '
                f'{sorted(attributes - {"activeTab"})}')
    sheets = root.find(f'{S}sheets')
    require(sheets is not None, f'{workbook}: workbook.xml has no <sheets>')
    entries = [child for child in sheets if child.tag == f'{S}sheet']
    require(len(entries) == len(intent),
            f'{workbook}: {len(entries)} <sheet> elements, declared {len(intent)}')
    for entry, expected in zip(entries, intent):
        require(entry.get('name') == expected['name'],
                f'{workbook}: sheet {expected["index"]} name {entry.get("name")!r} != declared '
                f'{expected["name"]!r}')
        require(entry.get('sheetId') == str(expected['index'] + 1),
                f'{workbook}: sheet {expected["index"]} sheetId {entry.get("sheetId")!r} != '
                f'{expected["index"] + 1}')
        require(entry.get(f'{R}id') == f'rSheet{expected["index"] + 1}',
                f'{workbook}: sheet {expected["index"]} relationship {entry.get(f"{R}id")!r} != '
                f'rSheet{expected["index"] + 1}')
        attributes = {key.split('}')[-1] if key.startswith('{') else key for key in entry.attrib}
        require(attributes <= {'name', 'sheetId', 'state', 'id'},
                f'{workbook}: sheet {expected["index"]} carries undeclared attributes '
                f'{sorted(attributes - {"name", "sheetId", "state", "id"})}')
        state = entry.get('state')
        if expected['state'] == 'visible':
            require(state is None,
                    f'{workbook}: sheet {expected["index"]} is visible but carries state={state!r}; the '
                    f'schema default must stay unwritten so legacy bytes are unchanged')
        else:
            require(state == expected['state'],
                    f'{workbook}: sheet {expected["index"]} state {state!r} != declared '
                    f'{expected["state"]!r}')


def check_with_openpyxl(path: Path, intent: list[dict]) -> None:
    import openpyxl  # imported lazily so the raw-OPC checks can run without it

    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        workbook = openpyxl.load_workbook(str(path))
    require(workbook.sheetnames == [entry['name'] for entry in intent],
            f'{path.name}: openpyxl sheet order {workbook.sheetnames} != declared '
            f'{[entry["name"] for entry in intent]}')
    for sheet, expected in zip(workbook.worksheets, intent):
        require(sheet.sheet_state == expected['state'],
                f'{path.name}/{sheet.title}: openpyxl sheet_state {sheet.sheet_state!r} != declared '
                f'{expected["state"]!r}')
        occupied = sum(1 for row in sheet.iter_rows() for cell in row if cell.value is not None)
        require(occupied == expected['cells'],
                f'{path.name}/{sheet.title}: openpyxl counts {occupied} non-empty cells, declared '
                f'{expected["cells"]} (hiding a sheet must not drop its data)')
    state_by_name = {entry['name']: entry['state'] for entry in intent}
    require(state_by_name.get(workbook.active.title) == 'visible',
            f'{path.name}: openpyxl resolves the active tab to {workbook.active.title!r}, whose declared state '
            f'is {state_by_name.get(workbook.active.title)!r}; a hidden active tab is repaired on open')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'visibility.tsv')
    sheets = 0
    for name, expected in intent.items():
        path = directory / f'{name}.xlsx'
        require(path.is_file(), f'missing workbook {path}')
        with zipfile.ZipFile(path) as archive:
            require('xl/workbook.xml' in archive.namelist(), f'{path.name}: missing xl/workbook.xml')
            check_part(archive.read('xl/workbook.xml').decode('utf-8'), expected, path.name)
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
            print(f'R84 SHEET VISIBILITY CHECK FAILED: openpyxl unavailable: {error}', file=sys.stderr)
            return 1
        print(f'openpyxl unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R84 SHEET VISIBILITY CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R84 sheet visibility: {report['workbooks']} workbooks and {report['sheets']} sheets verified with "
        "openpyxl 3.1.5 and raw OPC inspection; hidden sheets keep their cells. Not Excel tab-strip or "
        "navigation evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
