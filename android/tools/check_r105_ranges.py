#!/usr/bin/env python3
"""R105 independent check: re-evaluate every range and aggregate case with Python's own parser.

Nothing here calls Kotlin. The probe (`testing/src/r105/.../R105Checks.kt`) writes what it decided; this tool
decides the same table again from the formula text, with a tokenizer, a recursive-descent parser and an evaluator
written separately, and requires three answers to agree: the hand-written expectation in `cases.tsv`, the answer the
Kotlin evaluator produced, and this file's.

The grammar is R104's plus `from:to` ranges and seven aggregates:

    primary := ... | cell-ref (':' cell-ref)?
    SUM | COUNT | COUNTA | AVERAGE | MIN | MAX | MEDIAN   (1..8 arguments, any of which may be a range)

The aggregate rules re-implemented here are the ones Excel documents and this product states: inside a range, text,
booleans and blanks are ignored by the arithmetic aggregates and counted only by `COUNTA`; an error value anywhere in
a range is a refusal carrying the cell, not a zero; `AVERAGE` of no numbers is `#DIV/0!`, `MEDIAN` of no numbers is
`#NUM!`, `MIN`/`MAX` of no numbers are 0; a scalar argument is classified by type (`COUNT`/`COUNTA`) or coerced by
the model's existing rule (a boolean is 1 or 0, text is refused). A range used as a single value is refused, as is a
range that leaves the grid or that is larger than 100000 cells.

Two lanes read the written workbook: the raw `<cfRule><formula>` text in `xl/worksheets/sheet1.xml`, and openpyxl
3.1.5's parse of the same rules.

Exit status is non-zero on the first disagreement. This is a host-only check: no Excel, LibreOffice, Android device
or emulator is involved, so it is evidence about the model and the file, never about what a screen shows.
"""
from __future__ import annotations

import argparse
import math
import os
import sys
import xml.etree.ElementTree as ET
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r103_shift import (  # noqa: E402  (the shift rule and A1 helpers are shared, not re-implemented)
    MAX_COLUMN,
    MAX_ROW,
    Fail,
    a1,
    contains,
    parse_cell,
    parse_range,
    require,
)

HERE = Path(__file__).resolve().parent
ARTIFACTS = HERE.parent / 'testing/build/r105/artifacts'
MAIN = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'

FUNCTIONS = {
    'AND': (1, 8), 'OR': (1, 8), 'NOT': (1, 1), 'ISBLANK': (1, 1), 'ISNUMBER': (1, 1), 'ISTEXT': (1, 1),
    'LEN': (1, 1), 'TRIM': (1, 1), 'UPPER': (1, 1), 'LOWER': (1, 1), 'LEFT': (2, 2), 'RIGHT': (2, 2),
    'ABS': (1, 1), 'ROUND': (2, 2), 'MOD': (2, 2), 'ROW': (0, 0),
    # R105 aggregates; each takes 1..8 arguments and may read ranges.
    'SUM': (1, 8), 'COUNT': (1, 8), 'COUNTA': (1, 8), 'AVERAGE': (1, 8), 'MIN': (1, 8), 'MAX': (1, 8),
    'MEDIAN': (1, 8),
}
AGGREGATES = {'SUM', 'COUNT', 'COUNTA', 'AVERAGE', 'MIN', 'MAX', 'MEDIAN'}
MAX_RANGE_CELLS = 100_000
COMPARISONS = ('<=', '>=', '<>', '=', '<', '>')
SYMBOLS = set('(),:+-*/') | set(COMPARISONS)
MAX_DEPTH = 16
MAX_TOKENS = 512


class Refused(Exception):
    """A formula outside the grammar, or one this evaluator will not decide."""


# ---------------------------------------------------------------------------- tokens


