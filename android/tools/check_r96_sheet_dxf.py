#!/usr/bin/env python3
"""Independent verification for the R96 differential-format table (`<dxfs>`, `<dxf>`, §18.8.3/§18.8.4).

Two layers:

1. **raw OPC** — a workbook that declares no formats must not carry a `<dxfs>` element at all (an empty container
   is a claim that the workbook has a differential-format table); a workbook that declares some must carry exactly
   the declared `<dxf>` children, in the declared id order, with `count` equal to the number of children. The table
   must follow `</cellXfs>` (and `</cellStyles>`), i.e. CT_Stylesheet order, and precede `<tableStyles>` when one is
   present. Each `<dxf>` must hold `<font>` before `<fill>`, the declared font flags only (`<b/>`, `<i/>`, `<strike/>`,
   `<u/>`), a `<color rgb>` of `FF` + six upper-case hex, and, for a solid fill, `<fgColor rgb>` plus
   `<bgColor indexed="64"/>` — the pair Excel writes for a pattern fill whose background is the default.
2. **openpyxl 3.1.5** — `workbook._differential_styles.styles` must agree on the entry count, every flag, the font
   colour and the fill foreground. openpyxl normalises the alpha channel, so colours are compared on six digits.

A differential format is a *reusable* style fragment: several rules may point at the same `<dxf>`, and the table's
indices are what `cfRule@dxfId` resolves against. Nothing here proves how any engine paints a cell; that needs
Excel, LibreOffice or a renderer, none of which this host has. Host-only utility:

    python3 tools/check_r96_sheet_dxf.py testing/build/r96/artifacts [--require-openpyxl]
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
FIELDS = 9
FLAGS = (('bold', 'b'), ('italic', 'i'), ('strike', 'strike'), ('underline', 'u'))


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows: list[list[str]] = []
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def six_hex(value: str, where: str) -> str:
    require(re.fullmatch(r'[0-9A-F]{6}', value) is not None, f'{where}: {value!r} is not six upper-case hex digits')
    return value


def verify_raw(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    totals = {'formats': 0, 'plain_books': 0, 'books_with_table': 0}
    grouped: dict[str, list[list[str]]] = {}
    for row in rows:
        grouped.setdefault(row[0], []).append(row)
    for name in sorted(grouped):
        path = folder / name
        require(path.is_file(), f'missing package {path}')
        with zipfile.ZipFile(path) as archive:
            require('xl/styles.xml' in archive.namelist(), f'{name}: no xl/styles.xml')
            raw = archive.read('xl/styles.xml').decode('utf-8')
        try:
            root = ElementTree.fromstring(raw)
        except ElementTree.ParseError as error:
            raise Fail(f'{name}: xl/styles.xml is not well-formed XML: {error}')
        entries = grouped[name]
        declared = [row for row in entries if row[1] != '-']
        count = int(entries[0][8])
        require(all(int(row[8]) == count for row in entries), f'{name}: the rows disagree about the entry count')
        require(count == len(declared), f'{name}: count column {count} but {len(declared)} entries declared')
        table = root.find(f'{S}dxfs')
        if count == 0:
            require(table is None, f'{name}: a workbook with no differential formats carries a <dxfs> element')
            require('<dxfs' not in raw, f'{name}: the string <dxfs appears in a styles part that declares none')
            totals['plain_books'] += 1
            continue
        totals['books_with_table'] += 1
        require(table is not None, f'{name}: {count} formats declared but no <dxfs> element')
        require(int(table.get('count', '-1')) == count, f'{name}: dxfs@count {table.get("count")} != {count}')
        children = table.findall(f'{S}dxf')
        require(len(children) == count, f'{name}: {len(children)} <dxf> children for count {count}')
        # CT_Stylesheet order: the table follows cellXfs/cellStyles and precedes tableStyles.
        require('</cellXfs>' in raw, f'{name}: the styles part has no cellXfs')
        require(raw.index('<dxfs') > raw.index('</cellXfs>'), f'{name}: <dxfs> must follow </cellXfs>')
        if '<cellStyles' in raw:
            require(raw.index('<dxfs') > raw.index('</cellStyles>'), f'{name}: <dxfs> must follow </cellStyles>')
        if '<tableStyles' in raw:
            require(raw.index('<dxfs') < raw.index('<tableStyles'), f'{name}: <dxfs> must precede <tableStyles>')
        for index, element in enumerate(children):
            where = f'{name}:dxf{index}'
            row = next((item for item in declared if item[1] == str(index)), None)
            require(row is not None, f'{where}: written but not declared')
            order = [child.tag.split('}')[1] for child in element]
            require(set(order) <= {'font', 'fill'}, f'{where}: unexpected children {order}')
            require(order == sorted(order, key=lambda tag: 0 if tag == 'font' else 1),
                    f'{where}: <font> must precede <fill>, got {order}')
            font = element.find(f'{S}font')
            fill = element.find(f'{S}fill')
            for (field, tag), column in zip(FLAGS, (2, 3, 4, 5)):
                wanted = row[column] == '1'
                present = font is not None and font.find(f'{S}{tag}') is not None
                require(present == wanted, f'{where}: {field} written={present} declared={wanted}')
            font_rgb = row[6]
            if font_rgb != '-':
                require(font is not None, f'{where}: a declared font colour has no <font>')
                colour = font.find(f'{S}color')
                require(colour is not None, f'{where}: <font> has no <color> for a declared colour')
                rgb = colour.get('rgb')
                require(rgb is not None, f'{where}: <color> has no rgb attribute')
                require(rgb == 'FF' + six_hex(font_rgb, where), f'{where}: font colour {rgb} != FF{font_rgb}')
            elif font is not None:
                require(font.find(f'{S}color') is None, f'{where}: an undeclared font colour was written')
            fill_rgb = row[7]
            if fill_rgb != '-':
                require(fill is not None, f'{where}: a declared fill has no <fill>')
                pattern = fill.find(f'{S}patternFill')
                require(pattern is not None, f'{where}: <fill> has no <patternFill> (fill children: {order})')
                require(pattern is not None and pattern.get('patternType') == 'solid',
                        f'{where}: patternType {None if pattern is None else pattern.get("patternType")} != solid')
                foreground = pattern.find(f'{S}fgColor')
                require(foreground is not None, f'{where}: solid fill has no <fgColor>')
                written = None if foreground is None else foreground.get('rgb')
                require(written == 'FF' + six_hex(fill_rgb, where), f'{where}: fill colour {written} != FF{fill_rgb}')
                background = pattern.find(f'{S}bgColor')
                require(background is not None, f'{where}: solid fill has no <bgColor>')
                require(background is not None and background.get('indexed') == '64',
                        f'{where}: bgColor must be the default indexed 64, got {None if background is None else background.attrib}')
            elif fill is not None:
                require(fill.find(f'{S}patternFill') is None, f'{where}: an undeclared fill was written')
            totals['formats'] += 1
    return totals


def verify_openpyxl(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415

    totals = {'reader_books': 0, 'reader_formats': 0}
    grouped: dict[str, list[list[str]]] = {}
    for row in rows:
        grouped.setdefault(row[0], []).append(row)
    for name in sorted(grouped):
        workbook = openpyxl.load_workbook(folder / name)
        styles = list(workbook._differential_styles.styles)  # noqa: SLF001 - the only public-enough surface
        declared = [row for row in grouped[name] if row[1] != '-']
        require(len(styles) == len(declared),
                f'{name}: openpyxl read {len(styles)} differential styles, {len(declared)} declared')
        for index, row in enumerate(declared):
            style = styles[index]
            where = f'{name}:dxf{index}'
            font = style.font
            for (field, _), column in zip(FLAGS, (2, 3, 4, 5)):
                written = bool(getattr(font, field, False))
                require(written == (row[column] == '1'), f'{where}: openpyxl {field} {written} != {row[column]}')
            if row[6] != '-':
                colour = None if font is None else font.color
                rgb = getattr(colour, 'rgb', None)
                require(rgb is not None, f'{where}: openpyxl lost the font colour')
                require(str(rgb)[-6:] == row[6], f'{where}: openpyxl font colour {rgb} != {row[6]}')
            else:
                # openpyxl leaves font.color as None when <font> carries no <color>; anything else is a claim.
                rgb = None if font is None else getattr(font.color, 'rgb', None)
                require(rgb in (None, 'FF000000'), f'{where}: openpyxl invented a font colour {rgb}')
            if row[7] != '-':
                pattern = style.fill
                foreground = getattr(pattern, 'fgColor', None)
                filled = getattr(foreground, 'rgb', None)
                require(filled is not None, f'{where}: openpyxl lost the fill foreground')
                require(str(filled)[-6:] == row[7], f'{where}: openpyxl fill {filled} != {row[7]}')
                require(getattr(pattern, 'patternType', None) == 'solid',
                        f'{where}: openpyxl patternType {getattr(pattern, "patternType", None)} != solid')
            totals['reader_formats'] += 1
        totals['reader_books'] += 1
    return totals


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    require(folder.is_dir(), f'no artifact folder {folder}')
    rows = intent_table(folder / 'dxfs.tsv')
    totals = verify_raw(folder, rows)
    require(totals['formats'] > 0, 'no differential format was written at all')
    require(totals['plain_books'] > 0, 'no rule-free workbook was produced, so the empty case is untested')
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
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
        print(f'R96 differential-format verification FAILED: {failure}')
        return 1
    except (zipfile.BadZipFile, KeyError) as error:
        print(f'R96 differential-format verification FAILED: unreadable package: {error}')
        return 1
    message = (f'R96 differential formats: {totals["formats"]} dxf entry(ies) in {totals["books_with_table"]} '
               f'workbook(s), {totals["plain_books"]} workbook(s) with no table')
    if 'reader_formats' in totals:
        message += f'; openpyxl agreed on {totals["reader_formats"]} entry(ies) in {totals["reader_books"]} workbook(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
