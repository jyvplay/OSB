#!/usr/bin/env python3
"""R87 independent spreadsheet reader lane: read our own `.xlsx` packages with exceljs.

exceljs (pinned in `tools/r87node/package.json`) is a third implementation: neither our writer nor
openpyxl, which is what the R86 lane used. It reports sheet state, `tabColor.argb`, `sheetView.zoomScale`
and the workbook view's `firstSheet`/`activeTab`, so those four can be cross-checked against `views.tsv`.

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


def verify(artifacts: Path, node: str, reader_json: Path | None) -> dict:
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
    return {'reader': payload['reader'], 'workbooks': len(rows), 'sheets': sheets,
            'colours': colour_asserted, 'zooms': zoom_asserted, 'first_sheet': first_sheet_asserted,
            'show_tabs_skipped': show_tabs_skipped}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path, help='R86 sheet artifacts directory (views.tsv)')
    parser.add_argument('--node', help='node executable (default: PATH lookup)')
    parser.add_argument('--reader-json', type=Path, help='use a precomputed exceljs dump')
    args = parser.parse_args(argv)
    node = args.node or shutil.which('node')
    try:
        require(not (args.reader_json is None and node is None), 'node is not on PATH; the reader lane needs Node 18+')
        if args.reader_json is None:
            require(NODE_MODULES.is_dir(), f'missing {NODE_MODULES}; run `npm ci --prefix tools/r87node` first')
        totals = verify(args.artifacts, node or '', args.reader_json)
    except Fail as failure:
        print(f'R87 exceljs verification FAILED: {failure}')
        return 1
    except subprocess.CalledProcessError as failure:
        print(f'R87 exceljs verification FAILED: reader exited {failure.returncode}: '
              f'{(failure.stderr or "").strip()[-400:]}')
        return 1
    print(f'R87 {totals["reader"]} read {totals["workbooks"]} workbooks and {totals["sheets"]} sheets: '
          f'{totals["colours"]} tab colour(s), {totals["zooms"]} zoom value(s) and '
          f'{totals["first_sheet"]} firstSheet value(s) cross-checked. showSheetTabs is not modelled by '
          f'exceljs, so {totals["show_tabs_skipped"]} workbook-level value(s) stay with the openpyxl lane. '
          'No Excel ran and nothing was recalculated.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
