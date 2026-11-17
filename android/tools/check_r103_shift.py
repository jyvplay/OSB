#!/usr/bin/env python3
"""R103 independent checks: a conditional-format formula's references are relative to the cell being evaluated.

The probe (`R103Checks.kt`) writes ten rules whose formulas carry references — relative, `$`-locked per
coordinate, and one cross-sheet reference this grammar does not read — and records the verdicts it reached in
`verdicts.tsv`. This script reads the same workbook three ways:

1. **raw OPC** — every `<formula>` in `xl/worksheets/sheet1.xml` compared with `rules.tsv`, including the `$`
   markers, and `<cfRule>`/`<conditionalFormatting>` counts against the table.
2. **openpyxl 3.1.5** — an unrelated parser, asked only what formula text it reads back per range.
3. **a Python oracle** — an independent implementation of Excel's shift rule (anchor at the top-left of the range
   token, `$` locks a coordinate, a shift off the grid is undecidable, undecided wins only when nothing fired),
   re-deriving every row of `verdicts.tsv` from `rules.tsv` and `values.tsv`.

The third lane is the one that matters: it shares no code with the evaluator and re-implements the very rule this
round added, so a wrong anchor, a dropped `$` or a clamped shift would have to be reproduced twice to pass.

Usage: `python3 tools/check_r103_shift.py <artifacts> [--require-openpyxl]`. Exit status 0 means every lane agreed.
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

MAIN = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
MAX_ROW = 1048576
MAX_COLUMN = 16384

COLUMNS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'


class Fail(Exception):
    """A lane disagreed with the intent table."""


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def read_part(package: Path, name: str) -> bytes:
    with zipfile.ZipFile(package) as archive:
        return archive.read(name)


# ---------------------------------------------------------------------------- addresses


def column_index(letters: str) -> int:
    value = 0
    for character in letters:
        value = value * 26 + (ord(character) - ord('A') + 1)
    return value


def column_letters(index: int) -> str:
    out = ''
    while index > 0:
        index, remainder = divmod(index - 1, 26)
        out = COLUMNS[remainder] + out
    return out


def parse_cell(text: str) -> tuple[int, int]:
    match = re.fullmatch(r'([A-Z]{1,3})([1-9][0-9]{0,6})', text.strip().upper())
    require(match is not None, f'{text!r} is not a cell address')
    return column_index(match.group(1)), int(match.group(2))


def a1(column: int, row: int) -> str:
    return f'{column_letters(column)}{row}'


def parse_range(token: str) -> tuple[tuple[int, int], tuple[int, int]]:
    if ':' in token:
        left, right = token.split(':', 1)
        first, second = parse_cell(left), parse_cell(right)
        return ((min(first[0], second[0]), min(first[1], second[1])),
                (max(first[0], second[0]), max(first[1], second[1])))
    single = parse_cell(token)
    return single, single


def contains(bounds, cell) -> bool:
    (left, top), (right, bottom) = bounds
    return left <= cell[0] <= right and top <= cell[1] <= bottom


# ---------------------------------------------------------------------------- the oracle

REFERENCE = re.compile(r'^(\$?)([A-Z]{1,3})(\$?)([1-9][0-9]{0,6})$')
NUMBER = re.compile(r'^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$')
COMPARISON = re.compile(r'^(.+?)\s*(<=|>=|<>|=|<|>)\s*(.+)$')
IS_BLANK = re.compile(r'^ISBLANK\(\s*(\$?[A-Z]{1,3}\$?[1-9][0-9]{0,6})\s*\)$', re.IGNORECASE)

# `cellIs` states its operator as an `ST_CfOperator` token rather than as a symbol.
OPERATORS = {
    'lessThan': '<',
    'lessThanOrEqual': '<=',
    'equal': '=',
    'notEqual': '<>',
    'greaterThanOrEqual': '>=',
    'greaterThan': '>',
}

UNREADABLE = object()  # a reference that shifts off the grid


class Oracle:
    """Excel's conditional-format reference rule, re-implemented; `None` means "undecided"."""

    def __init__(self, values: dict[tuple[int, int], tuple[str, str]]):
        self.values = values

    def value(self, cell):
        if cell not in self.values:
            return ('blank', '')
        return self.values[cell]

    def shift(self, reference: str, cell, anchor):
        match = REFERENCE.match(reference.strip().upper())
        require(match is not None, f'{reference!r} is not a reference token')
        column, row = parse_cell(match.group(2) + match.group(4))
        if not match.group(1):
            column += cell[0] - anchor[0]
        if not match.group(3):
            row += cell[1] - anchor[1]
        if not (1 <= column <= MAX_COLUMN) or not (1 <= row <= MAX_ROW):
            return UNREADABLE
        return (column, row)

    def operand(self, token: str, cell, anchor):
        text = token.strip()
        if NUMBER.match(text):
            return ('n', float(text))
        if len(text) >= 2 and text.startswith('"') and text.endswith('"'):
            return ('t', text[1:-1].replace('""', '"'))
        if text.upper() == 'TRUE':
            return ('b', True)
        if text.upper() == 'FALSE':
            return ('b', False)
        if REFERENCE.match(text.upper()):
            target = self.shift(text, cell, anchor)
            if target is UNREADABLE:
                return None
            kind, payload = self.value(target)
            if kind == 'n':
                return ('n', float(payload))
            if kind == 't':
                return ('t', payload)
            if kind == 'b':
                return ('b', payload == '1')
            return ('blank', '')
        return None

    @staticmethod
    def compare(left, right):
        """-1/0/1, or None when the pair has no defined order here (only numbers and blanks are exercised)."""
        def numeric(operand):
            # `values.tsv` carries numbers as text, so both a grid value and an operand are coerced here rather
            # than at the call sites — the oracle must not compare a string with a float.
            if operand[0] == 'n':
                return float(operand[1])
            if operand[0] == 'blank':
                return 0.0
            return None
        a, b = numeric(left), numeric(right)
        if a is None or b is None:
            return None
        return (a > b) - (a < b)

    @staticmethod
    def holds(order: int, operator: str) -> bool:
        return {
            '<': order < 0,
            '<=': order <= 0,
            '>': order > 0,
            '>=': order >= 0,
            '=': order == 0,
            '<>': order != 0,
        }[operator]

    def evaluate(self, rule, cell, anchor):
        """True/False, or None for undecided."""
        formulas = [part for part in rule['formulas'].split(',') if part]
        if rule['kind'] == 'expression':
            if len(formulas) != 1:
                return None
            text = formulas[0].strip()
            match = IS_BLANK.match(text)
            if match:
                target = self.shift(match.group(1), cell, anchor)
                if target is UNREADABLE:
                    return None
                return self.value(target)[0] == 'blank'
            match = COMPARISON.match(text)
            if not match:
                return None
            left = self.operand(match.group(1), cell, anchor)
            right = self.operand(match.group(3), cell, anchor)
            if left is None or right is None:
                return None
            order = self.compare(left, right)
            if order is None:
                return None
            return self.holds(order, match.group(2))
        if rule['kind'] == 'cellIs':
            if len(formulas) != 1:
                return None
            right = self.operand(formulas[0], cell, anchor)
            if right is None:
                return None
            order = self.compare(self.value(cell), right)
            if order is None:
                return None
            symbol = OPERATORS.get(rule['operator'])
            if symbol is None:
                return None
            return self.holds(order, symbol)
        return None

    def verdict(self, rules, cell) -> str:
        fired, undecided = [], False
        for rule in sorted(rules, key=lambda item: int(item['priority'])):
            tokens = [token for token in rule['sqref'].split() if token]
            anchor = None
            for token in tokens:
                bounds = parse_range(token)
                if contains(bounds, cell):
                    anchor = bounds[0]
                    break
            if anchor is None:
                continue
            outcome = self.evaluate(rule, cell, anchor)
            if outcome is None:
                undecided = True
            elif outcome:
                fired.append(rule['priority'])
        if not fired and undecided:
            return 'UNDECIDED'
        return ','.join(fired) if fired else '-'


