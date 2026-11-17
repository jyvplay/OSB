#!/usr/bin/env python3
"""R102 independent checks: the `<dxf>` differential format, re-read by three mechanisms that share no code.

The probe (`R102Checks.kt`) writes six formats and records what it *meant* in `formats.tsv`, the evaluator's
field-by-field merge in `merge.tsv` / `detail.tsv`. This script reads the workbook back three ways and compares:

1. **raw OPC** — `xl/styles.xml` parsed with `xml.etree`, re-encoded in the same grammar as the table. This catches
   a writer that drops, reorders or defaults a section, because it reconstructs every field *from the bytes*.
2. **openpyxl 3.1.5** — an unrelated parser with its own dxf model (`wb._differential_styles`). It shares no code
   with the raw lane and no code with the probe, so a bug would have to be reproduced twice to pass both.
3. **a Python re-derivation of the merge** — field-wise first-wins over `merge.tsv`, compared to `detail.tsv`. This
   re-implements the evaluator's rule rather than calling it.

A fourth lane states the foreign-package facts the probe relies on (a gradient fill, `textRotation="255"`, an
unknown border style and an id-only `numFmt` are all present in `r102-foreign.xlsx`), so a probe that silently
stopped producing them would fail here instead of passing vacuously.

Usage: `python3 tools/check_r102_dxf.py <artifacts> [--require-openpyxl]`. Exit status 0 means every lane agreed.
"""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

MAIN = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = 14


class Fail(Exception):
    """A lane disagreed with the intent table."""


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def read_part(package: Path, name: str) -> bytes:
    with zipfile.ZipFile(package) as archive:
        return archive.read(name)


def strip_alpha(rgb: str | None) -> str | None:
    """`FFRRGGBB` -> `RRGGBB`; anything that is not 6 or 8 hex digits is not a colour this model holds."""
    if not rgb:
        return None
    value = rgb.strip().upper()
    if len(value) == 8:
        value = value[2:]
    return value if len(value) == 6 and all(c in '0123456789ABCDEF' for c in value) else None


def flag(element: ET.Element | None, name: str) -> str:
    """A decoration flag: present with a false `val` is `0`, present without one is `1`, absent is `0`."""
    if element is None:
        return '0'
    child = element.find(f'{MAIN}{name}')
    if child is None:
        return '0'
    raw = child.get('val')
    return '0' if raw is not None and raw.strip().lower() in ('0', 'false', 'off') else '1'


def underline(element: ET.Element | None) -> str:
    if element is None:
        return '0'
    child = element.find(f'{MAIN}u')
    if child is None:
        return '0'
    raw = child.get('val')
    if raw is None:
        return '1'
    raw = raw.strip().lower()
    return '0' if raw in ('none', '0', 'false', 'off') else '1'


def number_text(value: float) -> str:
    """Kotlin's `Double.toString()` for the values this probe writes: `14.0`, `11.5`."""
    text = repr(float(value))
    return text


def border_text(border: ET.Element | None) -> str:
    if border is None:
        return '-'
    sides = []
    for side in ('left', 'right', 'top', 'bottom', 'diagonal'):
        edge = border.find(f'{MAIN}{side}')
        if edge is None:
            continue
        style = edge.get('style')
        colour = strip_alpha(edge.find(f'{MAIN}color').get('rgb') if edge.find(f'{MAIN}color') is not None else None)
        sides.append(f'{side}:{style}:{colour or "-"}')
    if not sides:
        return '-'
    up = 'up' if (border.get('diagonalUp') or '') in ('1', 'true') else '-'
    down = 'down' if (border.get('diagonalDown') or '') in ('1', 'true') else '-'
    return ','.join(sides) + '/' + up + '/' + down


def alignment_text(alignment: ET.Element | None) -> str:
    if alignment is None:
        return '-'
    def on_off(name: str) -> str:
        raw = alignment.get(name)
        if raw is None:
            return '-'
        return '1' if raw.strip().lower() in ('1', 'true', 'on') else '0'
    return ';'.join([
        alignment.get('horizontal') or '-',
        alignment.get('vertical') or '-',
        alignment.get('textRotation') or '-',
        on_off('wrapText'),
        alignment.get('indent') or '-',
        on_off('shrinkToFit'),
        alignment.get('readingOrder') or '-',
    ])


