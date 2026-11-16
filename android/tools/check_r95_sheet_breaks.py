#!/usr/bin/env python3
"""Independent verification for R95 manual page breaks (`<rowBreaks>`, `<colBreaks>`, §18.3.1.84/§18.3.1.11).

Two layers:

1. **raw OPC** — a sheet that declares no breaks must carry neither container; a sheet that declares some must
   carry exactly the declared children, in ascending `id` order, with `count` and `manualBreakCount` equal to
   what is written, `id` inside the grid, `min`/`max` only when declared, and `<brk>` attributes in schema order
   (`id`, `min`, `max`, `man`). The containers must follow `<headerFooter>` when one is present;
2. **openpyxl 3.1.5** — `worksheet.row_breaks` / `worksheet.col_breaks` must agree on the ids, the manual flags
   and both counts. openpyxl fills `min`/`max` with its own defaults when they are absent, so an absent span is
   only required to be inside the grid, not absent in the reader.

A break record says where the user *asked* for a new page. It says nothing about pagination, which the printing
application computes. Host-only.

Utility: `python3 tools/check_r95_sheet_breaks.py testing/build/r95/artifacts [--require-openpyxl]`
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
FIELDS = 8
AXES = {'row': 'rowBreaks', 'col': 'colBreaks'}
ATTRIBUTE_ORDER = ['id', 'min', 'max', 'man']
MAX_ROW = 1_048_576
MAX_COLUMN = 16_384


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> list[list[str]]:
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


def parse_part(raw: str, where: str):
    """A part that is not well-formed XML is a verification failure with a message, not a traceback."""
    try:
        return ElementTree.fromstring(raw)
    except ElementTree.ParseError as error:
        raise Fail(f'{where} is not well-formed XML: {error}')


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


def sheet_groups(rows: list[list[str]]) -> dict[tuple[str, str], list[list[str]]]:
    grouped: dict[tuple[str, str], list[list[str]]] = {}
    for row in rows:
        grouped.setdefault((row[0], row[1]), []).append(row)
    return grouped


def verify_raw(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    declared = {row[0] for row in rows}
    on_disk = {path.name for path in folder.glob('*.xlsx')}
    require(declared <= on_disk, f'the intent table declares files that are absent: {sorted(declared - on_disk)}')
    for name in sorted(declared):
        described = {row[1] for row in rows if row[0] == name}
        present = sheet_names(folder, name)
        require(described == present,
                f'{name}: the intent table describes {sorted(described)}, the workbook has {sorted(present)}')

    with_breaks = 0
    without = 0
    brk_total = 0
    for (name, sheet_name), group in sorted(sheet_groups(rows).items()):
        # The probe writes one "0" row for a sheet with no breaks and one row per break otherwise.
        has = '0' if group[0][2] == '0' else '1'
        require(len({row[2] == '0' for row in group}) == 1,
                f'{name}:{sheet_name} mixes a "no breaks" row with break rows')
        part = parts_of(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        with zipfile.ZipFile(folder / name) as archive:
            raw = archive.read(part).decode('utf-8')
        root = parse_part(raw, f'{name}:{sheet_name} {part}')
        children = {child.tag.split('}')[-1]: child for child in root}

        if has == '0':
            require(not any(axis in children for axis in AXES.values()),
                    f'{name}:{sheet_name} declares no breaks but carries '
                    f'{[axis for axis in AXES.values() if axis in children]}')
            require(len(group) == 1, f'{name}:{sheet_name} has {len(group)} rows for a sheet with no breaks')
            without += 1
            continue

        with_breaks += 1
        if 'headerFooter' in children:
            for axis in AXES.values():
                if axis in children:
                    require(list(children).index('headerFooter') < list(children).index(axis),
                            f'{name}:{sheet_name} puts <{axis}> before <headerFooter>')

        for axis, container_name in AXES.items():
            axis_rows = [row for row in group if row[2] == axis]
            container = children.get(container_name)
            if not axis_rows:
                require(container is None,
                        f'{name}:{sheet_name} wrote an empty <{container_name}> for no declared {axis} breaks')
                continue
            axis_rows.sort(key=lambda row: int(row[3]))
            require(container is not None, f'{name}:{sheet_name} declares {axis} breaks but carries none')
            nodes = container.findall(f'{S}brk')
            require(len(nodes) == len(axis_rows),
                    f'{name}:{sheet_name} <{container_name}> has {len(nodes)} brk, declared {len(axis_rows)}')
            require(container.get('count') == str(len(axis_rows)),
                    f'{name}:{sheet_name} <{container_name}> count={container.get("count")!r} != {len(axis_rows)}')
            manual = sum(1 for row in axis_rows if row[4] == '1')
            require(container.get('manualBreakCount') == str(manual),
                    f'{name}:{sheet_name} <{container_name}> manualBreakCount='
                    f'{container.get("manualBreakCount")!r} != {manual}')

            # The intent table repeats the sheet-level manual total (rows + columns) on every row.
            sheet_manual = sum(1 for row in group if row[4] == '1')
            declared_manual = group[0][7]
            if declared_manual != '-':
                require(int(declared_manual) == sheet_manual,
                        f'{name}:{sheet_name} intent manual total {declared_manual} != {sheet_manual}')
            previous = 0
            for row, node in zip(axis_rows, nodes):
                _, _, _, id_field, manual_field, min_field, max_field, manual_count = row
                identifier = int(id_field)
                limit = MAX_ROW if axis == 'row' else MAX_COLUMN
                span_limit = MAX_COLUMN if axis == 'row' else MAX_ROW
                require(1 <= identifier < limit,
                        f'{name}:{sheet_name} {axis} break id {identifier} outside 1..{limit - 1}')
                require(identifier > previous,
                        f'{name}:{sheet_name} {axis} break ids are not strictly ascending at {identifier}')
                previous = identifier
                attributes = list(node.attrib)
                require(all(attribute in ATTRIBUTE_ORDER for attribute in attributes),
                        f'{name}:{sheet_name} <brk> has an unmodelled attribute: {attributes}')
                require(attributes == [attribute for attribute in ATTRIBUTE_ORDER if attribute in attributes],
                        f'{name}:{sheet_name} <brk> attributes {attributes} are not in schema order')
                require(node.get('id') == id_field,
                        f'{name}:{sheet_name} <brk> id={node.get("id")!r} != {id_field!r}')
                require(node.get('man') == manual_field,
                        f'{name}:{sheet_name} <brk> id={id_field} man={node.get("man")!r} != {manual_field!r}')
                for attribute, field in (('min', min_field), ('max', max_field)):
                    if field == '-':
                        require(attribute not in attributes,
                                f'{name}:{sheet_name} <brk> id={id_field} invented {attribute}')
                    else:
                        require(node.get(attribute) == field,
                                f'{name}:{sheet_name} <brk> id={id_field} {attribute}='
                                f'{node.get(attribute)!r} != {field!r}')
                        require(0 <= int(field) <= span_limit,
                                f'{name}:{sheet_name} <brk> id={id_field} {attribute}={field} outside the grid')
                brk_total += 1
    return {'break_sheets': with_breaks, 'plain_sheets': without, 'breaks': brk_total}


def verify_openpyxl(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415

    checked = 0
    books: dict[str, object] = {}
    for (name, sheet_name), group in sorted(sheet_groups(rows).items()):
        if name not in books:
            with warnings.catch_warnings():
                warnings.simplefilter('ignore')
                books[name] = openpyxl.load_workbook(folder / name)
        sheet = books[name][sheet_name]
        for axis, attribute in (('row', 'row_breaks'), ('col', 'col_breaks')):
            axis_rows = sorted([row for row in group if row[2] == axis], key=lambda row: int(row[3]))
            container = getattr(sheet, attribute)
            require(len(container.brk) == len(axis_rows),
                    f'openpyxl {name}:{sheet_name} sees {len(container.brk)} {axis} breaks, '
                    f'declared {len(axis_rows)}')
            if axis_rows:
                # openpyxl 3.1.5 computes `count` and `manualBreakCount` as len(brk) and discards both
                # attributes on read (openpyxl/worksheet/pagebreak.py: RowBreak.count is a property). The
                # writer's manualBreakCount is therefore checked in the raw layer only; here the reader is
                # required to report its own length, which is all it can say.
                require(container.count == len(axis_rows),
                        f'openpyxl {name}:{sheet_name} {axis} count={container.count!r} != {len(axis_rows)}')
                require(container.manualBreakCount == len(axis_rows),
                        f'openpyxl {name}:{sheet_name} {axis} manualBreakCount='
                        f'{container.manualBreakCount!r} != {len(axis_rows)} (openpyxl counts every brk)')
            for row, brk in zip(axis_rows, container.brk):
                _, _, _, id_field, manual_field, _, _, _ = row
                require(brk.id == int(id_field),
                        f'openpyxl {name}:{sheet_name} {axis} break id {brk.id!r} != {id_field!r}')
                got = brk.man
                want = manual_field == '1'
                require(bool(got) == want,
                        f'openpyxl {name}:{sheet_name} {axis} id={id_field} man={got!r} != {want}')
        checked += 1
    return {'reader_sheets': checked}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    rows = intent_table(folder / 'breaks.tsv')
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
        print(f'R95 page-break verification FAILED: {failure}')
        return 1
    message = (f'R95 page breaks: {totals["breaks"]} break record(s) over {totals["break_sheets"]} sheet(s), '
               f'{totals["plain_sheets"]} sheet(s) with no break record')
    if 'reader_sheets' in totals:
        message += f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
