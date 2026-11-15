#!/usr/bin/env python3
"""Independent verification for the R86 spreadsheet tab-colour and workbook-view workbooks.

`XlsxStyledBook` may emit:

* `<sheetPr><tabColor rgb="FFRRGGBB"/></sheetPr>` as the first child of a worksheet, only when
  `StyledSheet.tabRgb` is declared;
* `<sheetViews><sheetView workbookViewId="0" zoomScale="N"/></sheetViews>` when `StyledSheet.zoomScale`
  is declared, sharing the one `sheetView` with a frozen `<pane>` when both are declared;
* `<bookViews><workbookView showSheetTabs="0|1" firstSheet="N" activeTab="M"/></bookViews>` when a view
  option is declared or a sheet is hidden, with `activeTab` only in the hidden case.

This verifier opens every workbook twice:

1. **raw OPC** — `xl/workbook.xml` and each `xl/worksheets/sheetN.xml` are parsed directly: `<bookViews>`
   must be present exactly when a view option is declared or a sheet is hidden, `<workbookView>` may carry
   only `showSheetTabs`/`firstSheet`/`activeTab` with exactly the declared values, `activeTab` must name the
   first visible sheet, `sheetPr` must be the worksheet's first child and hold exactly one `tabColor` with
   the declared ARGB value, `sheetView` must carry exactly the declared `zoomScale`, and a frozen pane must
   keep its `ySplit`.
2. **openpyxl 3.1.5** — an independent implementation exposing `wb.views` (`showSheetTabs`, `firstSheet`,
   `activeTab`), `ws.sheet_properties.tabColor.rgb`, `ws.sheet_view.zoomScale` and `ws.sheet_state`. An
   absent `<bookViews>` reads as an empty `views` list, which is asserted rather than papered over.

Host-only. openpyxl is a reader, not Excel: agreement here is package-structure evidence, never the visible
tab strip, tab navigation, repair prompts or a visual result.
"""
from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
INTENT_FIELDS = 10
STATES = ('visible', 'hidden', 'veryHidden')
HEX6 = re.compile(r'^[0-9A-F]{6}$')


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
        require(fields[3] in STATES, f'intent line {index} declares unknown state {fields[3]!r}')
        require(fields[7] in ('-', 'true', 'false'), f'intent line {index} has a bad showSheetTabs '
                                                    f'{fields[7]!r}')
        if fields[4] != '-':
            require(bool(HEX6.match(fields[4])), f'intent line {index} declares bad tabRgb {fields[4]!r}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'name': fields[2],
            'state': fields[3],
            'tab_rgb': None if fields[4] == '-' else fields[4],
            'zoom': None if fields[5] == '-' else int(fields[5]),
            'cells': int(fields[6]),
            'show_tabs': None if fields[7] == '-' else fields[7] == 'true',
            'first_sheet': None if fields[8] == '-' else int(fields[8]),
            'freeze_rows': int(fields[9]),
        })
    require(table, 'intent table is empty')
    for name, sheets in table.items():
        require([entry['index'] for entry in sheets] == list(range(len(sheets))),
                f'{name}: sheet indices are not 0..{len(sheets) - 1}')
        require(any(entry['state'] == 'visible' for entry in sheets),
                f'{name}: intent declares no visible sheet, which this writer must refuse to produce')
        for key in ('show_tabs', 'first_sheet'):
            require(len({entry[key] for entry in sheets}) == 1,
                    f'{name}: the workbook-level {key} is not constant across its sheet rows')
    return table


def attr_names(node) -> set[str]:
    return {key.split('}')[-1] if key.startswith('{') else key for key in node.attrib}


