#!/usr/bin/env python3
"""Independent verification for the R89 Writer run-styling packages (`<w:color>`, `<w:sz>` in `w:rPr`).

`DocxPackage.Run` may now declare `colorRgb` (6-hex, written as `<w:color w:val="RRGGBB"/>`) and
`sizeHalfPoints` (2..1638, written as `<w:sz w:val="N"/>`). This verifier opens every package twice:

1. **raw OPC** — for every run in document order, `w:rPr` must be absent when nothing is declared, each
   declared attribute must carry exactly the declared value, an undeclared attribute must be physically
   absent, and the CT_RPr order `b < i < color < sz < u` must hold for the elements this writer emits;
2. **python-docx 1.2** — `run.font.color.rgb`, `run.font.size.pt`, `run.font.bold` and `run.font.italic`
   are read through python-docx's own API (unlike `w:pBdr`, these *are* modelled), so the declared values
   have to survive into a second implementation's object model, not just its XML tree.

Host-only. python-docx is a reader, not Word: agreement here is package/object-model evidence, never
rendering. What a renderer does with the same bytes is checked by the docx-preview lane.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
FIELDS = 10
ORDER = ['b', 'i', 'color', 'sz', 'u']


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
    paragraphs = []
    for child in body:
        if child.tag == f'{W}p':
            paragraphs.append(child)
        elif child.tag == f'{W}tbl':
            raise Fail('the R89 fixtures contain no tables; a table would break the index mapping')
    return paragraphs


def run_text(run: ElementTree.Element) -> str:
    pieces = []
    for child in run:
        if child.tag == f'{W}t':
            pieces.append(child.text or '')
        elif child.tag == f'{W}tab':
            pieces.append('\t')
        elif child.tag == f'{W}br':
            pieces.append('\n')
    return ''.join(pieces)


def verify_raw(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    on_disk = {path.stem for path in folder.glob('*.docx')}
    require(on_disk == set(table), f'runs.tsv names {sorted(table)} but the folder holds {sorted(on_disk)}')
    packages = runs = styled = 0
    for name, rows in table.items():
        with zipfile.ZipFile(folder / f'{name}.docx') as archive:
            require('word/document.xml' in archive.namelist(), f'{name}: missing word/document.xml')
            root = ElementTree.fromstring(archive.read('word/document.xml'))
        rendered = []
        for paragraph in body_paragraphs(root):
            for run in paragraph.findall(f'{W}r'):
                rendered.append(run)
        require(len(rendered) == len(rows),
                f'{name}: {len(rendered)} runs on disk, intent table declares {len(rows)}')
        packages += 1
        for row, run in zip(rows, rendered):
            where = f'{name} paragraph {row[1]} run {row[2]}'
            declared_text, colour, size, bold, italic, underline, strike = row[3:10]
            require(run_text(run) == unescape(declared_text),
                    f'{where}: text {run_text(run)!r} != declared {unescape(declared_text)!r}')
            pr = run.find(f'{W}rPr')
            wants_rpr = (colour != '-' or size != '-'
                         or 'true' in (bold, italic, underline, strike))
            if not wants_rpr:
                require(pr is None, f'{where}: unexpected w:rPr')
                runs += 1
                continue
            require(pr is not None, f'{where}: missing w:rPr')
            names = [child.tag.replace(W, '') for child in pr]
            colour_el = pr.find(f'{W}color')
            size_el = pr.find(f'{W}sz')
            if colour == '-':
                require(colour_el is None, f'{where}: undeclared colour written')
            else:
                require(colour_el is not None and colour_el.get(f'{W}val') == colour,
                        f'{where}: colour {None if colour_el is None else colour_el.get(f"{W}val")!r} '
                        f'!= {colour!r}')
            if size == '-':
                require(size_el is None, f'{where}: undeclared size written')
            else:
                require(size_el is not None and size_el.get(f'{W}val') == size,
                        f'{where}: size {None if size_el is None else size_el.get(f"{W}val")!r} != {size!r}')
            for wanted, attribute in (('true', 'b'), ('true', 'i'), ('true', 'u'), ('true', 'strike')):
                present = attribute in names
                declared_flag = {'b': bold, 'i': italic, 'u': underline, 'strike': strike}[attribute]
                require(present == (declared_flag == wanted),
                        f'{where}: w:{attribute} present={present} declared={declared_flag}')
            # The elements that appear, in the order they appear, must already be in CT_RPr order.
            sequence = [name for name in names if name in ORDER]
            require(sequence == sorted(sequence, key=ORDER.index),
                    f'{where}: CT_RPr order violated: {names}')
            runs += 1
            if colour != '-' or size != '-':
                styled += 1
    return {'packages': packages, 'runs': runs, 'styled': styled}


def verify_python_docx(folder: Path, table: dict[str, list[list[str]]]) -> dict[str, int]:
    import docx  # noqa: PLC0415  (optional dependency, checked by the caller)

    packages = runs = 0
    colours = sizes = 0
    for name, rows in table.items():
        document = docx.Document(str(folder / f'{name}.docx'))
        flat = [run for paragraph in document.paragraphs for run in paragraph.runs]
        require(len(flat) == len(rows),
                f'{name}: python-docx sees {len(flat)} runs, intent table declares {len(rows)}')
        packages += 1
        for row, run in zip(rows, flat):
            where = f'python-docx {name} paragraph {row[1]} run {row[2]}'
            colour, size, bold, italic, underline, strike = row[4:10]
            actual_colour = run.font.color
            actual_rgb = None if actual_colour is None or actual_colour.rgb is None else str(actual_colour.rgb)
            if colour == '-':
                require(actual_rgb is None, f'{where}: python-docx reports colour {actual_rgb!r}')
            else:
                require(actual_rgb == colour, f'{where}: python-docx reports {actual_rgb!r} != {colour!r}')
                colours += 1
            actual_size = None if run.font.size is None else run.font.size.pt
            if size == '-':
                require(actual_size is None, f'{where}: python-docx reports size {actual_size}')
            else:
                want = int(size) / 2.0
                require(actual_size is not None and abs(actual_size - want) < 0.001,
                        f'{where}: python-docx reports {actual_size} pt != {want}')
                sizes += 1
            for attribute, declared in (('bold', bold), ('italic', italic), ('underline', underline),
                                        ('strike', strike)):
                actual = getattr(run.font, attribute)
                want = declared == 'true'
                require(bool(actual) == want,
                        f'{where}: python-docx reports {attribute}={actual!r}, declared {want}')
            runs += 1
    return {'packages': packages, 'runs': runs, 'colours': colours, 'sizes': sizes}


def verify(folder: Path, require_docx: bool) -> dict[str, int]:
    table = intent_table(folder / 'runs.tsv')
    totals = verify_raw(folder, table)
    if require_docx:
        try:
            import docx  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-python-docx was requested but python-docx is missing: {error}')
        reader = verify_python_docx(folder, table)
        totals['reader_runs'] = reader['runs']
        totals['reader_colours'] = reader['colours']
        totals['reader_sizes'] = reader['sizes']
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-python-docx', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_python_docx)
    except Fail as failure:
        print(f'R89 writer run-style verification FAILED: {failure}')
        return 1
    message = (f'R89 independent ZIP/XML checks: {totals["packages"]} packages, {totals["runs"]} runs '
               f'({totals["styled"]} with a declared colour or size) verified')
    if 'reader_runs' in totals:
        message += (f'; python-docx agreed on {totals["reader_runs"]} runs, {totals["reader_colours"]} '
                    f'colours and {totals["reader_sizes"]} sizes through its Font API')
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