# ---------------------------------------------------------------------------- lanes


def load(folder: Path):
    rules = []
    for line in (folder / 'rules.tsv').read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        columns = line.split('\t')
        require(len(columns) == 5, f'rules.tsv row has {len(columns)} columns, expected 5: {line!r}')
        sqref, priority, kind, operator, formulas = columns
        rules.append({'sqref': sqref, 'priority': priority, 'kind': kind,
                      'operator': operator, 'formulas': formulas})
    values = {}
    for line in (folder / 'values.tsv').read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        columns = line.split('\t')
        require(len(columns) == 3, f'values.tsv row has {len(columns)} columns, expected 3: {line!r}')
        ref, kind, payload = columns
        values[parse_cell(ref)] = (kind, payload)
    verdicts = []
    for line in (folder / 'verdicts.tsv').read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        columns = line.split('\t')
        require(len(columns) == 2, f'verdicts.tsv row has {len(columns)} columns, expected 2: {line!r}')
        verdicts.append((columns[0], columns[1]))
    require(rules and values and verdicts, 'an intent table is empty')
    return rules, values, verdicts


def check_raw(folder: Path, rules) -> int:
    sheet = ET.fromstring(read_part(folder / 'r103.xlsx', 'xl/worksheets/sheet1.xml'))
    blocks = sheet.findall(f'{MAIN}conditionalFormatting')
    written = [rule for block in blocks for rule in block.findall(f'{MAIN}cfRule')]
    require(len(written) == len(rules),
            f'{len(written)} <cfRule> elements but {len(rules)} table rows')
    for block in blocks:
        sqref = block.get('sqref')
        expected = {rule['sqref'] for rule in rules}
        require(sqref in expected, f'block sqref {sqref!r} is not in the table')
    # Every formula must appear verbatim, `$` markers included, in its own rule.
    for rule, node in zip(rules, written):
        formulas = [child.text or '' for child in node.findall(f'{MAIN}formula')]
        require(','.join(formulas) == rule['formulas'],
                f"rule {rule['priority']} wrote {formulas}, table says {rule['formulas']}")
    text = read_part(folder / 'r103.xlsx', 'xl/worksheets/sheet1.xml').decode('utf-8')
    for marker in ('$C$1', '$D1', 'D$1', 'ISBLANK($I$1)'):
        require(marker in text, f'the written sheet lost the {marker} marker')
    return len(written)