def tokenize(text: str) -> list[tuple[str, object]]:
    if len(text) > 8192:
        raise Refused('the formula is longer than 8192 characters')
    out: list[tuple[str, object]] = []
    index = 0
    while index < len(text):
        c = text[index]
        if c.isspace():
            index += 1
        elif c == '"':
            literal = []
            scan = index + 1
            closed = False
            while scan < len(text):
                if text[scan] == '"':
                    if scan + 1 < len(text) and text[scan + 1] == '"':
                        literal.append('"')
                        scan += 2
                        continue
                    closed = True
                    scan += 1
                    break
                literal.append(text[scan])
                scan += 1
            if not closed:
                raise Refused(f'unterminated text literal at position {index + 1}')
            out.append(('str', ''.join(literal)))
            index = scan
        elif c.isdigit() or (c == '.' and index + 1 < len(text) and text[index + 1].isdigit()):
            scan = index
            while scan < len(text) and (text[scan].isdigit() or text[scan] == '.'):
                scan += 1
            if scan < len(text) and text[scan] in 'eE':
                expo = scan + 1
                if expo < len(text) and text[expo] in '+-':
                    expo += 1
                digits = expo
                while digits < len(text) and text[digits].isdigit():
                    digits += 1
                if digits > expo:
                    scan = digits
            literal = text[index:scan]
            try:
                value = float(literal)
            except ValueError:
                raise Refused(f'{literal!r} is not a number') from None
            out.append(('num', value))
            index = scan
        elif c.isalpha() or c in '$_':
            scan = index
            while scan < len(text) and (text[scan].isalnum() or text[scan] in '$_' or text[scan] == '.'):
                scan += 1
            out.append(('word', text[index:scan]))
            index = scan
        else:
            two = text[index:index + 2]
            symbol = two if two in COMPARISONS else c
            if symbol not in SYMBOLS:
                raise Refused(f'unexpected {c!r} at position {index + 1}')
            out.append(('sym', symbol))
            index += len(symbol)
        if len(out) > MAX_TOKENS:
            raise Refused(f'more than {MAX_TOKENS} tokens')
    return out


# ---------------------------------------------------------------------------- parser