def check_workbook(part: str, intent: list[dict], workbook: str) -> None:
    root = ElementTree.fromstring(part)
    hidden = [entry['index'] for entry in intent if entry['state'] != 'visible']
    visible = [entry['index'] for entry in intent if entry['state'] == 'visible']
    show_tabs = intent[0]['show_tabs']
    first_sheet = intent[0]['first_sheet']
    views = root.find(f'{S}bookViews')
    expected_views = bool(hidden) or show_tabs is not None or first_sheet is not None
    require((views is not None) == expected_views,
            f'{workbook}: <bookViews> presence {views is not None} != expected {expected_views} '
            f'(hidden={bool(hidden)}, showSheetTabs={show_tabs}, firstSheet={first_sheet})')
    if views is not None:
        entries = views.findall(f'{S}workbookView')
        require(len(entries) == 1, f'{workbook}: <bookViews> must hold exactly one <workbookView>')
        view = entries[0]
        require(attr_names(view) <= {'showSheetTabs', 'firstSheet', 'activeTab'},
                f'{workbook}: <workbookView> carries undeclared attributes '
                f'{sorted(attr_names(view) - {"showSheetTabs", "firstSheet", "activeTab"})}')
        if show_tabs is None:
            require(view.get('showSheetTabs') is None,
                    f'{workbook}: an undeclared showSheetTabs must stay unwritten, found '
                    f'{view.get("showSheetTabs")!r}')
        else:
            expected = '1' if show_tabs else '0'
            require(view.get('showSheetTabs') == expected,
                    f'{workbook}: showSheetTabs {view.get("showSheetTabs")!r} != {expected!r}')
        if first_sheet is None:
            require(view.get('firstSheet') is None,
                    f'{workbook}: an undeclared firstSheet must stay unwritten')
        else:
            require(view.get('firstSheet') == str(first_sheet),
                    f'{workbook}: firstSheet {view.get("firstSheet")!r} != declared {first_sheet}')
        if hidden:
            require(view.get('activeTab') == str(visible[0]),
                    f'{workbook}: activeTab {view.get("activeTab")!r} != first visible sheet index '
                    f'{visible[0]}')
        else:
            require(view.get('activeTab') is None,
                    f'{workbook}: activeTab belongs to the hidden-sheet case only, found '
                    f'{view.get("activeTab")!r}')

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
        require(attr_names(entry) <= {'name', 'sheetId', 'state', 'id'},
                f'{workbook}: sheet {expected["index"]} carries undeclared attributes '
                f'{sorted(attr_names(entry) - {"name", "sheetId", "state", "id"})}')
        state = entry.get('state')
        if expected['state'] == 'visible':
            require(state is None,
                    f'{workbook}: sheet {expected["index"]} is visible but carries state={state!r}; the '
                    f'schema default must stay unwritten so legacy bytes are unchanged')
        else:
            require(state == expected['state'],
                    f'{workbook}: sheet {expected["index"]} state {state!r} != declared '
                    f'{expected["state"]!r}')


def check_sheet_part(part: str, expected: dict, workbook: str) -> None:
    root = ElementTree.fromstring(part)
    require(root.tag == f'{S}worksheet', f'{workbook}: sheet part is not a worksheet')
    children = [child.tag.replace(S, '') for child in root]
    require(children, f'{workbook}: {expected["name"]}: worksheet has no children')
    sheet_pr = root.find(f'{S}sheetPr')
    if expected['tab_rgb'] is None:
        require(sheet_pr is None,
                f'{workbook}: {expected["name"]}: a sheet without a tab colour must not emit <sheetPr>')
    else:
        require(sheet_pr is not None, f'{workbook}: {expected["name"]}: missing <sheetPr>')
        require(children[0] == 'sheetPr',
                f'{workbook}: {expected["name"]}: <sheetPr> must be the worksheet\'s first child, found '
                f'{children[0]!r}')
        tab_colors = sheet_pr.findall(f'{S}tabColor')
        require(len(tab_colors) == 1,
                f'{workbook}: {expected["name"]}: <sheetPr> must hold exactly one <tabColor>')
        require(attr_names(tab_colors[0]) <= {'rgb'},
                f'{workbook}: {expected["name"]}: <tabColor> carries undeclared attributes '
                f'{sorted(attr_names(tab_colors[0]) - {"rgb"})}')
        require(tab_colors[0].get('rgb') == f'FF{expected["tab_rgb"]}',
                f'{workbook}: {expected["name"]}: tabColor rgb {tab_colors[0].get("rgb")!r} != '
                f'FF{expected["tab_rgb"]}')

    views = root.find(f'{S}sheetViews')
    frozen = expected['freeze_rows'] > 0
    require((views is not None) == (frozen or expected['zoom'] is not None),
            f'{workbook}: {expected["name"]}: <sheetViews> presence {views is not None} != expected '
            f'(freeze={frozen}, zoom={expected["zoom"]})')
    if views is None:
        return
    entries = views.findall(f'{S}sheetView')
    require(len(entries) == 1,
            f'{workbook}: {expected["name"]}: <sheetViews> must hold exactly one <sheetView>')
    view = entries[0]
    require(attr_names(view) <= {'workbookViewId', 'zoomScale'},
            f'{workbook}: {expected["name"]}: <sheetView> carries undeclared attributes '
            f'{sorted(attr_names(view) - {"workbookViewId", "zoomScale"})}')
    require(view.get('workbookViewId') == '0',
            f'{workbook}: {expected["name"]}: sheetView workbookViewId {view.get("workbookViewId")!r} != 0')
    if expected['zoom'] is None:
        require(view.get('zoomScale') is None,
                f'{workbook}: {expected["name"]}: an undeclared zoomScale must stay unwritten')
    else:
        require(view.get('zoomScale') == str(expected['zoom']),
                f'{workbook}: {expected["name"]}: zoomScale {view.get("zoomScale")!r} != {expected["zoom"]}')
    pane = view.find(f'{S}pane')
    if frozen:
        require(pane is not None, f'{workbook}: {expected["name"]}: missing frozen <pane>')
        require(pane.get('ySplit') == str(expected['freeze_rows']),
                f'{workbook}: {expected["name"]}: pane ySplit {pane.get("ySplit")!r} != '
                f'{expected["freeze_rows"]}')
    else:
        require(pane is None, f'{workbook}: {expected["name"]}: no pane was declared')