def check_openpyxl(folder: Path, rules, require_reader: bool) -> int:
    try:
        from openpyxl import load_workbook  # noqa: PLC0415 - optional lane
    except ImportError:
        if require_reader:
            raise Fail('openpyxl is required for this run but is not importable') from None
        print('R103 check: openpyxl lane skipped (not importable)')
        return 0
    book = load_workbook(folder / 'r103.xlsx')
    sheet = book['Shift']
    seen = []
    for group in sheet.conditional_formatting:
        for rule in group.rules:
            seen.append(','.join(rule.formula or []))
    expected = [rule['formulas'] for rule in rules]
    require(seen == expected, f'openpyxl read formulas {seen}, table says {expected}')
    return len(seen)


def check_oracle(folder: Path, rules, values, verdicts) -> int:
    oracle = Oracle(values)
    for ref, encoded in verdicts:
        cell = parse_cell(ref)
        got = oracle.verdict(rules, cell)
        require(got == encoded, f'{ref}: oracle says {got}, the probe recorded {encoded}')
    return len(verdicts)


def verify(folder: Path, reader: bool = False, required: bool = False) -> tuple[int, int, int]:
    """Every lane, in order. `(rules, formulas through openpyxl, verdicts recomputed)`."""
    rules, values, verdicts = load(folder)
    written = check_raw(folder, rules)
    recomputed = check_oracle(folder, rules, values, verdicts)
    through = check_openpyxl(folder, rules, required) if reader else 0
    return written, through, recomputed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true',
                        help='fail instead of skipping when openpyxl is not importable')
    args = parser.parse_args(argv)
    written, through, recomputed = verify(args.artifacts, reader=True, required=args.require_openpyxl)
    print(f'R103 check: {written} rule(s) raw, {through} formula(s) through openpyxl, '
          f'{recomputed} verdict(s) recomputed from the shift rule')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Fail as failure:
        raise SystemExit(f'R103 check FAILED: {failure}')
