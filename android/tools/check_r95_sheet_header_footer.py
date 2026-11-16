#!/usr/bin/env python3
"""Independent verification for R95 page headers and footers (`<headerFooter>`, §18.3.1.46).

Two layers:

1. **raw OPC** — every declared slot must be a child of `<headerFooter>` in `CT_HeaderFooter` order, the element
   must sit after `<pageSetup>` and before `<rowBreaks>`/`<colBreaks>`, the four flags must be written only when
   declared and always as `0`/`1`, and each slot's text must round-trip the exact code text with every `&`
   escaped as `&amp;` and no raw `<`;
2. **openpyxl 3.1.5** — `worksheet.HeaderFooter` must agree on the four flags and must split each slot into the
   same `&L` / `&C` / `&R` sections this writer's text declares. openpyxl strips `&"font,style"` and `&nn` size
   codes while splitting, so for texts that carry those codes the section *text* is not compared; the section
   *presence* still is.

A header/footer is a *record*: it says nothing about what a printer or a print preview draws. Host-only.

Utility: `python3 tools/check_r95_sheet_header_footer.py testing/build/r95/artifacts [--require-openpyxl]`
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
FIELDS = 10
SLOTS = ['oddHeader', 'oddFooter', 'evenHeader', 'evenFooter', 'firstHeader', 'firstFooter']
FLAGS = ['differentOddEven', 'differentFirst', 'scaleWithDoc', 'alignWithMargins']
WRITE_WHEN_TRUE = {'differentOddEven', 'differentFirst'}
TAIL_ORDER = ['printOptions', 'pageMargins', 'pageSetup', 'headerFooter', 'rowBreaks', 'colBreaks']
SECTIONS = 'LCR'
FONT_CODE = re.compile(r'&"|&\d')


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


def unescape(field: str) -> str:
    return field.replace('\\t', '\t').replace('\\n', '\n')


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


def sections(raw: str) -> dict[str, str]:
    """Split the code text the way a reader does: `&L`/`&C`/`&R` open a section, later codes stay inside it."""
    out: dict[str, str] = {}
    current = None
    start = 0
    index = 0
    while index < len(raw):
        if raw[index] == '&' and index + 1 < len(raw) and raw[index + 1] in SECTIONS:
            if current is not None:
                out[current] = raw[start:index]
            current = raw[index + 1]
            start = index + 2
            index += 2
        elif raw[index] == '&' and index + 1 < len(raw):
            index += 2
        else:
            index += 1
    if current is not None:
        out[current] = raw[start:]
    return out


def sheet_groups(rows: list[list[str]]) -> dict[tuple[str, str], list[list[str]]]:
    grouped: dict[tuple[str, str], list[list[str]]] = {}
    for row in rows:
        grouped.setdefault((row[0], row[1]), []).append(row)
    return grouped


def verify_raw(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    declared = {row[0] for row in rows}
    on_disk = {path.name for path in folder.glob('*.xlsx')}
    require(declared <= on_disk,
            f'the intent table declares files that are absent: {sorted(declared - on_disk)}')
    for name in sorted(declared):
        described = {row[1] for row in rows if row[0] == name}
        present = sheet_names(folder, name)
        require(described == present,
                f'{name}: the intent table describes {sorted(described)}, the workbook has {sorted(present)}')

    with_header = 0
    without = 0
    for (name, sheet_name), group in sorted(sheet_groups(rows).items()):
        has = group[0][2]
        require(has in {'0', '1'}, f'{name}:{sheet_name} has={has!r}')
        require(len(group) == (len(SLOTS) if has == '1' else 1),
                f'{name}:{sheet_name} has {len(group)} intent rows; expected '
                f'{len(SLOTS) if has == "1" else 1} for has={has}')
        part = parts_of(folder, name).get(sheet_name)
        require(part is not None, f'{name}: sheet {sheet_name!r} is not in workbook.xml')
        with zipfile.ZipFile(folder / name) as archive:
            raw = archive.read(part).decode('utf-8')
        root = parse_part(raw, f'{name}:{sheet_name} {part}')
        children = {child.tag.split('}')[-1]: child for child in root}
        element = children.get('headerFooter')

        if has == '0':
            require(element is None, f'{name}:{sheet_name} declares no header/footer but carries one')
            require(not any(row[3] != '-' and row[4] == '1' for row in group),
                    f'{name}:{sheet_name} declares no header/footer but claims a present slot')
            without += 1
            continue

        require(element is not None, f'{name}:{sheet_name} declares a header/footer but carries none')
        with_header += 1

        # CT_Worksheet order: the element sits in the print tail, after pageSetup and before the breaks.
        order = [tag for tag in TAIL_ORDER if tag in children]
        require(order == [tag for tag in TAIL_ORDER if tag in children],
                f'{name}:{sheet_name} tail order {order} is not {TAIL_ORDER}')
        if 'pageSetup' in children:
            require(list(children).index('pageSetup') < list(children).index('headerFooter'),
                    f'{name}:{sheet_name} puts <headerFooter> before <pageSetup>')
        for later in ('rowBreaks', 'colBreaks'):
            if later in children:
                require(list(children).index('headerFooter') < list(children).index(later),
                        f'{name}:{sheet_name} puts <headerFooter> after <{later}>')

        # Children in schema order, and no child outside the six slots.
        slots_seen = [child.tag.split('}')[-1] for child in element]
        require(all(slot in SLOTS for slot in slots_seen),
                f'{name}:{sheet_name} headerFooter carries an unknown child: {slots_seen}')
        require(slots_seen == [slot for slot in SLOTS if slot in slots_seen],
                f'{name}:{sheet_name} headerFooter children {slots_seen} are not in schema order')
        require(len(slots_seen) == len(set(slots_seen)),
                f'{name}:{sheet_name} headerFooter repeats a slot: {slots_seen}')

        # Attributes: only the four flags, only when declared, always 0/1, in schema order.
        attributes = list(element.attrib)
        require(all(attribute in FLAGS for attribute in attributes),
                f'{name}:{sheet_name} headerFooter has an unmodelled attribute: {attributes}')
        require(attributes == [attribute for attribute in FLAGS if attribute in attributes],
                f'{name}:{sheet_name} headerFooter attributes {attributes} are not in schema order')
        for attribute in attributes:
            require(element.get(attribute) in {'0', '1'},
                    f'{name}:{sheet_name} headerFooter {attribute}={element.get(attribute)!r} is not 0/1')

        declared_flags = {'differentOddEven': group[0][6], 'differentFirst': group[0][7],
                          'scaleWithDoc': group[0][8], 'alignWithMargins': group[0][9]}
        for attribute, value in declared_flags.items():
            if attribute in WRITE_WHEN_TRUE:
                # false is the schema default, so a false flag must not be written at all.
                if value == '1':
                    require(element.get(attribute) == '1',
                            f'{name}:{sheet_name} lost {attribute}')
                else:
                    require(attribute not in attributes,
                            f'{name}:{sheet_name} wrote {attribute}={element.get(attribute)!r} '
                            f'for a false flag')
            elif value == '-':
                require(attribute not in attributes,
                        f'{name}:{sheet_name} wrote undeclared {attribute}')
            else:
                require(element.get(attribute) == value,
                        f'{name}:{sheet_name} {attribute}={element.get(attribute)!r} != {value!r}')

        # Slots: presence and exact code text, with every '&' escaped and no raw markup.
        by_slot = {row[3]: row for row in group}
        for slot in SLOTS:
            row = by_slot[slot]
            present = row[4]
            node = element.find(f'{S}{slot}')
            require(present in {'0', '1'}, f'{name}:{sheet_name} {slot} present={present!r}')
            if present == '0':
                require(node is None, f'{name}:{sheet_name} wrote an undeclared {slot}')
                continue
            require(node is not None, f'{name}:{sheet_name} omitted the declared {slot}')
            want = unescape(row[5])
            require(node.text == want,
                    f'{name}:{sheet_name} {slot} text {node.text!r} != {want!r}')
            inner = re.search(rf'<{slot}>(.*)</{slot}>', raw, re.S)
            require(inner is not None, f'{name}:{sheet_name} {slot} is not a simple text element')
            body = inner.group(1)
            require(body.count('&amp;') == want.count('&'),
                    f'{name}:{sheet_name} {slot} escapes {body.count("&amp;")} of {want.count("&")} ampersands')
            require('<' not in body and '>' not in body,
                    f'{name}:{sheet_name} {slot} carries raw markup: {body!r}')
            require(sections(want), f'{name}:{sheet_name} {slot} declares no &L/&C/&R section: {want!r}')
            require(want.startswith(('&L', '&C', '&R')),
                    f'{name}:{sheet_name} {slot} does not open with a section marker: {want!r}')
    return {'header_sheets': with_header, 'plain_sheets': without}


def verify_openpyxl(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415

    checked = 0
    books: dict[str, object] = {}
    for (name, sheet_name), group in sorted(sheet_groups(rows).items()):
        if name not in books:
            with warnings.catch_warnings(record=True) as caught:
                warnings.simplefilter('always')
                books[name] = openpyxl.load_workbook(folder / name)
            parsing = [str(item.message) for item in caught if 'header or footer' in str(item.message)]
            require(not parsing,
                    f'openpyxl could not parse a header/footer this writer emitted: {parsing}')
        sheet = books[name][sheet_name]
        footer = sheet.HeaderFooter
        has = group[0][2]
        declared_flags = {'differentOddEven': group[0][6], 'differentFirst': group[0][7],
                          'scaleWithDoc': group[0][8], 'alignWithMargins': group[0][9]}
        for attribute, value in declared_flags.items():
            got = getattr(footer, attribute)
            if attribute in WRITE_WHEN_TRUE:
                if value == '1':
                    require(got in (True, '1', 'true'),
                            f'openpyxl {name}:{sheet_name} {attribute}={got!r}, declared true')
                else:
                    require(got in (None, False, '0', 'false'),
                            f'openpyxl {name}:{sheet_name} {attribute}={got!r}, declared false')
            else:
                want = None if value == '-' else value == '1'
                require(got == want, f'openpyxl {name}:{sheet_name} {attribute}={got!r} != {want!r}')
        if has == '0':
            require(all(getattr(footer, slot).left.text in (None, '') for slot in SLOTS),
                    f'openpyxl {name}:{sheet_name} invented header/footer content')
            checked += 1
            continue
        for row in group:
            slot, present, field = row[3], row[4], row[5]
            item = getattr(footer, slot)
            if present == '0':
                require(item.left.text in (None, '') and item.center.text in (None, '')
                        and item.right.text in (None, ''),
                        f'openpyxl {name}:{sheet_name} sees an undeclared {slot}')
                continue
            want = unescape(field)
            expected = sections(want)
            for letter, attribute in (('L', 'left'), ('C', 'center'), ('R', 'right')):
                part = getattr(item, attribute)
                got = part.text
                if letter in expected:
                    if FONT_CODE.search(want):
                        require(got is not None,
                                f'openpyxl {name}:{sheet_name} {slot} lost the {letter} section')
                    else:
                        require(got == expected[letter],
                                f'openpyxl {name}:{sheet_name} {slot} {letter}={got!r} != {expected[letter]!r}')
                else:
                    require(got in (None, ''),
                            f'openpyxl {name}:{sheet_name} {slot} invented a {letter} section: {got!r}')
        checked += 1
    return {'reader_sheets': checked}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    rows = intent_table(folder / 'headerfooter.tsv')
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
        print(f'R95 header/footer verification FAILED: {failure}')
        return 1
    message = (f'R95 header/footer: {totals["header_sheets"]} sheet(s) with a headerFooter in schema order, '
               f'{totals["plain_sheets"]} sheet(s) with no header/footer')
    if 'reader_sheets' in totals:
        message += f'; openpyxl agreed on {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
