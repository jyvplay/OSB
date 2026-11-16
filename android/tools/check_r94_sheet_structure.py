#!/usr/bin/env python3
"""Independent verification for R94 merged cells and cell hyperlinks.

Two layers:

1. **raw OPC** — `<mergeCells>`/`<mergeCell>` must be absent exactly when the intent table declares no merges,
   must sit at the `CT_Worksheet` position (after `autoFilter`/`sheetProtection`, before `dataValidations`), must
   carry `count` equal to the number of children, and the ranges must be pairwise disjoint and span at least two
   cells (recomputed here, not read back from the writer). `<hyperlinks>` must hold one `<hyperlink>` per
   declared link in order, with `ref` and then exactly one of `r:id` (external) or `location` (internal), then
   the optional `tooltip`/`display`; every `r:id` must resolve through the sheet's own relationship part to a
   `Type="…/hyperlink"` relationship with `TargetMode="External"` and the declared `Target`; the relationship
   part must exist **iff** the sheet has at least one external link, its `Id`s must be unique, and the package
   must declare the `rels` default content type.
2. **openpyxl 3.1.5** — `worksheet.merged_cells.ranges` and `cell.hyperlink.{target,location,tooltip,display}`
   must agree, and a cell that declares nothing must not carry a link.

Everything here is a *record*. A merged range is a rectangle the grid draws as one cell and a hyperlink is a
relationship; neither says anything about what Excel or a browser does when the user clicks. openpyxl is a
reader, not Excel.

Host-only. Utility: `python3 tools/check_r94_sheet_structure.py testing/build/r94/artifacts [--require-openpyxl]`
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
PKG = '{http://schemas.openxmlformats.org/package/2006/relationships}'
CT = '{http://schemas.openxmlformats.org/package/2006/content-types}'
MERGE_FIELDS = 3
LINK_FIELDS = 7
HYPERLINK_REL = R[1:-1] + '/hyperlink'

# CT_Worksheet child sequence (ECMA-376 Part 1 §18.3.1.99). It is a *sequence*: Excel reports a part whose
# children are out of order as damaged. Only the elements this writer can emit are listed.
WORKSHEET_ORDER = [
    'sheetPr', 'dimension', 'sheetViews', 'sheetFormatPr', 'cols', 'sheetData', 'sheetCalcPr',
    'sheetProtection', 'protectedRanges', 'scenarios', 'autoFilter', 'sortState', 'dataConsolidate',
    'customSheetViews', 'mergeCells', 'phoneticPr', 'conditionalFormatting', 'dataValidations', 'hyperlinks',
    'printOptions', 'pageMargins', 'pageSetup', 'headerFooter', 'rowBreaks', 'colBreaks',
    'customProperties', 'cellWatches', 'ignoredErrors', 'smartTags', 'drawing', 'legacyDrawing',
    'legacyDrawingHF', 'picture', 'oleObjects', 'controls', 'webPublishItems', 'tableParts', 'extLst',
]
CELL = re.compile(r'^([A-Z]{1,3})([1-9][0-9]{0,6})$')
RANGE = re.compile(r'^[A-Z]{1,3}[1-9][0-9]{0,6}(:[A-Z]{1,3}[1-9][0-9]{0,6})?$')
MAX_ROW = 1_048_576
MAX_COLUMN = 16_384


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


def column_index(label: str) -> int:
    value = 0
    for ch in label:
        value = value * 26 + (ord(ch) - ord('A') + 1)
    return value


def box(ref: str) -> tuple[int, int, int, int]:
    """(first row, first column, last row, last column), recomputed here rather than trusted from the writer."""
    require(RANGE.match(ref) is not None, f'not an A1 range: {ref}')
    first, _, rest = ref.partition(':')
    last = rest or first
    first_cell = CELL.match(first)
    last_cell = CELL.match(last)
    require(first_cell is not None and last_cell is not None, f'not an A1 range: {ref}')
    return (int(first_cell.group(2)), column_index(first_cell.group(1)),
            int(last_cell.group(2)), column_index(last_cell.group(1)))


def overlaps(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> bool:
    return a[0] <= b[2] and b[0] <= a[2] and a[1] <= b[3] and b[1] <= a[3]


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


def rels_part(part: str) -> str:
    directory, _, name = part.rpartition('/')
    return f'{directory}/_rels/{name}.rels'


def read_part(folder: Path, name: str, entry: str) -> str | None:
    with zipfile.ZipFile(folder / name) as archive:
        try:
            return archive.read(entry).decode('utf-8')
        except KeyError:
            return None


def worksheet(folder: Path, name: str, part: str) -> tuple[ElementTree.Element, str]:
    raw = read_part(folder, name, part)
    require(raw is not None, f'{name}: missing {part}')
    try:
        return ElementTree.fromstring(raw), raw
    except ElementTree.ParseError as error:
        raise Fail(f'{name}:{part} is not well-formed: {error}') from error


def declarations(path: Path, fields: int) -> dict[tuple[str, str], list[list[str]]]:
    grouped: dict[tuple[str, str], list[list[str]]] = {}
    for row in intent_table(path, fields):
        grouped.setdefault((row[0], row[1]), []).append(row)
    return grouped


def verify_merges(folder: Path, merges: dict[tuple[str, str], list[list[str]]]) -> int:
    checked = 0
    for (name, sheet_name), rows in merges.items():
        require(len(rows) == 1, f'{name}:{sheet_name} declares {len(rows)} merge rows')
        declared = rows[0][2]
        expected = [] if declared == '-' else declared.split(',')
        part = parts_of(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        root, raw = worksheet(folder, name, part)
        nodes = [child for child in root if child.tag == f'{S}mergeCells']
        order = [child.tag.split('}')[-1] for child in root]
        unknown = [item for item in order if item not in WORKSHEET_ORDER]
        require(not unknown, f'{name}:{sheet_name} has unmapped worksheet children {unknown}')
        ordinals = [WORKSHEET_ORDER.index(item) for item in order]
        require(ordinals == sorted(ordinals), f'{name}:{sheet_name} violates the CT_Worksheet sequence: {order}')
        if not expected:
            require(not nodes, f'{name}:{sheet_name} declares no merges but carries <mergeCells>')
            require('mergeCells' not in raw, f'{name}:{sheet_name} mentions mergeCells in raw XML')
            checked += 1
            continue
        require(len(nodes) == 1, f'{name}:{sheet_name} declares {expected} but carries {len(nodes)} <mergeCells>')
        node = nodes[0]
        require(node.get('count') == str(len(expected)),
                f'{name}:{sheet_name} mergeCells count={node.get("count")!r} != {len(expected)}')
        entries = list(node)
        require(all(entry.tag == f'{S}mergeCell' for entry in entries),
                f'{name}:{sheet_name} <mergeCells> holds a non-mergeCell child')
        got = [entry.get('ref') for entry in entries]
        require(got == expected, f'{name}:{sheet_name} merged refs {got} != {expected}')
        require(all(list(entry.attrib) == ['ref'] for entry in entries),
                f'{name}:{sheet_name} a mergeCell carries an extra attribute')
        boxes = []
        for ref in expected:
            area = box(ref)
            require(area[0] != area[2] or area[1] != area[3],
                    f'{name}:{sheet_name} {ref} covers one cell and merges nothing')
            require(area[2] <= MAX_ROW and area[3] <= MAX_COLUMN,
                    f'{name}:{sheet_name} {ref} is outside the grid')
            for other in boxes:
                require(not overlaps(area, other), f'{name}:{sheet_name} {ref} overlaps another merged range')
            boxes.append(area)
        checked += 1
    return checked


def verify_hyperlinks(folder: Path, links: dict[tuple[str, str], list[list[str]]]) -> dict[str, int]:
    checked_links = 0
    checked_parts = 0
    for (name, sheet_name), rows in links.items():
        kinds = {row[3] for row in rows}
        require(kinds <= {'external', 'internal', 'none'}, f'{name}:{sheet_name} unknown link kind {kinds}')
        # A sheet either declares no links at all (one `none` row) or declares only links; a sheet may of
        # course mix internal and external links.
        require('none' not in kinds or (len(rows) == 1 and kinds == {'none'}),
                f'{name}:{sheet_name} mixes a none row with link rows')
        part = parts_of(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        root, raw = worksheet(folder, name, part)
        nodes = [child for child in root if child.tag == f'{S}hyperlinks']
        rels_raw = read_part(folder, name, rels_part(part))
        if 'none' in kinds:
            require(not nodes, f'{name}:{sheet_name} declares no links but carries <hyperlinks>')
            require('hyperlink' not in raw, f'{name}:{sheet_name} mentions hyperlink in raw XML')
            require(rels_raw is None, f'{name}:{sheet_name} has no links but a relationship part exists')
            continue
        require(len(nodes) == 1, f'{name}:{sheet_name} carries {len(nodes)} <hyperlinks> elements')
        external = [row for row in rows if row[3] == 'external']
        require((rels_raw is not None) == bool(external),
                f'{name}:{sheet_name} relationship part presence does not match its external links')
        targets = {}
        if rels_raw is not None:
            try:
                rel_root = ElementTree.fromstring(rels_raw)
            except ElementTree.ParseError as error:
                raise Fail(f'{name}:{rels_part(part)} is not well-formed: {error}') from error
            for item in rel_root.findall(f'{PKG}Relationship'):
                require(item.get('Type') == HYPERLINK_REL,
                        f'{name}:{sheet_name} relationship type {item.get("Type")!r} is not a hyperlink')
                require(item.get('TargetMode') == 'External',
                        f'{name}:{sheet_name} hyperlink relationship without TargetMode="External"')
                require(item.get('Id') not in targets, f'{name}:{sheet_name} duplicate relationship Id')
                targets[item.get('Id')] = item.get('Target')
            require(len(targets) == len({row[4] for row in external}),
                    f'{name}:{sheet_name} has {len(targets)} relationship(s) for '
                    f'{len({row[4] for row in external})} distinct target(s)')
            checked_parts += 1
        node = nodes[0]
        attribute = f'xmlns:r'
        if external:
            require(attribute in raw[:raw.index('<hyperlinks') + 400] or f'xmlns:r="{R[1:-1]}"' in raw,
                    f'{name}:{sheet_name} uses r:id without declaring the relationship namespace')
        entries = list(node)
        require(len(entries) == len(rows),
                f'{name}:{sheet_name} has {len(entries)} hyperlink(s), declared {len(rows)}')
        for entry, row in zip(entries, rows):
            _, _, ref, kind, payload, tooltip, display = row
            require(entry.tag == f'{S}hyperlink', f'{name}:{sheet_name} unexpected child {entry.tag}')
            require(entry.get('ref') == ref, f'{name}:{sheet_name} ref {entry.get("ref")!r} != {ref!r}')
            expected_order = ['ref']
            if kind == 'external':
                identifier = entry.get(f'{R}id')
                require(identifier is not None, f'{name}:{sheet_name} {ref} is external but carries no r:id')
                require(identifier in targets,
                        f'{name}:{sheet_name} {ref} points at unknown relationship {identifier!r}')
                require(targets[identifier] == payload,
                        f'{name}:{sheet_name} {ref} target {targets[identifier]!r} != {payload!r}')
                expected_order.append('id')
            else:
                require(entry.get(f'{R}id') is None, f'{name}:{sheet_name} {ref} internal link carries r:id')
                require(entry.get('location') == payload,
                        f'{name}:{sheet_name} {ref} location {entry.get("location")!r} != {payload!r}')
                expected_order.append('location')
            got = [name.split('}')[-1] for name in entry.attrib]
            require(got == expected_order + [item for item in ('tooltip', 'display') if entry.get(item) is not None],
                    f'{name}:{sheet_name} {ref} attribute order {got} is not ref, id/location, tooltip, display')
            for item, declared in (('tooltip', tooltip), ('display', display)):
                if declared == '-':
                    require(entry.get(item) is None, f'{name}:{sheet_name} {ref} wrote an undeclared {item}')
                else:
                    require(entry.get(item) == declared,
                            f'{name}:{sheet_name} {ref} {item}={entry.get(item)!r} != {declared!r}')
            checked_links += 1
    return {'links': checked_links, 'relationship_parts': checked_parts}


def verify_content_types(folder: Path) -> int:
    checked = 0
    for path in sorted(folder.glob('*.xlsx')):
        with zipfile.ZipFile(path) as archive:
            try:
                raw = archive.read('[Content_Types].xml')
            except KeyError as error:
                raise Fail(f'{path.name}: missing [Content_Types].xml') from error
            names = set(archive.namelist())
        root = ElementTree.fromstring(raw)
        defaults = {item.get('Extension'): item.get('ContentType') for item in root.findall(f'{CT}Default')}
        require(defaults.get('rels') == 'application/vnd.openxmlformats-package.relationships+xml',
                f'{path.name} does not declare the rels default content type')
        overrides = {item.get('PartName') for item in root.findall(f'{CT}Override')}
        for entry in names:
            if entry.endswith('.rels'):
                require(entry.startswith('_rels/') or '/_rels/' in entry,
                        f'{path.name}: relationship part {entry} is not under a _rels directory')
            if entry.startswith('xl/worksheets/sheet'):
                require('/' + entry in overrides, f'{path.name}: {entry} has no content-type override')
        checked += 1
    return checked


def verify_openpyxl(folder: Path, merges, links) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    checked_sheets = 0
    checked_links = 0
    for (name, sheet_name), rows in merges.items():
        declared = rows[0][2]
        expected = set() if declared == '-' else set(declared.split(','))
        link_rows = links.get((name, sheet_name), [])
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            book = openpyxl.load_workbook(folder / name)
        sheet = book[sheet_name]
        got = {str(item) for item in sheet.merged_cells.ranges}
        require(got == expected, f'openpyxl {name}:{sheet_name} merges {sorted(got)} != {sorted(expected)}')
        declared_cells = {row[2]: row for row in link_rows if row[2] != '-'}
        seen = set()
        for row in sheet.iter_rows():
            for cell in row:
                if cell.hyperlink is None:
                    continue
                seen.add(cell.coordinate)
                require(cell.coordinate in declared_cells,
                        f'openpyxl {name}:{sheet_name}!{cell.coordinate} carries an undeclared hyperlink')
                _, _, _, kind, payload, tooltip, display = declared_cells[cell.coordinate]
                link = cell.hyperlink
                if kind == 'external':
                    require(link.target == payload,
                            f'openpyxl {name}:{sheet_name}!{cell.coordinate} target {link.target!r} != {payload!r}')
                else:
                    require(link.location == payload,
                            f'openpyxl {name}:{sheet_name}!{cell.coordinate} location {link.location!r} != {payload!r}')
                for item, declared in (('tooltip', tooltip), ('display', display)):
                    want = None if declared == '-' else declared
                    got_value = getattr(link, item)
                    require(got_value == want,
                            f'openpyxl {name}:{sheet_name}!{cell.coordinate} {item}={got_value!r} != {want!r}')
                checked_links += 1
        require(seen == set(declared_cells),
                f'openpyxl {name}:{sheet_name} links {sorted(seen)} != declared {sorted(declared_cells)}')
        checked_sheets += 1
    return {'reader_sheets': checked_sheets, 'reader_links': checked_links}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    merges = declarations(folder / 'merges.tsv', MERGE_FIELDS)
    links = declarations(folder / 'hyperlinks.tsv', LINK_FIELDS)
    require(set(merges) == set(links),
            f'merges.tsv and hyperlinks.tsv describe different sheets: {sorted(set(merges) ^ set(links))}')
    files = {path.name for path in folder.glob('*.xlsx')}
    described = {file for file, _ in merges}
    require(files == described,
            f'the intent table and the folder disagree: declared {sorted(described)}, present {sorted(files)}')
    # Every sheet of every workbook must be described by both tables.
    for name in sorted(files):
        expected = sheet_names(folder, name)
        for table, keys in (('merges.tsv', merges), ('hyperlinks.tsv', links)):
            got = {sheet for file, sheet in keys if file == name}
            require(got == expected,
                    f'{name}: {table} describes {sorted(got)}, the workbook has {sorted(expected)}')
    totals = {'merged_sheets': verify_merges(folder, merges)}
    totals.update(verify_hyperlinks(folder, links))
    totals['packages'] = verify_content_types(folder)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, merges, links))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R94 merge/hyperlink verification FAILED: {failure}')
        return 1
    message = (f'R94 independent ZIP/XML checks: {totals["merged_sheets"]} sheet(s) with disjoint merged '
               f'ranges, {totals["links"]} hyperlink record(s) over {totals["relationship_parts"]} relationship '
               f'part(s), {totals["packages"]} package(s) with the rels content type and overrides')
    if 'reader_links' in totals:
        message += (f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s) and '
                    f'{totals["reader_links"]} link(s)')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
