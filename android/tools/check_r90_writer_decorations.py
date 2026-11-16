#!/usr/bin/env python3
"""Independent verification for the R90 Writer run decorations (`w:vertAlign`, `w:highlight`).

`DocxPackage.Run` may now declare `verticalAlign` (ST_VerticalAlignRun: `superscript`/`subscript`) and
`highlight` (the closed sixteen-token ST_HighlightColor set). This verifier opens every package twice:

1. **raw OPC** — each declared decoration must be physically present with the declared token, an undeclared
   one must be absent, and the CT_RPr order `color < sz < highlight < u < vertAlign` must hold for the
   elements this writer emits;
2. **python-docx 1.2** — `run.font.superscript`, `run.font.subscript` and `run.font.highlight_color` are read
   through python-docx's API. python-docx reports a highlight as a `WD_COLOR_INDEX` member, whose *names*
   differ from ST_HighlightColor (`cyan` arrives as `TURQUOISE`, `green` as `BRIGHT_GREEN`); this verifier
   encodes the documented mapping only for the tokens its fixtures exercise and refuses to guess the rest
   rather than silently accepting any enum member.

Host-only. No Word, browser or Android execution is involved; python-docx is a reader, not a renderer.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
FIELDS = 12
ORDER = ['color', 'sz', 'highlight', 'u', 'vertAlign']
# ST_HighlightColor -> the WD_COLOR_INDEX member python-docx reports. Only exercised tokens are mapped.
WD_HIGHLIGHT = {
    'black': 'BLACK', 'blue': 'BLUE', 'cyan': 'TURQUOISE', 'darkBlue': 'DARK_BLUE',
    'darkGray': 'GRAY_25', 'darkGreen': 'GREEN', 'darkRed': 'DARK_RED', 'green': 'BRIGHT_GREEN',
    'lightGray': 'GRAY_25', 'magenta': 'PINK', 'red': 'RED', 'white': 'WHITE', 'yellow': 'YELLOW',
}


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
            out.append({'n': '\n', 't': '\t', '\\': '\\'}.get(text[index + 1], text[index + 1]))
            index += 2
            continue
        out.append(char)
        index += 1
    return ''.join(out)


def intent_table(path: Path) -> dict[str, list[list[str]]]:
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


def body_paragraphs(root: ElementTree.Element) -> list[ElementTree.Element]:
    body = root.find(f'{W}body')
    require(body is not None, 'missing w:body')
    return [child for child in body if child.tag == f'{W}p']


def verify_raw(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    on_disk = {path.stem for path in folder.glob('*.docx')}
    require(on_disk == set(table), f'decorations.tsv names {sorted(table)} but the folder holds {sorted(on_disk)}')
    packages = runs = aligned = highlighted = 0
    for name, rows in table.items():
        with zipfile.ZipFile(folder / f'{name}.docx') as archive:
            require('word/document.xml' in archive.namelist(), f'{name}: missing word/document.xml')
            root = ElementTree.fromstring(archive.read('word/document.xml'))
        found = [run for paragraph in body_paragraphs(root) for run in paragraph.findall(f'{W}r')]
        require(len(found) == len(rows), f'{name}: {len(found)} runs on disk, declared {len(rows)}')
        packages += 1
        for row, run in zip(rows, found):
            where = f'{name} paragraph {row[1]} run {row[2]}'
            declared_align, declared_highlight = row[4], row[5]
            pr = run.find(f'{W}rPr')
            align_el = pr.find(f'{W}vertAlign') if pr is not None else None
            highlight_el = pr.find(f'{W}highlight') if pr is not None else None
            if declared_align == '-':
                require(align_el is None, f'{where}: undeclared w:vertAlign written')
            else:
                require(align_el is not None and align_el.get(f'{W}val') == declared_align,
                        f'{where}: w:vertAlign is '
                        f'{None if align_el is None else align_el.get(f"{W}val")!r}, declared {declared_align!r}')
                aligned += 1
            if declared_highlight == '-':
                require(highlight_el is None, f'{where}: undeclared w:highlight written')
            else:
                require(highlight_el is not None and highlight_el.get(f'{W}val') == declared_highlight,
                        f'{where}: w:highlight is '
                        f'{None if highlight_el is None else highlight_el.get(f"{W}val")!r}, '
                        f'declared {declared_highlight!r}')
                highlighted += 1
            if pr is not None:
                names = [child.tag.replace(W, '') for child in pr]
                sequence = [attribute for attribute in names if attribute in ORDER]
                require(sequence == sorted(sequence, key=ORDER.index),
                        f'{where}: CT_RPr order violated: {names}')
            runs += 1
    return {'packages': packages, 'runs': runs, 'aligned': aligned, 'highlighted': highlighted}


def verify_python_docx(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    import docx  # noqa: PLC0415  (optional dependency, checked by the caller)

    runs = aligned = highlighted = 0
    for name, rows in table.items():
        document = docx.Document(str(folder / f'{name}.docx'))
        flat = [run for paragraph in document.paragraphs for run in paragraph.runs]
        require(len(flat) == len(rows), f'{name}: python-docx sees {len(flat)} runs, declared {len(rows)}')
        for row, run in zip(rows, flat):
            where = f'python-docx {name} paragraph {row[1]} run {row[2]}'
            declared_align, declared_highlight = row[4], row[5]
            superscript = bool(run.font.superscript)
            subscript = bool(run.font.subscript)
            require(not (superscript and subscript), f'{where}: python-docx reports both super and subscript')
            actual = 'superscript' if superscript else ('subscript' if subscript else '-')
            require(actual == declared_align, f'{where}: python-docx reports {actual!r}, declared {declared_align!r}')
            if declared_align != '-':
                aligned += 1
            colour = run.font.highlight_color
            actual_highlight = None if colour is None else str(colour).split('.')[-1].split(' ')[0]
            if declared_highlight == '-':
                require(actual_highlight is None, f'{where}: python-docx reports highlight {actual_highlight!r}')
            else:
                expected = WD_HIGHLIGHT.get(declared_highlight)
                require(expected is not None,
                        f'{where}: this verifier has no documented WD_COLOR_INDEX mapping for '
                        f'{declared_highlight!r}; refusing to guess')
                require(actual_highlight == expected,
                        f'{where}: python-docx reports {actual_highlight!r}, expected {expected!r} '
                        f'for ST {declared_highlight!r}')
                highlighted += 1
            runs += 1
    return {'reader_runs': runs, 'reader_aligned': aligned, 'reader_highlighted': highlighted}


def verify(folder: Path, require_docx: bool) -> dict[str, int]:
    table = intent_table(folder / 'decorations.tsv')
    totals = verify_raw(folder, table)
    if require_docx:
        try:
            import docx  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-python-docx was requested but python-docx is missing: {error}')
        totals.update(verify_python_docx(folder, table))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-python-docx', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_python_docx)
    except Fail as failure:
        print(f'R90 writer decoration verification FAILED: {failure}')
        return 1
    message = (f'R90 independent ZIP/XML checks: {totals["packages"]} packages, {totals["runs"]} runs '
               f'({totals["aligned"]} aligned, {totals["highlighted"]} highlighted) verified')
    if 'reader_runs' in totals:
        message += (f'; python-docx agreed on {totals["reader_runs"]} runs, {totals["reader_aligned"]} '
                    f'alignment values and {totals["reader_highlighted"]} highlight values through its Font API')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