def protection_text(protection: ET.Element | None) -> str:
    if protection is None:
        return '-'
    def one(name: str) -> str:
        raw = protection.get(name)
        if raw is None:
            return '-'
        return '1' if raw.strip().lower() in ('1', 'true', 'on') else '0'
    return f'{one("locked")};{one("hidden")}'


def dxf_description(dxf: ET.Element) -> str:
    """The 14-field grammar `formats.tsv` uses, rebuilt from the raw element."""
    font = dxf.find(f'{MAIN}font')
    numfmt = dxf.find(f'{MAIN}numFmt')
    fill = dxf.find(f'{MAIN}fill')
    pattern = fill.find(f'{MAIN}patternFill') if fill is not None else None
    pattern_type = pattern.get('patternType') if pattern is not None else None
    fg = strip_alpha(pattern.find(f'{MAIN}fgColor').get('rgb')
                     if pattern is not None and pattern.find(f'{MAIN}fgColor') is not None else None)
    bg = strip_alpha(pattern.find(f'{MAIN}bgColor').get('rgb')
                     if pattern is not None and pattern.find(f'{MAIN}bgColor') is not None else None)
    hatched = pattern_type is not None and pattern_type != 'solid'
    font_rgb = None
    font_name = None
    font_size = None
    if font is not None:
        font_rgb = strip_alpha(font.find(f'{MAIN}color').get('rgb')
                               if font.find(f'{MAIN}color') is not None else None)
        name = font.find(f'{MAIN}name')
        font_name = name.get('val') if name is not None else None
        size = font.find(f'{MAIN}sz')
        font_size = number_text(float(size.get('val'))) if size is not None else None
    return '|'.join([
        flag(font, 'b'),
        flag(font, 'i'),
        flag(font, 'strike'),
        underline(font),
        font_rgb or '-',
        font_name or '-',
        font_size or '-',
        (fg if hatched else (fg or bg)) or '-',
        pattern_type if hatched else '-',
        (bg if hatched else None) or '-',
        numfmt.get('formatCode') if numfmt is not None else '-',
        border_text(dxf.find(f'{MAIN}border')),
        alignment_text(dxf.find(f'{MAIN}alignment')),
        protection_text(dxf.find(f'{MAIN}protection')),
    ])


def check_raw(folder: Path, rows: list[list[str]]) -> int:
    styles = read_part(folder / 'r102.xlsx', 'xl/styles.xml')
    root = ET.fromstring(styles)
    dxfs = root.find(f'{MAIN}dxfs')
    require(dxfs is not None, 'styles.xml has no <dxfs>')
    entries = dxfs.findall(f'{MAIN}dxf')
    require(len(entries) == len(rows),
            f'{len(entries)} <dxf> entries but {len(rows)} table rows')
    for index, (entry, row) in enumerate(zip(entries, rows)):
        got = dxf_description(entry)
        require(got == row[4], f'raw lane, dxf {index}: {got} != {row[4]}')
        # CT_Dxf is a sequence: the sections must appear in schema order, and only those present may be present.
        order = ['font', 'numFmt', 'fill', 'alignment', 'border', 'protection']
        present = [name for name in order if entry.find(f'{MAIN}{name}') is not None]
        positions = [list(entry).index(entry.find(f'{MAIN}{name}')) for name in present]
        require(positions == sorted(positions), f'dxf {index} is out of CT_Dxf order: {present}')
    # Every id a rule points at must exist, and every dxf must be pointed at: an orphan is an unverified format.
    sheet = ET.fromstring(read_part(folder / 'r102.xlsx', 'xl/worksheets/sheet1.xml'))
    ids = sorted(int(rule.get('dxfId')) for block in sheet.iter(f'{MAIN}conditionalFormatting')
                 for rule in block.findall(f'{MAIN}cfRule') if rule.get('dxfId') is not None)
    require(ids == list(range(len(entries))), f'rules point at dxfIds {ids}, dxfs are 0..{len(entries) - 1}')
    # `numFmtId` values used inside dxfs must resolve in <numFmts>, or a reader that follows ids sees General.
    declared = {int(nf.get('numFmtId')): nf.get('formatCode')
                for nf in (root.find(f'{MAIN}numFmts') or []).findall(f'{MAIN}numFmt')}
    for entry in entries:
        numfmt = entry.find(f'{MAIN}numFmt')
        if numfmt is None:
            continue
        identifier = int(numfmt.get('numFmtId'))
        require(declared.get(identifier) == numfmt.get('formatCode'),
                f'dxf numFmt {identifier} is not declared in <numFmts>: {declared}')
    return len(entries)


