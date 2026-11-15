#!/usr/bin/env python3
"""R87/R88 independent spreadsheet reader lane: read our own `.xlsx` packages with exceljs.

exceljs (pinned in `tools/r87node/package.json`) is a third implementation: neither our writer nor
openpyxl, which is what the R86 lane used. It reports sheet state, `tabColor.argb`, `sheetView.zoomScale`
and the workbook view's `firstSheet`/`activeTab`, so those four can be cross-checked against `views.tsv`.

The optional `--r89` lane reads the R89 font-size workbooks: exceljs reports `font.size` (whole
points — it truncates a fractional size, which this checker accepts only within one point and counts
separately), `font.bold`, `font.italic` and `font.color.argb`.

The optional `--r88` lane reads the R88 gridline workbooks: exceljs reports `sheetView.showGridLines`
tri-state (`null`, `false`, `true`), so the declared suppression is cross-checked against a third-party
reader. exceljs defaults an absent `zoomScale` to 100, so an undeclared zoom is accepted as `None`/`100`
exactly as in the R86 lane.

`showSheetTabs` is **not** modelled by exceljs, and this checker says so instead of implying coverage: the
count of skipped attributes is printed. Host-only; no Excel and no recalculation is involved anywhere.
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
READER = ROOT / 'tools/r87node/read_xlsx.mjs'
NODE_MODULES = ROOT / 'tools/r87node/node_modules'
FIELDS = 10


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_rows(path: Path) -> dict[str, list[list[str]]]:
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


def optional_int(text: str) -> int | None:
    return None if text == '-' else int(text)


def read(artifacts: Path, node: str, workdir: Path) -> dict:
    output = workdir / 'exceljs.json'
    subprocess.run([node, str(READER), str(artifacts), str(output)], check=True, text=True, capture_output=True)
    payload = json.loads(output.read_text())
    require(not payload['errors'], f'exceljs failed on {payload["errors"]}')
    return payload


GRID_FIELDS = 7


def check_r88(payload: dict, artifacts: Path) -> dict[str, int]:
    """Cross-check `grid.tsv` against an exceljs dump of the R88 workbooks."""
    require((artifacts / 'grid.tsv').is_file(), f'missing intent table {artifacts / "grid.tsv"}')
    table: dict[str, list[list[str]]] = {}
    for number, line in enumerate((artifacts / 'grid.tsv').read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == GRID_FIELDS, f'grid.tsv line {number}: {len(parts)} fields')
        table.setdefault(parts[0], []).append(parts)
    files = {entry['name'][:-5]: entry for entry in payload['files']}
    require(sorted(files) == sorted(table),
            'grid.tsv and the read workbooks disagree about which files exist')
    sheets = 0
    suppressed = 0
    for name, entries in table.items():
        actual = files[name]['sheets']
        require(len(actual) == len(entries),
                f'{name}: exceljs reports {len(actual)} sheets, declared {len(entries)}')
        for entry, sheet in zip(entries, actual):
            where = f'{name} sheet {entry[1]} ({entry[2]})'
            require(sheet['name'] == entry[2], f'{where}: exceljs reports name {sheet["name"]!r}')
            declared = {'-': None, 'false': False, 'true': True}[entry[3]]
            require(sheet['showGridLines'] == declared,
                    f'{where}: exceljs reports showGridLines={sheet["showGridLines"]!r}, '
                    f'declared {declared!r}')
            if declared is not None:
                suppressed += int(not declared)
            zoom = optional_int(entry[4])
            if zoom is None:
                require(sheet['zoomScale'] in (None, 100),
                        f'{where}: undeclared zoom read as {sheet["zoomScale"]}')
            else:
                require(sheet['zoomScale'] == zoom,
                        f'{where}: zoom read as {sheet["zoomScale"]}, declared {zoom}')
            require(sheet['state'] == entry[6],
                    f'{where}: exceljs reports state {sheet["state"]!r}, declared {entry[6]!r}')
            sheets += 1
    return {'workbooks': len(table), 'sheets': sheets, 'grid_off': suppressed}


FONT_FIELDS = 8


def check_r89(payload: dict, artifacts: Path, fields: int) -> dict[str, int]:
    """Cross-check `fonts.tsv` against an exceljs dump of the R89 workbooks."""
    path = artifacts / 'fonts.tsv'
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[list[str]]] = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'fonts.tsv line {number}: {len(parts)} fields')
        table.setdefault(parts[0], []).append(parts)
    files = {entry['name'][:-5]: entry for entry in payload['files']}
    require(sorted(files) == sorted(table),
            'fonts.tsv and the read workbooks disagree about which files exist')
    cells = 0
    sized = 0
    rounded = 0
    for name, entries in table.items():
        sheets = {sheet['name']: sheet for sheet in files[name]['sheets']}
        for row in entries:
            _, sheet_name, ref, bold, italic, colour, size, _fill = row
            where = f'{name}:{sheet_name}!{ref}'
            require(sheet_name in sheets, f'{where}: exceljs did not read this sheet')
            cell = next((c for c in sheets[sheet_name].get('cells', []) if c['ref'] == ref), None)
            require(cell is not None, f'{where}: exceljs did not read this cell')
            require(bool(cell['bold']) == (bold == 'true'), f'{where}: exceljs bold mismatch')
            require(bool(cell['italic']) == (italic == 'true'), f'{where}: exceljs italic mismatch')
            if colour == '-':
                require(cell['color'] is None, f'{where}: undeclared colour read as {cell["color"]}')
            else:
                require(cell['color'] == 'FF' + colour,
                        f'{where}: colour read as {cell["color"]}, expected FF{colour}')
            expected = None if size == '-' else float(size)
            actual = cell['size']
            if expected is None:
                require(actual is None, f'{where}: undeclared size read as {actual}')
            else:
                require(actual is not None, f'{where}: exceljs reports no size, declared {size}')
                if expected % 1.0 == 0.0:
                    require(float(actual) == expected, f'{where}: size read as {actual}, declared {size}')
                else:
                    require(abs(float(actual) - expected) <= 0.5,
                            f'{where}: size read as {actual}, declared {size} beyond one point')
                    rounded += 1
                sized += 1
            cells += 1
    return {'workbooks': len(table), 'cells': cells, 'sized': sized, 'rounded': rounded}


def verify(artifacts: Path, node: str, reader_json: Path | None,
           r88: Path | None = None, r88_json: Path | None = None,
           r89: Path | None = None, r89_json: Path | None = None) -> dict:
    rows = intent_rows(artifacts / 'views.tsv')
    workdir = Path(tempfile.mkdtemp(prefix='r87-exceljs-'))
    try:
        payload = json.loads(reader_json.read_text()) if reader_json else read(artifacts, node, workdir)
    finally:
        shutil.rmtree(workdir, ignore_errors=True)
    files = {entry['name'][:-5]: entry for entry in payload['files']}
    require(sorted(files) == sorted(rows), 'views.tsv and the read workbooks disagree about which files exist')
    sheets = 0
    colour_asserted = 0
    zoom_asserted = 0
    first_sheet_asserted = 0
    show_tabs_skipped = 0
    for name, entries in rows.items():
        workbook = files[name]
        declared_first = {optional_int(entry[8]) for entry in entries}
        require(len(declared_first) == 1, f'{name}: the intent table disagrees with itself about firstSheet')
        first_sheet = declared_first.pop()
        require(workbook['firstSheet'] == first_sheet,
                f'{name}: exceljs reports firstSheet={workbook["firstSheet"]}, declared {first_sheet}')
        first_sheet_asserted += 1
        declared_show = {entry[7] for entry in entries}
        require(len(declared_show) == 1, f'{name}: the intent table disagrees with itself about showSheetTabs')
        show_tabs_skipped += 1
        require([entry[1] for entry in entries] == [str(index) for index in range(len(entries))],
                f'{name}: sheet indices are not 0..{len(entries) - 1}')
        actual = workbook['sheets']
        require(len(actual) == len(entries),
                f'{name}: exceljs reports {len(actual)} sheets, declared {len(entries)}')
        for entry, sheet in zip(entries, actual):
            where = f'{name} sheet {entry[1]} ({entry[2]})'
            require(sheet['name'] == entry[2], f'{where}: exceljs reports name {sheet["name"]!r}')
            require(sheet['state'] == entry[3],
                    f'{where}: exceljs reports state {sheet["state"]!r}, declared {entry[3]!r}')
            fill = entry[4]
            if fill == '-':
                require(sheet['tabArgb'] is None, f'{where}: undeclared tab colour read as {sheet["tabArgb"]}')
            else:
                require(sheet['tabArgb'] == 'FF' + fill.upper(),
                        f'{where}: tab colour read as {sheet["tabArgb"]}, expected FF{fill.upper()}')
                colour_asserted += 1
            zoom = optional_int(entry[5])
            if zoom is None:
                require(sheet['zoomScale'] in (None, 100),
                        f'{where}: undeclared zoom read as {sheet["zoomScale"]}')
            else:
                require(sheet['zoomScale'] == zoom,
                        f'{where}: zoom read as {sheet["zoomScale"]}, declared {zoom}')
                zoom_asserted += 1
            sheets += 1
    totals = {'reader': payload['reader'], 'workbooks': len(rows), 'sheets': sheets,
            'colours': colour_asserted, 'zooms': zoom_asserted, 'first_sheet': first_sheet_asserted,
            'show_tabs_skipped': show_tabs_skipped}
    totals.update({'r88_workbooks': 0, 'r88_sheets': 0, 'r88_grid_off': 0,
                   'r89_workbooks': 0, 'r89_cells': 0, 'r89_sized': 0, 'r89_rounded': 0})
    if r88 is not None:
        workdir = Path(tempfile.mkdtemp(prefix='r88-exceljs-'))
        try:
            r88_payload = (json.loads(r88_json.read_text()) if r88_json
                           else read(r88, node, workdir))
        finally:
            shutil.rmtree(workdir, ignore_errors=True)
        r88_totals = check_r88(r88_payload, r88)
        totals.update({'r88_workbooks': r88_totals['workbooks'], 'r88_sheets': r88_totals['sheets'],
                       'r88_grid_off': r88_totals['grid_off']})
    if r89 is not None:
        workdir = Path(tempfile.mkdtemp(prefix='r89-exceljs-'))
        try:
            r89_payload = (json.loads(r89_json.read_text()) if r89_json
                           else read(r89, node, workdir))
        finally:
            shutil.rmtree(workdir, ignore_errors=True)
        r89_totals = check_r89(r89_payload, r89, FONT_FIELDS)
        totals.update({'r89_workbooks': r89_totals['workbooks'], 'r89_cells': r89_totals['cells'],
                       'r89_sized': r89_totals['sized'], 'r89_rounded': r89_totals['rounded']})
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path, help='R86 sheet artifacts directory (views.tsv)')
    parser.add_argument('--node', help='node executable (default: PATH lookup)')
    parser.add_argument('--reader-json', type=Path, help='use a precomputed exceljs dump')
    parser.add_argument('--r88', type=Path, help='R88 gridline artifacts (grid.tsv)')
    parser.add_argument('--r88-reader-json', type=Path, help='use a precomputed R88 exceljs dump')
    parser.add_argument('--r89', type=Path, help='R89 font-size artifacts (fonts.tsv)')
    parser.add_argument('--r89-reader-json', type=Path, help='use a precomputed R89 exceljs dump')
    args = parser.parse_args(argv)
    node = args.node or shutil.which('node')
    try:
        require(not (args.reader_json is None and node is None), 'node is not on PATH; the reader lane needs Node 18+')
        if args.reader_json is None:
            require(NODE_MODULES.is_dir(), f'missing {NODE_MODULES}; run `npm ci --prefix tools/r87node` first')
        totals = verify(args.artifacts, node or '', args.reader_json,
                        getattr(args, 'r88', None), getattr(args, 'r88_reader_json', None),
                        getattr(args, 'r89', None), getattr(args, 'r89_reader_json', None))
    except Fail as failure:
        print(f'R87 exceljs verification FAILED: {failure}')
        return 1
    except subprocess.CalledProcessError as failure:
        print(f'R87 exceljs verification FAILED: reader exited {failure.returncode}: '
              f'{(failure.stderr or "").strip()[-400:]}')
        return 1
    print(f'R87/R88 {totals["reader"]} read {totals["workbooks"]} workbooks and {totals["sheets"]} sheets: '
          f'{totals["colours"]} tab colour(s), {totals["zooms"]} zoom value(s) and '
          f'{totals["first_sheet"]} firstSheet value(s) cross-checked. showSheetTabs is not modelled by '
          f'exceljs, so {totals["show_tabs_skipped"]} workbook-level value(s) stay with the openpyxl lane. '
          'No Excel ran and nothing was recalculated.')
    if totals['r88_workbooks']:
        print(f'R88 gridline lane: {totals["r88_sheets"]} sheets in {totals["r88_workbooks"]} workbooks '
              f'cross-checked through exceljs, {totals["r88_grid_off"]} with gridlines suppressed.')
    if totals['r89_workbooks']:
        print(f'R89 font lane: {totals["r89_cells"]} cells in {totals["r89_workbooks"]} workbooks '
              f'cross-checked through exceljs, {totals["r89_sized"]} with a declared size '
              f'({totals["r89_rounded"]} fractional size(s) that exceljs truncates by design).')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
