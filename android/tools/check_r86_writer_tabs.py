#!/usr/bin/env python3
"""Independent verification for the R86 Writer tab-stop, page-break and widow-control packages.

`DocxPackage` may emit, inside `w:pPr`:

* `w:tabs` with one `w:tab` per declared stop — `w:val` (always present, including the schema default
  `left`, because python-docx raises `InvalidXmlError` when it is absent), `w:pos` in twips and an optional
  `w:leader`;
* `w:pageBreakBefore` and `w:widowControl` as tri-state CT_OnOff children — absent when nothing is declared,
  a bare element for an explicit on and `w:val="0"` for an explicit off.

This verifier opens every package twice:

1. **raw OPC** — body paragraphs in document order: element presence must match the declared tri-state,
   each `w:tab` must carry exactly the declared `w:val`/`w:pos`/`w:leader` (with `w:val` mandatory),
   positions must be strictly ascending inside the 0..31680 twip window, the children of `w:pPr` must appear
   in CT_PPr order, and list items, quotes, code blocks and table cells must carry none of the new elements.
2. **python-docx 1.2** — an independent implementation exposing `paragraph_format.tab_stops`
   (`TabStop.position` as a `Length` with `.twips`, `.alignment`, `.leader`), `page_break_before` and
   `widow_control`, all tri-state (`None` when the element is absent). Alignment and leader enums are mapped
   back to their schema tokens and compared with the declared table.

Host-only. python-docx is a reader, not Word: agreement here is package-structure evidence, never
pagination, tab-strip rendering or a visual result.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
INTENT_FIELDS = 6
MAX_POSITION = 31_680
MAX_STOPS = 64
ALIGNS = ('left', 'center', 'right', 'decimal', 'bar', 'clear')
LEADERS = ('dot', 'hyphen', 'underscore', 'heavy', 'middleDot')
PROPERTY_ORDER = ['pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'framePr', 'widowControl',
                  'numPr', 'suppressLineNumbers', 'pBdr', 'shd', 'tabs', 'suppressAutoHyphens',
                  'kinsoku', 'wordWrap', 'overflowPunct', 'topLinePunct', 'autoSpaceDE',
                  'autoSpaceDN', 'bidi', 'adjustRightInd', 'snapToGrid', 'spacing', 'ind',
                  'contextualSpacing', 'mirrorIndents', 'suppressOverlap', 'jc']
ALIGN_BY_PYTHON_DOCX = {'LEFT': 'left', 'CENTER': 'center', 'RIGHT': 'right', 'DECIMAL': 'decimal',
                        'BAR': 'bar', 'CLEAR': 'clear'}
LEADER_BY_PYTHON_DOCX = {'SPACES': None, 'DOTS': 'dot', 'DASHES': 'hyphen', 'LINES': 'underscore',
                         'HEAVY': 'heavy', 'MIDDLE_DOT': 'middleDot'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def flag_value(text: str) -> bool | None:
    """`-` means the element is absent (inherit), `true` a bare element, `false` an explicit off."""
    require(text in ('-', 'true', 'false'), f'bad tri-state flag {text!r}')
    return None if text == '-' else text == 'true'


def parse_stops(text: str, line: int) -> list[dict]:
    if text == '-':
        return []
    stops = []
    for item in text.split(';'):
        parts = item.split(':')
        require(len(parts) == 3, f'intent line {line}: malformed tab stop {item!r}')
        position, align, leader = parts
        require(align in ALIGNS, f'intent line {line}: unknown alignment {align!r}')
        require(leader == 'none' or leader in LEADERS, f'intent line {line}: unknown leader {leader!r}')
        stops.append({'position': int(position), 'align': align,
                      'leader': None if leader == 'none' else leader})
    require(len(stops) <= MAX_STOPS, f'intent line {line}: {len(stops)} stops exceed {MAX_STOPS}')
    for index, stop in enumerate(stops):
        require(0 <= stop['position'] <= MAX_POSITION,
                f'intent line {line}: position {stop["position"]} outside 0..{MAX_POSITION}')
        if index:
            require(stop['position'] > stops[index - 1]['position'],
                    f'intent line {line}: positions are not strictly ascending')
    return stops


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'kind': fields[2],
            'page_break_before': flag_value(fields[3]),
            'widow_control': flag_value(fields[4]),
            'tabs': parse_stops(fields[5], index),
        })
    require(table, 'intent table is empty')
    for name, paragraphs in table.items():
        require([entry['index'] for entry in paragraphs] == list(range(len(paragraphs))),
                f'{name}: paragraph indices are not 0..{len(paragraphs) - 1}')
        for entry in paragraphs:
            if entry['kind'] not in ('heading', 'paragraph'):
                require(entry['page_break_before'] is None and entry['widow_control'] is None
                        and not entry['tabs'],
                        f'{name}: only headings and paragraphs may declare tab stops or the new flags')
    return table


def attr_names(node) -> set[str]:
    return {key.split('}')[-1] if key.startswith('{') else key for key in node.attrib}


def check_on_off(node, declared: bool | None, workbook: str, label: str) -> None:
    require((node is not None) == (declared is not None),
            f'{workbook}: {label} presence {node is not None} != declared {declared!r}')
    if node is None:
        return
    require(attr_names(node) <= {'val'}, f'{workbook}: {label} carries undeclared attributes '
                                        f'{sorted(attr_names(node) - {"val"})}')
    value = node.get(f'{W}val')
    if declared:
        require(value is None or value.lower() not in ('0', 'false', 'off'),
                f'{workbook}: {label} is declared on but carries {value!r}')
    else:
        require(value is not None and value.lower() in ('0', 'false', 'off'),
                f'{workbook}: {label} is declared off but carries {value!r}; an omitted element inherits '
                f'the style hierarchy and is not an override')


def check_package(path: Path, intent: list[dict]) -> None:
    name = path.name
    with zipfile.ZipFile(path) as archive:
        require('word/document.xml' in archive.namelist(), f'{name}: missing word/document.xml')
        root = ElementTree.fromstring(archive.read('word/document.xml').decode('utf-8'))
    body = root.find(f'{W}body')
    require(body is not None, f'{name}: document has no body')
    paragraphs = [child for child in body if child.tag == f'{W}p']
    require(len(paragraphs) == len(intent),
            f'{name}: body paragraph count {len(paragraphs)} != declared {len(intent)}')
    for table in body.findall(f'{W}tbl'):
        for element in ('tabs', 'pageBreakBefore', 'widowControl'):
            require(not list(table.iter(f'{W}{element}')),
                    f'{name}: a table cell carries w:{element}, which this writer never emits')

    for paragraph, expected in zip(paragraphs, intent):
        properties = paragraph.find(f'{W}pPr')
        children = [] if properties is None else [child.tag.replace(W, '') for child in properties]
        unknown = [child for child in children if child not in PROPERTY_ORDER]
        require(not unknown, f'{name}: paragraph {expected["index"]} carries unknown w:pPr children {unknown}')
        ordered = sorted(children, key=PROPERTY_ORDER.index)
        require(children == ordered,
                f'{name}: paragraph {expected["index"]} children {children} are not in CT_PPr order {ordered}')
        tabs = None if properties is None else properties.find(f'{W}tabs')
        break_before = None if properties is None else properties.find(f'{W}pageBreakBefore')
        widow = None if properties is None else properties.find(f'{W}widowControl')

        if expected['kind'] not in ('heading', 'paragraph'):
            require(tabs is None and break_before is None and widow is None,
                    f'{name}: a {expected["kind"]} paragraph carries tab stops or the new flags, but only '
                    f'headings and paragraphs may declare them')

        check_on_off(break_before, expected['page_break_before'], name,
                     f'paragraph {expected["index"]} w:pageBreakBefore')
        check_on_off(widow, expected['widow_control'], name,
                     f'paragraph {expected["index"]} w:widowControl')

        declared = expected['tabs']
        require((tabs is not None) == bool(declared),
                f'{name}: paragraph {expected["index"]} w:tabs presence {tabs is not None} != declared '
                f'{len(declared)} stop(s)')
        if tabs is None:
            continue
        entries = [child for child in tabs if child.tag == f'{W}tab']
        require(len(entries) == len(tabs),
                f'{name}: paragraph {expected["index"]} w:tabs carries a non-w:tab child')
        require(len(entries) == len(declared),
                f'{name}: paragraph {expected["index"]} has {len(entries)} w:tab, declared {len(declared)}')
        for entry, want in zip(entries, declared):
            require(attr_names(entry) <= {'val', 'pos', 'leader'},
                    f'{name}: paragraph {expected["index"]} w:tab carries undeclared attributes '
                    f'{sorted(attr_names(entry) - {"val", "pos", "leader"})}')
            value = entry.get(f'{W}val')
            require(value is not None,
                    f'{name}: paragraph {expected["index"]} w:tab has no w:val; python-docx raises '
                    f'InvalidXmlError on that spelling, so it is not a readable file')
            require(value == want['align'],
                    f'{name}: paragraph {expected["index"]} w:tab w:val {value!r} != declared {want["align"]!r}')
            require(entry.get(f'{W}pos') == str(want['position']),
                    f'{name}: paragraph {expected["index"]} w:tab w:pos {entry.get(f"{W}pos")!r} != '
                    f'declared {want["position"]}')
            leader = entry.get(f'{W}leader')
            require(leader == want['leader'],
                    f'{name}: paragraph {expected["index"]} w:tab w:leader {leader!r} != declared '
                    f'{want["leader"]!r}')


def check_with_python_docx(path: Path, intent: list[dict]) -> None:
    import docx  # imported lazily so the raw-OPC checks can run without it

    document = docx.Document(str(path))
    paragraphs = document.paragraphs
    require(len(paragraphs) == len(intent),
            f'{path.name}: python-docx sees {len(paragraphs)} paragraphs, declared {len(intent)}')
    for paragraph, expected in zip(paragraphs, intent):
        fmt = paragraph.paragraph_format
        require(fmt.page_break_before is expected['page_break_before'],
                f'{path.name}: paragraph {expected["index"]} page_break_before '
                f'{fmt.page_break_before!r} is not the declared {expected["page_break_before"]!r}')
        require(fmt.widow_control is expected['widow_control'],
                f'{path.name}: paragraph {expected["index"]} widow_control {fmt.widow_control!r} is not the '
                f'declared {expected["widow_control"]!r}')
        actual = [
            {'position': stop.position.twips,
             'align': ALIGN_BY_PYTHON_DOCX[stop.alignment.name],
             'leader': LEADER_BY_PYTHON_DOCX[stop.leader.name]}
            for stop in fmt.tab_stops
        ]
        require(actual == expected['tabs'],
                f'{path.name}: paragraph {expected["index"]} tab stops {actual} != declared {expected["tabs"]}')


def verify(folder: Path) -> dict:
    """Runs every check against `folder` and returns the counts it covered."""
    require(folder.is_dir() and not folder.is_symlink(), f'missing/symlink artifact root {folder}')
    table = intent_table(folder / 'tabs.tsv')
    packages = sorted(folder.glob('*.docx'))
    require(packages, f'{folder}: no .docx packages')
    require({path.stem for path in packages} == set(table),
            f'{folder}: packages {sorted(path.stem for path in packages)} != declared {sorted(table)}')
    for path in packages:
        check_package(path, table[path.stem])
        check_with_python_docx(path, table[path.stem])
    return {'packages': len(packages), 'paragraphs': sum(len(entries) for entries in table.values())}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-python-docx', action='store_true')
    args = parser.parse_args(argv)
    report = verify(args.artifacts)
    print(f'R86 independent python-docx/ZIP/XML checks: {report["packages"]} packages and '
          f'{report["paragraphs"]} body paragraphs verified; no Word pagination or rendering')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Fail as error:
        print(f'R86 writer tab verification FAILED: {error}', file=sys.stderr)
        raise SystemExit(1)