def check_with_openpyxl(path: Path, intent: list[dict]) -> None:
    import openpyxl  # imported lazily so the raw-OPC checks can run without it

    workbook = openpyxl.load_workbook(str(path))
    hidden = [entry['index'] for entry in intent if entry['state'] != 'visible']
    show_tabs = intent[0]['show_tabs']
    first_sheet = intent[0]['first_sheet']
    if not hidden and show_tabs is None and first_sheet is None:
        require(workbook.views == [],
                f'{path.name}: openpyxl sees {workbook.views} for a workbook with no <bookViews>')
    else:
        require(len(workbook.views) == 1,
                f'{path.name}: openpyxl sees {len(workbook.views)} workbook views')
        view = workbook.views[0]
        expected_tabs = True if show_tabs is None else show_tabs
        expected_first = 0 if first_sheet is None else first_sheet
        require(bool(view.showSheetTabs) == expected_tabs,
                f'{path.name}: openpyxl showSheetTabs {view.showSheetTabs!r} != {expected_tabs}')
        require(view.firstSheet == expected_first,
                f'{path.name}: openpyxl firstSheet {view.firstSheet!r} != {expected_first}')
        if hidden:
            require(view.activeTab == min(i for i, entry in enumerate(intent) if entry['state'] == 'visible'),
                    f'{path.name}: openpyxl activeTab {view.activeTab!r} is not the first visible sheet')
    for sheet, expected in zip(workbook.worksheets, intent):
        require(sheet.title == expected['name'],
                f'{path.name}: openpyxl sheet {expected["index"]} is {sheet.title!r}')
        require(sheet.sheet_state == expected['state'],
                f'{path.name}: {expected["name"]} sheet_state {sheet.sheet_state!r} != '
                f'{expected["state"]!r}')
        colour = sheet.sheet_properties.tabColor
        actual_rgb = None if colour is None else colour.rgb
        expected_rgb = None if expected['tab_rgb'] is None else f'FF{expected["tab_rgb"]}'
        require(actual_rgb == expected_rgb,
                f'{path.name}: {expected["name"]} tabColor {actual_rgb!r} != {expected_rgb!r}')
        require(sheet.sheet_view.zoomScale == expected['zoom'],
                f'{path.name}: {expected["name"]} zoomScale {sheet.sheet_view.zoomScale!r} != '
                f'{expected["zoom"]!r}')
        cells = sum(1 for row in sheet.iter_rows() for cell in row if cell.value is not None)
        require(cells == expected['cells'],
                f'{path.name}: {expected["name"]} has {cells} non-empty cells, declared '
                f'{expected["cells"]}; a hidden sheet must keep its data')


def verify(folder: Path) -> dict:
    """Runs every check against `folder` and returns the counts it covered."""
    require(folder.is_dir() and not folder.is_symlink(), f'missing/symlink artifact root {folder}')
    table = intent_table(folder / 'views.tsv')
    books = sorted(folder.glob('*.xlsx'))
    require(books, f'{folder}: no .xlsx workbooks')
    require({path.stem for path in books} == set(table),
            f'{folder}: workbooks {sorted(path.stem for path in books)} != declared {sorted(table)}')
    for path in books:
        intent = table[path.stem]
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            require('xl/workbook.xml' in names, f'{path.name}: missing xl/workbook.xml')
            check_workbook(archive.read('xl/workbook.xml').decode('utf-8'), intent, path.name)
            for entry in intent:
                part_name = f'xl/worksheets/sheet{entry["index"] + 1}.xml'
                require(part_name in names, f'{path.name}: missing {part_name}')
                check_sheet_part(archive.read(part_name).decode('utf-8'), entry, path.name)
        check_with_openpyxl(path, intent)
    return {'workbooks': len(books), 'sheets': sum(len(entries) for entries in table.values())}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    report = verify(args.artifacts)
    print(f'R86 independent openpyxl/ZIP/XML checks: {report["workbooks"]} workbooks and '
          f'{report["sheets"]} sheets verified; no Excel tab strip, navigation or repair evidence')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Fail as error:
        print(f'R86 sheet view verification FAILED: {error}', file=sys.stderr)
        raise SystemExit(1)