def check_openpyxl(folder: Path, rows: list[list[str]]) -> int:
    from openpyxl import load_workbook  # noqa: PLC0415 - optional lane

    book = load_workbook(folder / 'r102.xlsx')
    styles = book._differential_styles.styles
    require(len(styles) == len(rows), f'openpyxl read {len(styles)} dxfs, table has {len(rows)}')
    for index, (style, row) in enumerate(zip(styles, rows)):
        def onoff(value) -> str:
            return '1' if value else '0'

        font = style.font
        fields = [
            onoff(font.b) if font is not None else '0',
            onoff(font.i) if font is not None else '0',
            onoff(font.strike) if font is not None else '0',
            onoff(font.u) if font is not None else '0',
            (strip_alpha(font.color.rgb) if font is not None and font.color is not None else None) or '-',
            (font.name if font is not None else None) or '-',
            number_text(font.sz) if font is not None and font.sz is not None else '-',
        ]
        fill = style.fill
        if fill is not None and getattr(fill, 'patternType', None) not in (None, 'solid'):
            fields += [
                (strip_alpha(fill.fgColor.rgb) if fill.fgColor is not None else None) or '-',
                fill.patternType,
                (strip_alpha(fill.bgColor.rgb) if fill.bgColor is not None else None) or '-',
            ]
        else:
            colour = None
            if fill is not None:
                colour = strip_alpha(fill.bgColor.rgb if fill.bgColor is not None else None) or \
                    strip_alpha(fill.fgColor.rgb if fill.fgColor is not None else None)
            fields += [colour or '-', '-', '-']
        fields.append(style.numFmt.formatCode if style.numFmt is not None else '-')
        border = style.border
        if border is None:
            fields.append('-')
        else:
            sides = []
            for side_name in ('left', 'right', 'top', 'bottom', 'diagonal'):
                side = getattr(border, side_name)
                if side is None or side.style is None:
                    continue
                colour = strip_alpha(side.color.rgb if side.color is not None else None)
                sides.append(f'{side_name}:{side.style}:{colour or "-"}')
            if sides:
                up = 'up' if border.diagonalUp else '-'
                down = 'down' if border.diagonalDown else '-'
                fields.append(','.join(sides) + '/' + up + '/' + down)
            else:
                fields.append('-')
        alignment = style.alignment
        if alignment is None:
            fields.append('-')
        else:
            def three(value) -> str:
                return '-' if value is None else ('1' if value else '0')
            fields.append(';'.join([
                alignment.horizontal or '-',
                alignment.vertical or '-',
                '-' if alignment.textRotation is None else str(int(alignment.textRotation)),
                three(alignment.wrapText),
                '-' if alignment.indent is None else str(int(alignment.indent)),
                three(alignment.shrinkToFit),
                '-' if alignment.readingOrder is None else str(int(alignment.readingOrder)),
            ]))
        protection = style.protection
        if protection is None:
            fields.append('-')
        else:
            def one(value) -> str:
                return '-' if value is None else ('1' if value else '0')
            fields.append(f'{one(protection.locked)};{one(protection.hidden)}')
        got = '|'.join(fields)
        compare_with_defaults(index, row[4], got)
    return len(styles)


# openpyxl's `Alignment` and `Protection` objects fill absent attributes with the schema default, so an attribute
# this writer did not emit (`-`) and an explicit default (`0`/`false`) are the *same object* after a read. Absence
# is therefore verified in the raw lane, which sees the attribute, and accepted here when openpyxl reports the
# default. Every other field must match exactly.
DEFAULTS_FILLED = {12: {2, 3, 4, 5, 6}, 13: {0, 1}}