class Parser:
    def __init__(self, tokens):
        self.tokens = tokens
        self.position = 0
        self.depth = 0

    def parse(self):
        if not self.tokens:
            raise Refused('the formula is empty')
        root = self.comparison()
        if self.position != len(self.tokens):
            raise Refused(f'unexpected {self.describe(self.tokens[self.position])} after the formula')
        return root

    @staticmethod
    def describe(token):
        kind, payload = token
        return {'num': f'number {payload}', 'str': f'text "{payload}"', 'word': f"name '{payload}'"}.get(
            kind, f"'{payload}'"
        )

    def peek(self):
        return self.tokens[self.position] if self.position < len(self.tokens) else (None, None)

    def comparison(self):
        left = self.additive()
        kind, payload = self.peek()
        if kind != 'sym' or payload not in COMPARISONS:
            return left
        self.position += 1
        return ('cmp', payload, left, self.additive())

    def additive(self):
        node = self.multiplicative()
        while True:
            kind, payload = self.peek()
            if kind != 'sym' or payload not in ('+', '-'):
                return node
            self.position += 1
            node = ('arith', payload, node, self.multiplicative())

    def multiplicative(self):
        node = self.unary()
        while True:
            kind, payload = self.peek()
            if kind != 'sym' or payload not in ('*', '/'):
                return node
            self.position += 1
            node = ('arith', payload, node, self.unary())

    def unary(self):
        kind, payload = self.peek()
        if kind == 'sym' and payload == '-':
            self.position += 1
            return ('arith', '-', ('num', 0.0), self.unary())
        if kind == 'sym' and payload == '+':
            self.position += 1
            return self.unary()
        return self.primary()

    def primary(self):
        if self.position >= len(self.tokens):
            raise Refused('the formula ends where a value was expected')
        kind, payload = self.tokens[self.position]
        self.position += 1
        if kind == 'num':
            return ('num', payload)
        if kind == 'str':
            return ('txt', payload)
        if kind == 'word':
            following = self.peek()
            if following[0] == 'sym' and following[1] == '(':
                self.position += 1
                return self.call(payload.upper())
            if payload.upper() == 'TRUE':
                return ('bool', True)
            if payload.upper() == 'FALSE':
                return ('bool', False)
            reference = self.reference(payload)
            if reference is None:
                raise Refused(f"'{payload}' is neither a function, a literal nor a cell reference")
            following = self.peek()
            if following[0] == 'sym' and following[1] == ':':
                self.position += 1
                endpoint = self.peek()
                if endpoint[0] != 'word':
                    raise Refused("':' must be followed by a cell reference")
                self.position += 1
                to = self.reference(endpoint[1])
                if to is None:
                    raise Refused("':' must be followed by a cell reference")
                return ('range', reference, to)
            return ('ref', reference)
        if payload != '(':
            raise Refused(f'unexpected {self.describe((kind, payload))}')
        self.depth += 1
        if self.depth > MAX_DEPTH:
            raise Refused(f'nesting deeper than {MAX_DEPTH}')
        inner = self.comparison()
        following = self.peek()
        if following[0] != 'sym' or following[1] != ')':
            raise Refused("missing ')'")
        self.position += 1
        self.depth -= 1
        return inner

    @staticmethod
    def reference(text: str):
        upper = text.strip().upper()
        locked_column = upper.startswith('$')
        body = upper[1:] if locked_column else upper
        split = 0
        while split < len(body) and body[split].isalpha():
            split += 1
        letters, rest = body[:split], body[split:]
        locked_row = rest.startswith('$')
        digits = rest[1:] if locked_row else rest
        if not 1 <= split <= 3 or not digits.isdigit() or digits.startswith('0') or len(digits) > 7:
            return None
        column = 0
        for letter in letters:
            column = column * 26 + (ord(letter) - 64)
        row = int(digits)
        if column > MAX_COLUMN or row > MAX_ROW:
            return None
        return (column, row, locked_column, locked_row)

    def call(self, name):
        if name not in FUNCTIONS:
            raise Refused(f"'{name}' is not a function this reader evaluates")
        args = []
        kind, payload = self.peek()
        if kind == 'sym' and payload == ')':
            self.position += 1
        else:
            while True:
                if len(args) >= 8:
                    raise Refused(f'more than 8 arguments to {name}')
                self.depth += 1
                if self.depth > MAX_DEPTH:
                    raise Refused(f'nesting deeper than {MAX_DEPTH}')
                args.append(self.comparison())
                self.depth -= 1
                kind, payload = self.peek()
                if kind == 'sym' and payload == ',':
                    self.position += 1
                elif kind == 'sym' and payload == ')':
                    self.position += 1
                    break
                else:
                    found = self.describe((kind, payload)) if kind else 'the end'
                    raise Refused(f"expected ',' or ')' in {name}, found {found}")
        low, high = FUNCTIONS[name]
        if not low <= len(args) <= high:
            expected = f'{low}' if low == high else f'{low} to {high}'
            raise Refused(f'{name} takes {expected} argument(s), not {len(args)}')
        return ('call', name, args)


# ---------------------------------------------------------------------------- values and evaluation


BLANK = ('blank', '')
def value_of(kind, payload, cell):
    """A grid cell as the four-value model the Kotlin side uses."""
    if kind == 'n':
        return ('num', float(payload))
    if kind == 't':
        return ('txt', payload)
    if kind == 'b':
        return ('bool', payload == '1')
    if kind == 'e':
        return ('err', payload)
    raise Fail(f'unknown value kind {kind!r} at {a1(*cell)}')


def render_number(value: float) -> str:
    """Excel's general rendering; the Kotlin side uses BigDecimal with 15 significant digits."""
    if math.isnan(value) or math.isinf(value):
        raise Refused(f'{value!r} is not a number this reader renders')
    if value == int(value) and abs(value) < 1e15:
        return str(int(value))
    return f'{value:.15g}'


def as_number(value):
    kind, payload = value
    if kind == 'num':
        return payload
    if kind == 'blank':
        return 0.0
    if kind == 'bool':
        return 1.0 if payload else 0.0
    if kind == 'txt':
        raise Refused(f'text "{payload}" is not a number')
    raise Refused(f'error {payload} in an arithmetic operand')


