#!/usr/bin/env python3
"""Independent verification for the R88 Writer paragraph-border packages (`w:pBdr`).

`DocxPackage` may now emit a `w:pBdr` box inside `w:pPr`: zero to six edges in CT_PBdr order (top, left,
bottom, right, between, bar), each with `w:val`, `w:sz`, `w:space` and `w:color`. This verifier opens every
package twice:

1. **raw OPC** — for every body paragraph in document order, `w:pBdr` must be absent exactly when the
   intent table says no box exists. Otherwise the edge *names* must match the declared set, in schema
   order, no undeclared edge may appear, each edge must carry all four attributes with the declared
   values, and `w:pBdr` must sit at the CT_PPr position between `w:numPr`/`w:suppressLineNumbers` and
   `w:shd`.
2. **python-docx 1.2** — the same boxes are re-read from python-docx's own lxml tree. python-docx has no
   paragraph-border API (its `ParagraphFormat` exposes no `border`), so agreement here is evidence that a
   second, independent parser of the same part sees the same six-edge model; it is not Word.

Host-only. Nothing here is rendering evidence, and no Word/Excel/LibreOffice process is involved. The
rendered consequences of the same bytes are checked by the docx-preview lane.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
EDGES = ['top', 'left', 'bottom', 'right', 'between', 'bar']
PROPERTY_ORDER = ['pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'framePr', 'widowControl',
                  'numPr', 'suppressLineNumbers', 'pBdr', 'shd', 'tabs', 'suppressAutoHyphens',
                  'kinsoku', 'wordWrap', 'overflowPunct', 'topLinePunct', 'autoSpaceDE',
                  'autoSpaceDN', 'bidi', 'adjustRightInd', 'snapToGrid', 'spacing', 'ind',
                  'contextualSpacing', 'mirrorIndents', 'suppressOverlap', 'jc']
FIELDS = 6


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
        name, index, kind, box, shading, tabs = parts
        edges = []
        if box != '-':
            for spec in box.split(';'):
                edge, style, size, space, color = spec.split(':')
                require(edge in EDGES, f'unknown edge {edge!r} in {line!r}')
                edges.append({'edge': edge, 'style': style, 'size': int(size), 'space': int(space),
                              'color': color})
        table.setdefault(name, []).append({'index': int(index), 'kind': kind, 'edges': edges,
                                           'shading': shading, 'tabs': tabs})
    return table


def body_paragraphs(root: ElementTree.Element) -> list[ElementTree.Element]:
    body = root.find(f'{W}body')
    require(body is not None, 'missing w:body')
    paragraphs = []
    for child in body:
        if child.tag == f'{W}p':
            paragraphs.append(child)
        elif child.tag == f'{W}tbl':
            raise Fail('the R88 fixtures contain no tables; a table would break the index mapping')
    return paragraphs


def read_box(paragraph: ElementTree.Element, where: str) -> list[dict]:
    ppr = paragraph.find(f'{W}pPr')
    if ppr is None:
        return []
    element = ppr.find(f'{W}pBdr')
    if element is None:
        return []
    children = list(element)
    require(children, f'{where}: an empty w:pBdr must not be written')
    names = [child.tag.replace(W, '') for child in children]
    require(names == [name for name in EDGES if name in names],
            f'{where}: edges {names} are not in CT_PBdr order')
    edges = []
    for child in children:
        name = child.tag.replace(W, '')
        for attribute in ('val', 'sz', 'space', 'color'):
            require(f'{W}{attribute}' in child.attrib,
                    f'{where}: the {name} edge is missing w:{attribute}')
        edges.append({'edge': name, 'style': child.get(f'{W}val'), 'size': int(child.get(f'{W}sz')),
                      'space': int(child.get(f'{W}space')), 'color': child.get(f'{W}color')})
    position = [child.tag.replace(W, '') for child in ppr]
    index = position.index('pBdr')
    for before in ('pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'widowControl'):
        if before in position:
            require(position.index(before) < index, f'{where}: w:{before} must precede w:pBdr')
    for after in ('shd', 'tabs', 'spacing', 'ind', 'jc'):
        if after in position:
            require(position.index(after) > index, f'{where}: w:{after} must follow w:pBdr')
    require(position == sorted(position, key=PROPERTY_ORDER.index),
            f'{where}: w:pPr children {position} violate CT_PPrBase order')
    return edges


def verify_raw(folder: Path, table: dict[str, list[dict]]) -> dict[str, int]:
    packages = paragraphs = bordered = edges_total = 0
    for name, rows in table.items():
        path = folder / f'{name}.docx'
        require(path.is_file(), f'missing package {path}')
        with zipfile.ZipFile(path) as archive:
            require('word/document.xml' in archive.namelist(), f'{name}: missing word/document.xml')
            root = ElementTree.fromstring(archive.read('word/document.xml'))
        found = body_paragraphs(root)
        require(len(found) == len(rows),
                f'{name}: {len(found)} body paragraphs, intent table declares {len(rows)}')
        packages += 1
        for row, paragraph in zip(rows, found):
            where = f'{name}[{row["index"]}]'
            declared = row['edges']
            actual = read_box(paragraph, where)
            require([edge['edge'] for edge in actual] == [edge['edge'] for edge in declared],
                    f'{where}: edges {[e["edge"] for e in actual]} != declared '
                    f'{[e["edge"] for e in declared]}')
            for want, got in zip(declared, actual):
                for key in ('style', 'size', 'space', 'color'):
                    require(want[key] == got[key],
                            f'{where}: {want["edge"]} {key} {got[key]!r} != {want[key]!r}')
            # An undeclared edge must be physically absent, not merely inert.
            ppr = paragraph.find(f'{W}pPr')
            element = ppr.find(f'{W}pBdr') if ppr is not None else None
            if element is not None:
                present = {child.tag.replace(W, '') for child in element}
                require(present == {edge['edge'] for edge in declared},
                        f'{where}: physical edges {present} != declared')
            paragraphs += 1
            if actual:
                bordered += 1
                edges_total += len(actual)
    return {'packages': packages, 'paragraphs': paragraphs, 'bordered': bordered,
            'edges': edges_total}


def verify_python_docx(folder: Path, table: dict[str, list[dict]]) -> dict[str, int]:
    import docx  # noqa: PLC0415  (optional dependency, checked by the caller)

    packages = paragraphs = borders = 0
    for name, rows in table.items():
        document = docx.Document(str(folder / f'{name}.docx'))
        require(len(document.paragraphs) == len(rows),
                f'{name}: python-docx sees {len(document.paragraphs)} paragraphs, '
                f'intent table declares {len(rows)}')
        packages += 1
        for row, paragraph in zip(rows, document.paragraphs):
            where = f'python-docx {name}[{row["index"]}]'
            ppr = paragraph._p.pPr
            element = ppr.find(f'{W}pBdr') if ppr is not None else None
            if not row['edges']:
                require(element is None, f'{where}: unexpected w:pBdr')
                paragraphs += 1
                continue
            require(element is not None, f'{where}: missing w:pBdr')
            actual = []
            for child in element:
                actual.append({'edge': child.tag.replace(W, ''),
                               'style': child.get(f'{W}val'),
                               'size': int(child.get(f'{W}sz')),
                               'space': int(child.get(f'{W}space')),
                               'color': child.get(f'{W}color')})
            require([e['edge'] for e in actual] == [e['edge'] for e in row['edges']],
                    f'{where}: edges {[e["edge"] for e in actual]} != declared')
            for want, got in zip(row['edges'], actual):
                for key in ('style', 'size', 'space', 'color'):
                    require(want[key] == got[key], f'{where}: {want["edge"]} {key} mismatch')
            paragraphs += 1
            borders += 1
    return {'packages': packages, 'paragraphs': paragraphs, 'bordered': borders}


def verify(folder: Path, require_docx: bool) -> dict[str, int]:
    table = intent_table(folder / 'borders.tsv')
    require(table, 'the intent table is empty')
    # A verifier that only walks the table would silently ignore a package the table forgot.
    on_disk = {path.stem for path in folder.glob('*.docx')}
    require(on_disk == set(table),
            f'borders.tsv names {sorted(table)} but the folder holds {sorted(on_disk)}')
    totals = verify_raw(folder, table)
    totals['raw_packages'] = totals['packages']
    if require_docx:
        try:
            import docx  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-python-docx was requested but python-docx is missing: {error}')
        reader = verify_python_docx(folder, table)
        totals['reader_packages'] = reader['packages']
        totals['reader_paragraphs'] = reader['paragraphs']
        totals['reader_bordered'] = reader['bordered']
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-python-docx', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_python_docx)
    except Fail as failure:
        print(f'R88 writer border verification FAILED: {failure}')
        return 1
    message = (f'R88 independent ZIP/XML checks: {totals["raw_packages"]} packages, '
               f'{totals["paragraphs"]} body paragraphs ({totals["bordered"]} bordered, '
               f'{totals["edges"]} edges) verified')
    if 'reader_packages' in totals:
        message += (f'; python-docx (lxml tree) agreed on {totals["reader_paragraphs"]} paragraphs and '
                    f'{totals["reader_bordered"]} boxes. python-docx has no border API, so this is '
                    'package-structure agreement, not rendering')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
