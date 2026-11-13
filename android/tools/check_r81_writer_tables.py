#!/usr/bin/env python3
"""Independent verification for the R81 Writer table-geometry packages.

The production writer (`DocxPackage`) emits real `.docx` files whose tables may declare explicit
column widths (`w:tblW` + `w:tblGrid/w:gridCol` + `w:tblLayout` fixed) and a repeated header row
(`w:trPr/w:tblHeader`). This verifier opens every package twice:

1. **raw OPC** — the document part is parsed directly: `w:tblPr` must be the first child of
   `w:tbl` and may hold exactly `w:tblW` then `w:tblLayout`; `w:tblGrid` must carry one `w:gridCol`
   per column; the total width must equal the column sum; only the first row may carry
   `w:trPr/w:tblHeader`, and only when the table declares it; no `w:tcW`, no `w:tblStyle`.
2. **python-docx 1.2** — an independent implementation that exposes column widths through its
   `w:tblGrid/w:gridCol` reader, table autofit through `w:tblLayout`, and cell text. Its numbers are
   compared with the intent table the Kotlin probe wrote.

Host-only. python-docx reads the package structure; it is not Word, and it exposes neither
`w:tblHeader` nor Word's pagination, so header repetition is checked structurally only.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
EMU_PER_TWIP = 635
MIN_COL_TWIPS = 240
MAX_COL_TWIPS = 31_680
MAX_TOTAL_TWIPS = 31_680
INTENT_FIELDS = 7


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
        widths = None if fields[3] == '-' else [int(value) for value in fields[3].split(',')]
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'columns': int(fields[2]),
            'widths': widths,
            'repeat': fields[4] == '1',
            'rows': int(fields[5]),
            'first_cell': fields[6],
        })
    require(table, 'intent table is empty')
    for name, tables in table.items():
        require([entry['index'] for entry in tables] == list(range(len(tables))),
                f'{name}: table indices are not 0..{len(tables) - 1}')
        for entry in tables:
            if entry['widths'] is not None:
                require(len(entry['widths']) == entry['columns'],
                        f'{name}: intent declares {len(entry["widths"])} widths for {entry["columns"]} columns')
    return table


def cell_text(tc: ElementTree.Element) -> str:
    paragraphs = []
    for paragraph in tc.findall(f'{W}p'):
        text = ''.join(node.text or '' for node in paragraph.iter(f'{W}t'))
        paragraphs.append(text)
    return '\n'.join(paragraphs)


def check_package(path: Path, intent: list[dict]) -> None:
    with zipfile.ZipFile(path) as archive:
        require('word/document.xml' in archive.namelist(), f'{path.name}: missing word/document.xml')
        root = ElementTree.fromstring(archive.read('word/document.xml').decode('utf-8'))
        body = root.find(f'{W}body')
        require(body is not None, f'{path.name}: document has no body')
        tables = [child for child in body if child.tag == f'{W}tbl']
        require(len(tables) == len(intent),
                f'{path.name}: table count {len(tables)} != declared {len(intent)}')

        for table, expected in zip(tables, intent):
            children = list(table)
            require(children and children[0].tag == f'{W}tblPr',
                    f'{path.name}: w:tblPr must be the first child of w:tbl')
            require(len(children) > 1 and children[1].tag == f'{W}tblGrid',
                    f'{path.name}: w:tblGrid must follow w:tblPr')
            properties = list(children[0])
            grid = children[1]
            columns = grid.findall(f'{W}gridCol')
            require(len(columns) == expected['columns'],
                    f'{path.name}: grid column count {len(columns)} != declared {expected["columns"]}')

            if expected['widths'] is None:
                require(not properties,
                        f'{path.name}: a table without declared widths must carry an empty w:tblPr '
                        f'(found {[child.tag.split("}")[-1] for child in properties]})')
                for position, column in enumerate(columns):
                    require(f'{W}w' not in column.attrib,
                            f'{path.name}: grid column {position} declares a width but the table does not')
            else:
                names = [child.tag.split('}')[-1] for child in properties]
                require(names == ['tblW', 'tblLayout'],
                        f'{path.name}: w:tblPr children {names} != [tblW, tblLayout] in CT_TblPr order')
                width = properties[0]
                require(width.get(f'{W}type') == 'dxa',
                        f'{path.name}: w:tblW/@w:type {width.get(f"{W}type")!r} is not dxa')
                total = int(width.get(f'{W}w'))
                require(total == sum(expected['widths']),
                        f'{path.name}: w:tblW/@w:w {total} != sum of declared widths {sum(expected["widths"])}')
                require(properties[1].get(f'{W}type') == 'fixed',
                        f'{path.name}: w:tblLayout/@w:type must be fixed for explicit widths')
                seen = []
                for position, column in enumerate(columns):
                    raw = column.get(f'{W}w')
                    require(raw is not None, f'{path.name}: grid column {position} has no w:w')
                    measured = int(raw)
                    require(measured == expected['widths'][position],
                            f'{path.name}: grid column {position} width {measured} != declared '
                            f'{expected["widths"][position]}')
                    require(MIN_COL_TWIPS <= measured <= MAX_COL_TWIPS,
                            f'{path.name}: grid column {position} width {measured} outside '
                            f'{MIN_COL_TWIPS}..{MAX_COL_TWIPS}')
                    seen.append(measured)
                require(total <= MAX_TOTAL_TWIPS,
                        f'{path.name}: table width {total} exceeds {MAX_TOTAL_TWIPS}')
                require(sum(seen) == total, f'{path.name}: grid widths do not add up to w:tblW')

            rows = [child for child in children[2:] if child.tag == f'{W}tr']
            require(len(rows) == expected['rows'],
                    f'{path.name}: row count {len(rows)} != declared {expected["rows"]}')
            require(all(child.tag == f'{W}tr' for child in children[2:]),
                    f'{path.name}: w:tbl holds a child that is not w:tr after the grid')
            for row_index, row in enumerate(rows):
                markers = [child for child in row if child.tag == f'{W}trPr']
                headers = [node for node in row.iter(f'{W}tblHeader')]
                if row_index == 0 and expected['repeat']:
                    require(len(headers) == 1,
                            f'{path.name}: the header row must carry exactly one w:tblHeader '
                            f'(found {len(headers)})')
                    require(row[0].tag == f'{W}trPr',
                            f'{path.name}: w:trPr must be the first child of the header row')
                    require(len(markers) == 1, f'{path.name}: more than one w:trPr on the header row')
                else:
                    require(not headers,
                            f'{path.name}: row {row_index} carries w:tblHeader but only row 0 may repeat')
                require(f'{W}tcW' not in row.attrib, f'{path.name}: unexpected w:tcW')
                cells = [child for child in row if child.tag == f'{W}tc']
                require(len(cells) == expected['columns'],
                        f'{path.name}: row {row_index} has {len(cells)} cells, expected {expected["columns"]}')
                require(all(child.tag == f'{W}tc' for child in row if child.tag != f'{W}trPr'),
                        f'{path.name}: row {row_index} holds a child that is not w:tc')
            if expected['widths'] is not None:
                # Geometric containment: a fixed-layout table wider than the section's text column
                # overflows the page. Only checked when the package itself declares page geometry.
                page = body.find(f'{W}sectPr/{W}pgSz')
                margins = body.find(f'{W}sectPr/{W}pgMar')
                if page is not None and margins is not None:
                    text_width = (int(page.get(f'{W}w')) - int(margins.get(f'{W}left'))
                                  - int(margins.get(f'{W}right')))
                    require(sum(expected['widths']) <= text_width,
                            f'{path.name}: table is wider than the {text_width} twip text column '
                            f'({sum(expected["widths"])} twips)')

            first = rows[0].findall(f'{W}tc')[0]
            require(cell_text(first) == expected['first_cell'],
                    f'{path.name}: first cell text {cell_text(first)!r} != declared '
                    f'{expected["first_cell"]!r}')


def check_with_python_docx(path: Path, intent: list[dict]) -> None:
    import docx  # imported lazily so the raw-OPC checks can run without it

    document = docx.Document(str(path))
    require(len(document.tables) == len(intent),
            f'{path.name}: python-docx sees {len(document.tables)} tables, declared {len(intent)}')
    for table, expected in zip(document.tables, intent):
        require(len(table.columns) == expected['columns'],
                f'{path.name}: python-docx column count {len(table.columns)} != {expected["columns"]}')
        require(len(table.rows) == expected['rows'],
                f'{path.name}: python-docx row count {len(table.rows)} != {expected["rows"]}')
        if expected['widths'] is None:
            require(table.autofit is True,
                    f'{path.name}: python-docx reports autofit False for a table without widths')
            measured = [column.width for column in table.columns]
            require(all(width is None for width in measured),
                    f'{path.name}: python-docx reads widths {measured} from an auto table')
        else:
            require(table.autofit is False,
                    f'{path.name}: python-docx reports autofit True for a fixed-width table')
            measured = [int(column.width) for column in table.columns]
            expected_emu = [width * EMU_PER_TWIP for width in expected['widths']]
            require(measured == expected_emu,
                    f'{path.name}: python-docx widths {measured} EMU != declared {expected_emu} EMU')
        require(table.cell(0, 0).text == expected['first_cell'],
                f'{path.name}: python-docx first cell {table.cell(0, 0).text!r} != declared '
                f'{expected["first_cell"]!r}')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'tables.tsv')
    tables = 0
    for name, expected in intent.items():
        path = directory / f'{name}.docx'
        require(path.is_file(), f'missing package {path}')
        check_package(path, expected)
        check_with_python_docx(path, expected)
        tables += len(expected)
    extra = {path.stem for path in directory.glob('*.docx')} - set(intent)
    require(not extra, f'unexpected packages without a declared intent: {sorted(extra)}')
    return {'documents': len(intent), 'tables': tables}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-python-docx', action='store_true',
                        help='fail instead of downgrading when python-docx is unavailable')
    args = parser.parse_args(argv)
    try:
        import docx  # noqa: F401
    except Exception as error:
        if args.require_python_docx:
            print(f'R81 WRITER TABLE CHECK FAILED: python-docx unavailable: {error}', file=sys.stderr)
            return 1
        print(f'python-docx unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R81 WRITER TABLE CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R81 writer tables: {report['documents']} packages and {report['tables']} tables verified "
        "with python-docx 1.2 and raw OPC inspection; header repetition is structural only. "
        "Not Word rendering or Android evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