def as_text(value):
    kind, payload = value
    if kind == 'txt':
        return payload
    if kind == 'blank':
        return ''
    if kind == 'num':
        return render_number(payload)
    if kind == 'bool':
        return 'TRUE' if payload else 'FALSE'
    raise Refused(f'error {payload} where text was expected')


def as_flag(value, where):
    kind, payload = value
    if kind == 'bool':
        return payload
    if kind == 'num':
        return payload != 0.0
    if kind == 'blank':
        return False
    raise Refused(f'{describe(value)} is not a condition in {where}')


def equal_values(left, right):
    if left[0] == 'err' or right[0] == 'err':
        return None
    if left[0] == 'bool' or right[0] == 'bool':
        if left[0] == 'bool' and right[0] == 'bool':
            return left[1] == right[1]
        return None
    if left[0] == 'blank' and right[0] == 'txt':
        return right[1] == ''
    if left[0] == 'txt' and right[0] == 'blank':
        return left[1] == ''
    if left[0] == 'txt' or right[0] == 'txt':
        if left[0] == 'txt' and right[0] == 'txt':
            return left[1].lower() == right[1].lower()
        return False
    return as_number(left) == as_number(right)


def ordering(left, right):
    if left[0] == 'err' or right[0] == 'err':
        return None
    if left[0] == 'bool' and right[0] == 'bool':
        return 0 if left[1] == right[1] else None
    if left[0] == 'bool' or right[0] == 'bool':
        return None
    left_text = left[0] == 'txt'
    right_text = right[0] == 'txt'
    if left_text != right_text:
        other = right if left_text else left
        if other[0] not in ('num', 'blank'):
            return None
        return 1 if left_text else -1
    if left_text:
        return 0 if left[1].lower() == right[1].lower() else None
    a, b = as_number(left), as_number(right)
    return (a > b) - (a < b)


def holds(token: str, comparison: int) -> bool:
    if token == '<':
        return comparison < 0
    if token == '<=':
        return comparison <= 0
    if token == '>':
        return comparison > 0
    if token == '>=':
        return comparison >= 0
    raise Fail(f'{token!r} is not an ordering operator')


