#!/usr/bin/env python3
"""Independent verification for the R82 spreadsheet cell-alignment workbooks.

`XlsxStyledBook` may declare `horizontal` / `vertical` alignment on a `CellStyle`, which lives as an
`<alignment>` child of the deduplicated `cellXfs` entry. This verifier opens every workbook twice:

1. **raw OPC** — `xl/styles.xml` and each worksheet part are parsed directly. The expected `cellXfs`
   count is derived from the intent's style signatures (never from the implementation), cells with
   identical signatures must share one `s`, alignment-bearing entries must carry
   `applyAlignment="1"` and exactly the declared attributes, and default cells must resolve to an
   entry without `<alignment>`.
2. **openpyxl 3.1.5** — an independent implementation exposing `cell.alignment.horizontal/vertical`
   and `cell.font.bold`, compared one-to-one with the intent table the Kotlin probe wrote.

Host-only. openpyxl is a reader, not Excel: agreement here is package-structure evidence, never
recalculation or rendering.
"""
from __future__ import annotations

import argparse
import sys
import warnings
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
INTENT_FIELDS = 9
HORIZONTAL = {'left', 'center', 'right'}
VERTICAL = {'top', 'center', 'bottom'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def optional(text: str) -> str | None:
    return None if text == '-' else text


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        horizontal = optional(fields[3])
        vertical = optional(fields[4])
        require(horizontal is None or horizontal in HORIZONTAL,
                f'intent line {index} declares unknown horizontal token {horizontal!r}')
        require(vertical is None or vertical in VERTICAL,
                f'intent line {index} declares unknown vertical token {vertical!r}')
        table.setdefault(fields[0], []).append({
            'sheet': int(fields[1]),
            'ref': fields[2],
            'horizontal': horizontal,
            'vertical': vertical,
            'bold': fields[5] == '1',
            'fill': optional(fields[6]),
            'number_format': optional(fields[7]),
            'thin_border': fields[8] == '1',
        })
    require(table, 'intent table is empty')
    return table


def signature(entry: dict) -> tuple:
    return (entry['bold'], entry['fill'], entry['number_format'], entry['thin_border'],
            entry['horizontal'], entry['vertical'])


def expected_xf_count(entries: list[dict]) -> int:
    signatures = {signature(entry) for entry in entries}
    signatures.discard((False, None, None, False, None, None))
    return 1 + len(signatures)


def column_index(ref: str) -> int:
    letters = ''.join(character for character in ref if character.isalpha())
    value = 0
    for character in letters:
        value = value * 26 + (ord(character.upper()) - ord('A') + 1)
    return value


def check_workbook(path: Path, intent: list[dict]) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require('xl/styles.xml' in names, f'{path.name}: missing xl/styles.xml')
        styles = ElementTree.fromstring(archive.read('xl/styles.xml').decode('utf-8'))
        cell_xfs = styles.find(f'{S}cellXfs')
        require(cell_xfs is not None, f'{path.name}: styles.xml has no cellXfs')
        xfs = [child for child in cell_xfs if child.tag == f'{S}xf']
        expected_count = expected_xf_count(intent)
        require(len(xfs) == expected_count,
                f'{path.name}: cellXfs holds {len(xfs)} entries, the declared styles need {expected_count}')
        require(cell_xfs.get('count') == str(len(xfs)),
                f'{path.name}: cellXfs/@count {cell_xfs.get("count")} != {len(xfs)} entries')

        # Resolve every declared cell to its xf and check the entry itself.
        by_sheet: dict[int, dict[str, int]] = {}
        for entry in intent:
            sheet_index = entry['sheet']
            part = f'xl/worksheets/sheet{sheet_index + 1}.xml'
            require(part in names, f'{path.name}: missing {part}')
            if sheet_index not in by_sheet:
                root = ElementTree.fromstring(archive.read(part).decode('utf-8'))
                cells = {}
                for row in root.iter(f'{S}row'):
                    for cell in row:
                        if cell.tag != f'{S}c':
                            continue
                        ref = cell.get('r')
                        require(ref is not None, f'{path.name}: a cell has no r attribute')
                        cells[ref] = int(cell.get('s') or 0)
                by_sheet[sheet_index] = cells
            cells = by_sheet[sheet_index]
            require(entry['ref'] in cells, f'{path.name}: {entry["ref"]} is absent from the sheet')
            index = cells[entry['ref']]
            require(0 <= index < len(xfs), f'{path.name}: {entry["ref"]} points at xf {index} out of range')
            xf = xfs[index]
            alignment = xf.find(f'{S}alignment')
            has_alignment = entry['horizontal'] is not None or entry['vertical'] is not None
            if not has_alignment:
                require(alignment is None,
                        f'{path.name}: {entry["ref"]} resolves to an xf with <alignment> but declares none')
            else:
                require(alignment is not None,
                        f'{path.name}: {entry["ref"]} declares alignment but its xf has no <alignment>')
                require(xf.get('applyAlignment') == '1',
                        f'{path.name}: {entry["ref"]} alignment entry lacks applyAlignment="1"')
                require(alignment.get('horizontal') == entry['horizontal'],
                        f'{path.name}: {entry["ref"]} horizontal {alignment.get("horizontal")!r} != '
                        f'declared {entry["horizontal"]!r}')
                require(alignment.get('vertical') == entry['vertical'],
                        f'{path.name}: {entry["ref"]} vertical {alignment.get("vertical")!r} != '
                        f'declared {entry["vertical"]!r}')
                unexpected = set(alignment.attrib) - {'horizontal', 'vertical'}
                require(not unexpected,
                        f'{path.name}: {entry["ref"]} alignment carries undeclared attributes {sorted(unexpected)}')
            if entry['bold']:
                require(xf.get('applyFont') == '1', f'{path.name}: {entry["ref"]} bold without applyFont')
            if entry['fill'] is not None:
                require(xf.get('applyFill') == '1', f'{path.name}: {entry["ref"]} fill without applyFill')
            if entry['thin_border']:
                require(xf.get('applyBorder') == '1', f'{path.name}: {entry["ref"]} border without applyBorder')
            if entry['number_format'] is not None:
                require(xf.get('applyNumberFormat') == '1',
                        f'{path.name}: {entry["ref"]} number format without applyNumberFormat')

        # Sharing: identical declared signatures must resolve to one entry, distinct ones must not.
        seen: dict[tuple, int] = {}
        for entry in intent:
            cells = by_sheet[entry['sheet']]
            index = cells[entry['ref']]
            key = signature(entry)
            if key in seen:
                require(seen[key] == index,
                        f'{path.name}: {entry["ref"]} and an identical style resolve to different xfs '
                        f'({seen[key]} vs {index})')
            else:
                seen[key] = index
        require(len(set(seen.values())) == len(seen),
                f'{path.name}: two different declared styles share one xf: {seen}')
        default_index = seen.get((False, None, None, False, None, None), 0)
        require(default_index == 0,
                f'{path.name}: the default style must be xf 0, found {default_index}')


def check_with_openpyxl(path: Path, intent: list[dict]) -> None:
    import openpyxl  # imported lazily so the raw-OPC checks can run without it

    with warnings.catch_warnings():
        warnings.simplefilter('ignore')
        workbook = openpyxl.load_workbook(str(path))
    for entry in intent:
        sheet = workbook.worksheets[entry['sheet']]
        cell = sheet[entry['ref']]
        require(cell.alignment.horizontal == entry['horizontal'],
                f'{path.name}/{sheet.title}: openpyxl horizontal {cell.alignment.horizontal!r} != '
                f'declared {entry["horizontal"]!r} at {entry["ref"]}')
        require(cell.alignment.vertical == entry['vertical'],
                f'{path.name}/{sheet.title}: openpyxl vertical {cell.alignment.vertical!r} != '
                f'declared {entry["vertical"]!r} at {entry["ref"]}')
        require(bool(cell.font.bold) == entry['bold'],
                f'{path.name}/{sheet.title}: openpyxl bold {cell.font.bold} contradicts {entry["bold"]} '
                f'at {entry["ref"]}')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'align.tsv')
    cells = 0
    for name, expected in intent.items():
        path = directory / f'{name}.xlsx'
        require(path.is_file(), f'missing workbook {path}')
        check_workbook(path, expected)
        check_with_openpyxl(path, expected)
        cells += len(expected)
    extra = {path.stem for path in directory.glob('*.xlsx')} - set(intent)
    require(not extra, f'unexpected workbooks without a declared intent: {sorted(extra)}')
    return {'workbooks': len(intent), 'cells': cells}


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
            print(f'R82 SHEET ALIGN CHECK FAILED: openpyxl unavailable: {error}', file=sys.stderr)
            return 1
        print(f'openpyxl unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R82 SHEET ALIGN CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R82 spreadsheet alignment: {report['workbooks']} workbooks and {report['cells']} cells verified "
        "with openpyxl 3.1.5 and raw OPC inspection. Not Excel recalculation or rendering evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
