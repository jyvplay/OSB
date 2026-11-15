#!/usr/bin/env python3
"""Independent verification for the R87 Writer paragraph-shading and tab-character packages.

`DocxPackage` may now emit `w:shd` (`w:val="clear" w:color="auto" w:fill="RRGGBB"`) inside `w:pPr`, and a
run whose text contains `\\t` becomes `<w:r><w:tab/>…</w:r>`. This verifier opens every package twice:

1. **raw OPC** — body paragraphs are parsed in document order. `w:shd` must be absent when no fill is
   declared and must carry exactly `val="clear" color="auto" fill=<declared>` otherwise, must sit at the
   CT_PPr position between `w:widowControl` and `w:tabs`, must never appear inside a table, and the number
   of `<w:tab/>` run children must equal the number of declared tab characters.
2. **python-docx 1.2** — `document.paragraphs` must agree on the paragraph count and on the text, where a
   `<w:tab/>` surfaces as `\\t` in `paragraph.text`. python-docx has no shading API, so the fill is only
   checked at the byte level; that is stated rather than papered over.

Host-only. python-docx is a reader, not Word: agreement here is package-structure evidence, never
rendering. The rendered consequences are checked separately by `tools/check_r87_docx_render.py`.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
FIELDS = 6
PROPERTY_ORDER = ['pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'framePr', 'widowControl',
                  'numPr', 'suppressLineNumbers', 'pBdr', 'shd', 'tabs', 'suppressAutoHyphens',
                  'kinsoku', 'wordWrap', 'overflowPunct', 'topLinePunct', 'autoSpaceDE',
                  'autoSpaceDN', 'bidi', 'adjustRightInd', 'snapToGrid', 'spacing', 'ind',
                  'contextualSpacing', 'mirrorIndents', 'suppressOverlap', 'jc']


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def unescape(text: str) -> str:
    out: list[str] = []
    index = 0
    while index < len(text):
        char = text[index]
        if char == '\\' and index + 1 < len(text):
            token = text[index + 1]
            out.append({'n': '\n', 't': '\t', '\\': '\\'}.get(token, token))
            index += 2
            continue
        out.append(char)
        index += 1
    return ''.join(out)


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == FIELDS, f'intent line {number} has {len(fields)} fields, expected {FIELDS}')
        fill = None if fields[3] == '-' else fields[3]
        if fill is not None:
            require(len(fill) == 6 and all(c in '0123456789ABCDEFabcdef' for c in fill),
                    f'intent line {number} declares an invalid fill {fill!r}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'kind': fields[2],
            'fill': None if fill is None else fill.upper(),
            'tabs': fields[4],
            'text': unescape(fields[5]),
        })
    require(table, 'intent table is empty')
    for name, rows in table.items():
        require([row['index'] for row in rows] == list(range(len(rows))),
                f'{name}: paragraph indices are not 0..{len(rows) - 1}')
    return table


def paragraph_text(element: ElementTree.Element) -> str:
    """Run children only: a `w:tab` directly under `w:tabs` is a tab *stop*, not a character."""
    parts: list[str] = []
    for run in element.iter(f'{W}r'):
        for child in run:
            if child.tag == f'{W}t':
                parts.append(child.text or '')
            elif child.tag == f'{W}tab':
                parts.append('\t')
            elif child.tag == f'{W}br':
                parts.append('\n')
    return ''.join(parts)


def run_tab_count(element: ElementTree.Element) -> int:
    """Only `<w:tab/>` children of a run count: a `w:tab` inside `w:tabs` is a tab *stop*."""
    return sum(1 for run in element.iter(f'{W}r') for child in run if child.tag == f'{W}tab')


def check_tab_stops(name: str, element: dict, entry: dict) -> None:
    """A declared stop list must appear verbatim, ascending, with `w:leader` only when one is declared."""
    declared = [] if entry['tabs'] == '-' else entry['tabs'].split(';')
    stops = element.find(f'{W}tabs') if element is not None else None
    if not declared:
        require(stops is None, f'{name} paragraph {entry["index"]}: undeclared w:tabs emitted')
        return
    require(stops is not None, f'{name} paragraph {entry["index"]}: declared tab stops are missing')
    actual = []
    for stop in stops:
        require(stop.tag == f'{W}tab', f'{name} paragraph {entry["index"]}: non-tab child in w:tabs')
        actual.append(f"{stop.get(f'{W}pos')}:{stop.get(f'{W}val')}:{stop.get(f'{W}leader') or 'none'}")
    require(actual == declared,
            f'{name} paragraph {entry["index"]}: stops {actual} != declared {declared}')

def check_raw(name: str, root: ElementTree.Element, intent: list[dict]) -> tuple[int, int]:
    body = root.find(f'{W}body')
    require(body is not None, f'{name}: document has no body')
    paragraphs = [child for child in body if child.tag == f'{W}p']
    require(len(paragraphs) == len(intent),
            f'{name}: body paragraph count {len(paragraphs)} != declared {len(intent)}')
    for table in body.iter(f'{W}tbl'):
        require(not list(table.iter(f'{W}shd')), f'{name}: a table must not inherit paragraph shading')
    shaded = 0
    for entry, element in zip(intent, paragraphs):
        properties = element.find(f'{W}pPr')
        children = list(properties) if properties is not None else []
        names = [child.tag[len(W):] for child in children]
        shd = properties.find(f'{W}shd') if properties is not None else None
        if entry['fill'] is None:
            require(shd is None, f'{name} paragraph {entry["index"]}: undeclared w:shd emitted')
        else:
            require(shd is not None, f'{name} paragraph {entry["index"]}: declared fill {entry["fill"]} missing')
            require(shd.get(f'{W}val') == 'clear',
                    f'{name} paragraph {entry["index"]}: w:val={shd.get(f"{W}val")!r}, expected clear')
            require(shd.get(f'{W}color') == 'auto',
                    f'{name} paragraph {entry["index"]}: w:color={shd.get(f"{W}color")!r}, expected auto')
            require((shd.get(f'{W}fill') or '').upper() == entry['fill'],
                    f'{name} paragraph {entry["index"]}: fill {shd.get(f"{W}fill")!r} != {entry["fill"]}')
            shaded += 1
        if 'shd' in names:
            position = names.index('shd')
            for earlier in ('widowControl', 'numPr', 'pBdr'):
                if earlier in names:
                    require(names.index(earlier) < position,
                            f'{name} paragraph {entry["index"]}: w:shd must follow w:{earlier}')
            for later in ('tabs', 'spacing', 'ind', 'jc'):
                if later in names:
                    require(position < names.index(later),
                            f'{name} paragraph {entry["index"]}: w:shd must precede w:{later}')
            require(all(names.index(name_) <= PROPERTY_ORDER.index(name_) for name_ in names
                        if name_ in PROPERTY_ORDER),
                    f'{name} paragraph {entry["index"]}: CT_PPr order broken by {names}')
        check_tab_stops(name, properties, entry)
        tabs = run_tab_count(element)
        require(tabs == entry['text'].count('\t'),
                f'{name} paragraph {entry["index"]}: {tabs} run tab(s) for '
                f'{entry["text"].count(chr(9))} declared tab character(s)')
        actual = paragraph_text(element)
        require(actual == entry['text'],
                f'{name} paragraph {entry["index"]}: text {actual!r} != declared {entry["text"]!r}')
    return len(paragraphs), shaded


def check_python_docx(path: Path, intent: list[dict], require_reader: bool) -> tuple[int, int]:
    try:
        import docx  # noqa: PLC0415
    except ImportError:
        require(not require_reader, 'python-docx unavailable but --require-python-docx was given')
        return 0, 0
    document = docx.Document(str(path))
    paragraphs = document.paragraphs
    require(len(paragraphs) == len(intent),
            f'{path.name}: python-docx sees {len(paragraphs)} paragraphs, declared {len(intent)}')
    tabs = 0
    for entry, paragraph in zip(intent, paragraphs):
        require(paragraph.text == entry['text'],
                f'{path.name} paragraph {entry["index"]}: python-docx text {paragraph.text!r} '
                f'!= declared {entry["text"]!r}')
        tabs += paragraph.text.count('\t')
    return len(paragraphs), tabs


def verify(folder: Path, require_reader: bool) -> dict:
    intent = intent_table(folder / 'shade.tsv')
    packages = sorted(folder.glob('*.docx'))
    require([p.stem for p in packages] == sorted(intent), 'artifact set and intent table disagree')
    totals = {'packages': 0, 'paragraphs': 0, 'shaded': 0, 'reader_paragraphs': 0, 'reader_tabs': 0}
    for path in packages:
        with zipfile.ZipFile(path) as archive:
            require('word/document.xml' in archive.namelist(), f'{path.name}: missing word/document.xml')
            root = ElementTree.fromstring(archive.read('word/document.xml').decode('utf-8'))
        count, shaded = check_raw(path.stem, root, intent[path.stem])
        totals['packages'] += 1
        totals['paragraphs'] += count
        totals['shaded'] += shaded
        reader_paragraphs, reader_tabs = check_python_docx(path, intent[path.stem], require_reader)
        totals['reader_paragraphs'] += reader_paragraphs
        totals['reader_tabs'] += reader_tabs
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-python-docx', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_python_docx)
    except Fail as failure:
        print(f'R87 writer shade verification FAILED: {failure}')
        return 1
    print(f'R87 independent python-docx/ZIP/XML checks: {totals["packages"]} packages, '
          f'{totals["paragraphs"]} body paragraphs ({totals["shaded"]} shaded) verified; '
          f'python-docx agreed on {totals["reader_paragraphs"]} paragraphs and {totals["reader_tabs"]} tab '
          'character(s). No Word rendering or pagination')
    return 0


if __name__ == '__main__':
    sys.exit(main())