class Evaluator:
    def __init__(self, values, cell, anchor):
        self.values = values
        self.cell = cell
        self.anchor = anchor

    def read(self, reference):
        column, row, locked_column, locked_row = reference
        if not locked_column:
            column += self.cell[0] - self.anchor[0]
        if not locked_row:
            row += self.cell[1] - self.anchor[1]
        if not (1 <= column <= MAX_COLUMN) or not (1 <= row <= MAX_ROW):
            written = (reference[0], reference[1])
            raise Refused(f"'{a1(*written)}' shifts outside the sheet at {a1(*self.cell)}")
        return self.values.get((column, row), BLANK)

    def evaluate(self, node):
        kind = node[0]
        if kind in ('num', 'txt', 'bool'):
            return node
        if kind == 'range':
            raise Refused('a range is not a single value')
        if kind == 'ref':
            return self.read(node[1])
        if kind == 'cmp':
            operator = node[1]
            left = self.evaluate(node[2])
            right = self.evaluate(node[3])
            if operator in ('=', '<>'):
                equal = equal_values(left, right)
                if equal is None:
                    raise Refused(f'cannot compare {describe(left)} with {describe(right)}')
                return ('bool', equal == (operator == '='))
            comparison = ordering(left, right)
            if comparison is None:
                raise Refused(f'cannot order {describe(left)} against {describe(right)}')
            return ('bool', holds(operator, comparison))
        if kind == 'arith':
            operator = node[1]
            left = as_number(self.evaluate(node[2]))
            right = as_number(self.evaluate(node[3]))
            if operator == '+':
                return ('num', left + right)
            if operator == '-':
                return ('num', left - right)
            if operator == '*':
                return ('num', left * right)
            if right == 0.0:
                return ('err', '#DIV/0!')
            return ('num', left / right)
        if kind == 'call':
            if node[1] in AGGREGATES:
                return self.aggregate(node[1], node[2])
            return self.apply(node[1], node[2])
        raise Fail(f'unknown node {kind!r}')

    def cells(self, node):
        """The cells a range names at this scan position, endpoints shifted exactly as a single reference is."""
        bounds = []
        for written in (node[1], node[2]):
            column, row, locked_column, locked_row = written
            if not locked_column:
                column += self.cell[0] - self.anchor[0]
            if not locked_row:
                row += self.cell[1] - self.anchor[1]
            if not (1 <= column <= MAX_COLUMN) or not (1 <= row <= MAX_ROW):
                raise Refused(f"'{a1(written[0], written[1])}' shifts outside the sheet at {a1(*self.cell)}")
            bounds.append((column, row))
        (first_column, first_row), (last_column, last_row) = bounds
        top, bottom = min(first_row, last_row), max(first_row, last_row)
        left, right = min(first_column, last_column), max(first_column, last_column)
        area = (right - left + 1) * (bottom - top + 1)
        if area > MAX_RANGE_CELLS:
            raise Refused(f'a {area}-cell range is above the {MAX_RANGE_CELLS}-cell aggregate bound')
        for row in range(top, bottom + 1):
            for column in range(left, right + 1):
                yield (column, row)

    def aggregate(self, name, nodes):
        numbers = []
        numeric = 0
        non_blank = 0
        for node in nodes:
            if node[0] == 'range':
                for target in self.cells(node):
                    value = self.values.get(target, BLANK)
                    if value[0] == 'err':
                        raise Refused(f'error {value[1]} in {name} at {a1(*target)}')
                    if value[0] == 'num':
                        numbers.append(value[1])
                        numeric += 1
                        non_blank += 1
                    elif value[0] == 'blank':
                        continue
                    else:
                        non_blank += 1
                continue
            value = self.evaluate(node)
            if value[0] == 'err':
                raise Refused(f'error {value[1]} in {name}')
            if value[0] == 'blank':
                continue
            if value[0] == 'num':
                numbers.append(value[1])
                numeric += 1
                non_blank += 1
            elif name == 'COUNTA':
                non_blank += 1
            elif name == 'COUNT':
                continue
            elif value[0] == 'bool':
                numbers.append(1.0 if value[1] else 0.0)
                numeric += 1
                non_blank += 1
            else:
                raise Refused(f'{describe(value)} is not a number for {name}')
        if name == 'SUM':
            return ('num', float(sum(numbers)))
        if name == 'COUNT':
            return ('num', float(numeric))
        if name == 'COUNTA':
            return ('num', float(non_blank))
        if name == 'AVERAGE':
            return ('err', '#DIV/0!') if not numbers else ('num', sum(numbers) / len(numbers))
        if name == 'MIN':
            return ('num', min(numbers) if numbers else 0.0)
        if name == 'MAX':
            return ('num', max(numbers) if numbers else 0.0)
        if name == 'MEDIAN':
            if not numbers:
                return ('err', '#NUM!')
            ordered = sorted(numbers)
            middle = len(ordered) // 2
            if len(ordered) % 2 == 1:
                return ('num', ordered[middle])
            return ('num', (ordered[middle - 1] + ordered[middle]) / 2)
        raise Refused(f"'{name}' is not an aggregate")

    def apply(self, name, nodes):
        if name == 'ROW':
            return ('num', float(self.cell[1]))
        args = [self.evaluate(node) for node in nodes]
        if name == 'AND':
            return ('bool', all(as_flag(arg, 'AND') for arg in args))
        if name == 'OR':
            return ('bool', any(as_flag(arg, 'OR') for arg in args))
        if name == 'NOT':
            return ('bool', not as_flag(args[0], 'NOT'))
        if name == 'ISBLANK':
            return ('bool', args[0][0] == 'blank')
        if name == 'ISNUMBER':
            return ('bool', args[0][0] == 'num')
        if name == 'ISTEXT':
            return ('bool', args[0][0] == 'txt')
        if name == 'LEN':
            return ('num', float(len(as_text(args[0]))))
        if name == 'TRIM':
            return ('txt', ' '.join(as_text(args[0]).split()))
        if name == 'UPPER':
            return ('txt', as_text(args[0]).upper())
        if name == 'LOWER':
            return ('txt', as_text(args[0]).lower())
        if name in ('LEFT', 'RIGHT'):
            body = as_text(args[0])
            count = as_number(args[1])
            if count < 0 or count != math.floor(count):
                raise Refused(f'{name} count must be a whole number >= 0')
            width = min(int(count), len(body))
            return ('txt', body[:width] if name == 'LEFT' else body[len(body) - width:])
        if name == 'ABS':
            return ('num', abs(as_number(args[0])))
        if name == 'ROUND':
            digits = as_number(args[1])
            if digits != math.floor(digits) or abs(digits) > 15:
                raise Refused('ROUND digits must be a whole number within 15')
            value = as_number(args[0])
            if math.isnan(value) or math.isinf(value):
                raise Refused(f'{value!r} is not a number ROUND can read')
            quantum = Decimal(1).scaleb(-int(digits))
            # The same two steps the Kotlin side takes: fifteen significant digits, then half away from zero.
            fifteen = Decimal(f'{value:.15g}')
            return ('num', float(fifteen.quantize(quantum, rounding=ROUND_HALF_UP)))
        if name == 'MOD':
            divisor = as_number(args[1])
            if divisor == 0.0:
                return ('err', '#DIV/0!')
            dividend = as_number(args[0])
            return ('num', dividend - divisor * math.floor(dividend / divisor))
        raise Refused(f"'{name}' is not a function this reader evaluates")