def compare_with_defaults(index: int, expected: str, got: str) -> None:
    if expected == got:
        return
    expected_fields = expected.split('|')
    got_fields = got.split('|')
    require(len(expected_fields) == len(got_fields),
            f'openpyxl lane, dxf {index}: {got} != {expected}')
    for position, (want, have) in enumerate(zip(expected_fields, got_fields)):
        if want == have:
            continue
        fillable = DEFAULTS_FILLED.get(position, set())
        if fillable:
            wanted = want.split(';')
            had = have.split(';')
            if len(wanted) == len(had) and all(
                a == b or (slot in fillable and a == '-' and b in ('0', 'false'))
                for slot, (a, b) in enumerate(zip(wanted, had))
            ):
                continue
        require(False, f'openpyxl lane, dxf {index}, field {position}: {have} != {want}')


def split_detail(encoded: str) -> list[str]:
    require(encoded.count('|') == 7, f'a detail row must have eight fields: {encoded}')
    return encoded.split('|')


def merge_details(rows: list[list[str]]) -> str:
    """Field-wise first-wins: a rule that fired earlier keeps every field it set."""
    merged = ['-'] * 8
    for _, encoded in rows:
        for index, value in enumerate(split_detail(encoded)):
            if merged[index] == '-' and value != '-':
                merged[index] = value
    return '|'.join(merged)


def check_merge(folder: Path) -> int:
    merge_rows = [line.split('\t') for line in
                  (folder / 'merge.tsv').read_text(encoding='utf-8').splitlines() if line.strip()]
    require(len(merge_rows) == 3, f'merge.tsv names {len(merge_rows)} rules, expected 3')
    expected = (folder / 'detail.tsv').read_text(encoding='utf-8').splitlines()
    merged_row = next(line for line in expected if line.split('\t')[1] == 'merged')
    _, _, encoded = merged_row.split('\t')
    recomputed = merge_details([(row[0], row[1]) for row in merge_rows])
    require(recomputed == encoded, f'merge recomputed as {recomputed}, the probe recorded {encoded}')
    plain_row = next(line for line in expected if line.split('\t')[1] == 'plain')
    require(plain_row.split('\t')[2] == '-', 'a decoration-only dxf must record no detail at all')
    return len(merge_rows)


def check_foreign(folder: Path) -> int:
    styles = read_part(folder / 'r102-foreign.xlsx', 'xl/styles.xml')
    root = ET.fromstring(styles)
    dxfs = root.find(f'{MAIN}dxfs').findall(f'{MAIN}dxf')
    require(len(dxfs) == 5, f'the foreign package carries {len(dxfs)} dxfs, expected 5')
    require(dxfs[2].find(f'{MAIN}fill').find(f'{MAIN}gradientFill') is not None,
            'the foreign package lost its gradient fill, so its refusal proves nothing')
    require(dxfs[3].find(f'{MAIN}alignment').get('textRotation') == '255',
            'the foreign package lost its vertical-text alignment')
    left = dxfs[4].find(f'{MAIN}border').find(f'{MAIN}left')
    require(left.get('style') == 'notAStyle', 'the foreign package lost its unknown border style')
    numfmt = dxfs[1].find(f'{MAIN}numFmt')
    require(numfmt.get('formatCode') is None and numfmt.get('numFmtId') == '164',
            'the foreign package is supposed to carry an id-only numFmt')
    return len(dxfs)


def verify(folder: Path, reader: bool = False, required: bool = False) -> tuple[int, int, int, int]:
    """Every lane, in order. `(dxfs, dxfs through openpyxl, merged details, foreign dxfs)`."""
    rows = [line.split('\t') for line in (folder / 'formats.tsv').read_text(encoding='utf-8').splitlines()
            if line.strip()]
    require(rows and all(len(row) == 5 for row in rows), 'formats.tsv must have five columns per row')
    formats = check_raw(folder, rows)
    merged = check_merge(folder)
    foreign = check_foreign(folder)
    through = 0
    if reader:
        try:
            through = check_openpyxl(folder, rows)
        except ImportError:
            if required:
                raise Fail('openpyxl is required for this run but is not importable') from None
            print('R102 check: openpyxl lane skipped (not importable)')
    return formats, through, merged, foreign


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true',
                        help='fail instead of skipping when openpyxl is not importable')
    args = parser.parse_args(argv)
    formats, through, merged, foreign = verify(args.artifacts, reader=True, required=args.require_openpyxl)
    print(f'R102 check: {formats} dxf(s) raw, {through} through openpyxl, '
          f'{merged} merged detail(s) recomputed, {foreign} foreign dxf(s)')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Fail as failure:
        raise SystemExit(f'R102 check FAILED: {failure}')