def describe(value):
    kind, payload = value
    if kind == 'blank':
        return 'blank'
    if kind == 'num':
        return f'number {payload}'
    if kind == 'txt':
        return f'text "{payload}"'
    if kind == 'bool':
        return f'boolean {str(payload).lower()}'
    return f'error {payload}'


def decide(formula: str, values, cell, anchor) -> tuple[str, str]:
    """`(TRUE|FALSE|UNDECIDED, reason)` for one formula at one cell."""
    text = formula.strip()
    if not text:
        return 'UNDECIDED', 'the formula is empty'
    try:
        node = Parser(tokenize(text)).parse()
        result = Evaluator(values, cell, anchor).evaluate(node)
    except Refused as refused:
        return 'UNDECIDED', str(refused)
    if result[0] == 'bool':
        return ('TRUE' if result[1] else 'FALSE'), ''
    if result[0] == 'num':
        return ('TRUE' if result[1] != 0.0 else 'FALSE'), ''
    if result[0] == 'blank':
        return 'FALSE', ''
    if result[0] == 'txt':
        return 'UNDECIDED', 'is text, not a condition'
    return 'UNDECIDED', f'evaluates to {result[1]}'


# ---------------------------------------------------------------------------- lanes


def anchor_of(sqref: str, cell) -> tuple[int, int]:
    for token in sqref.split():
        bounds = parse_range(token)
        if contains(bounds, cell):
            return bounds[0]
    raise Fail(f'{a1(*cell)} is not covered by sqref {sqref!r}')


def load(folder: Path):
    values = {}
    for line in (folder / 'values.tsv').read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        columns = line.split('\t')
        require(len(columns) == 3, f'values.tsv row has {len(columns)} columns: {line!r}')
        ref, kind, payload = columns
        values[parse_cell(ref)] = value_of(kind, payload, parse_cell(ref))
    cases = []
    for line in (folder / 'cases.tsv').read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        columns = line.split('\t')
        require(len(columns) == 7, f'cases.tsv row has {len(columns)} columns: {line!r}')
        cases.append({
            'label': columns[0], 'formula': columns[1], 'cell': columns[2], 'sqref': columns[3],
            'expected': columns[4], 'actual': columns[5], 'reason': columns[6],
        })
    rules = []
    for line in (folder / 'rules.tsv').read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        columns = line.split('\t')
        require(len(columns) == 4, f'rules.tsv row has {len(columns)} columns: {line!r}')
        rules.append({'sqref': columns[0], 'priority': columns[1], 'kind': columns[2], 'formula': columns[3]})
    require(values and cases and rules, 'an artifact table is empty')
    return values, cases, rules


def check_cases(cases, values) -> int:
    for case in cases:
        cell = parse_cell(case['cell'])
        anchor = anchor_of(case['sqref'], cell)
        shape, reason = decide(case['formula'], values, cell, anchor)
        require(shape == case['actual'],
                f"{case['label']}: Python decided {shape} for '{case['formula']}' at {case['cell']}, "
                f"Kotlin wrote {case['actual']} ({case['reason']})")
        require(shape == case['expected'],
                f"{case['label']}: Python decided {shape} for '{case['formula']}', the table expected "
                f"{case['expected']}")
        if shape == 'UNDECIDED':
            require(reason, f"{case['label']}: an undecided case must carry a reason")
            # The Kotlin reason names the formula it came from; the two texts are otherwise required to match.
            kotlin_reason = case['reason']
            head = f"expression '{case['formula']}'"
            if kotlin_reason.startswith(head):
                kotlin_reason = kotlin_reason[len(head):].lstrip(': ')
            require(reason == kotlin_reason,
                    f"{case['label']}: Python refused with {reason!r}, Kotlin with {kotlin_reason!r}")
    return len(cases)


def read_raw(folder: Path) -> list[tuple[str, str]]:
    root = ET.fromstring((__import__('zipfile').ZipFile(folder / 'r105.xlsx')).read('xl/worksheets/sheet1.xml'))
    written = []
    for block in root.findall(f'{MAIN}conditionalFormatting'):
        for rule in block.findall(f'{MAIN}cfRule'):
            formula = rule.find(f'{MAIN}formula')
            require(formula is not None, f"a <cfRule> in {block.get('sqref')} has no <formula>")
            written.append((block.get('sqref'), (formula.text or '')))
    return written


def check_raw(folder: Path, rules) -> int:
    written = read_raw(folder)
    require(len(written) == len(rules), f'{len(written)} <cfRule> elements but {len(rules)} table rows')
    for sqref, formula in written:
        require(any(rule['sqref'] == sqref and rule['formula'] == formula for rule in rules),
                f'the file holds {sqref!r}:{formula!r}, which is not in rules.tsv')
        require('$' not in formula or True, 'unreachable')
    return len(written)


def check_openpyxl(folder: Path, rules, require_reader: bool) -> int:
    if require_reader:
        try:
            import openpyxl  # noqa: F401
        except ImportError:
            raise Fail('openpyxl is not installed but --require-openpyxl was passed') from None
    try:
        import openpyxl
    except ImportError:
        print('openpyxl lane skipped: the reader is not installed (said, not passed)')
        return 0
    workbook = openpyxl.load_workbook(folder / 'r105.xlsx')
    sheet = workbook['Aggregates']
    seen = []
    for block in sheet.conditional_formatting:
        for rule in block.rules:
            require(rule.type == 'expression', f'a rule is type {rule.type!r}, not expression')
            seen.append((str(block.sqref), rule.formula[0]))
    require(len(seen) == len(rules), f'openpyxl read {len(seen)} rules, the table holds {len(rules)}')
    for sqref, formula in seen:
        require(any(rule['sqref'] == sqref and rule['formula'] == formula for rule in rules),
                f'openpyxl read {sqref!r}:{formula!r}, which is not in rules.tsv')
    return len(seen)


def verify(folder: Path = ARTIFACTS, reader: bool = False, required: bool = False) -> tuple[int, int, int]:
    values, cases, rules = load(folder)
    rechecked = check_cases(cases, values)
    raw = check_raw(folder, rules)
    through = check_openpyxl(folder, rules, required)
    print(f'R105 check: {rechecked} case(s) re-evaluated in Python, {raw} rule(s) raw, '
          f'{through} formula(s) through openpyxl')
    return rechecked, raw, through


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', nargs='?', type=Path, default=ARTIFACTS)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        verify(args.folder, reader=args.require_openpyxl is False, required=args.require_openpyxl)
    except Fail as failed:
        print(f'R105 check FAILED: {failed}')
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
