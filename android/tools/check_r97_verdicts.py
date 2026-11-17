#!/usr/bin/env python3
"""Independent verdict check for R97 conditional formatting.

This is a second implementation of the decision procedure, written from the contract in
`XlsxConditionalFormatVerdict` and *not* calling into the Kotlin: it reads `rules.tsv` (the blocks and rules the
probe built) and `values.tsv` (the cell values), recomputes every verdict, and compares the result with
`verdicts.tsv` field by field. Agreement means two implementations agree; it does **not** mean Excel agrees,
because no spreadsheet engine ran here.

The recomputation covers, in the order the model applies them:

* which blocks cover a cell, and the number of applicable blocks;
* rule order by priority and the `stopIfTrue` cut;
* `cellIs` with one or two operands (numeric, quoted text, boolean or single-cell reference), inclusive
  `between`, and blank/numeric coercion;
* `expression` restricted to `ISBLANK(ref)`, `MOD(ROW(),k)=r` and `operand <op> operand`;
* `top10` / `top10 percent` with tie extension over the numeric values of the rule's own blocks;
* `colorScale` / `dataBar` threshold resolution (`min`, `max`, `num`, `percent`, `percentile`), interpolation and
  clamping;
* the merged decoration, where the first non-null property in priority order wins.

Anything the contract refuses is reproduced as *unresolved*, never as "did not fire".

A third layer, when `--require-openpyxl` is passed, reopens `r97.xlsx` with openpyxl and checks that the package
carries the blocks and priorities `rules.tsv` claims: the verdicts must be verdicts *about this file*.

Utility: `python3 tools/check_r97_verdicts.py testing/build/r97/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import math
import re
import sys
import warnings
import zipfile
from pathlib import Path

RULE_FIELDS = 17
VALUE_FIELDS = 4
VERDICT_FIELDS = 18
NUMBER = re.compile(r'^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$')
CELL_REF = re.compile(r'^[A-Z]{1,3}[1-9][0-9]{0,6}$')
COMPARISON = re.compile(r'^(.+?)\s*(<=|>=|<>|=|<|>)\s*(.+)$')
IS_BLANK = re.compile(r'^ISBLANK\(\s*([A-Z]{1,3}[1-9][0-9]{0,6})\s*\)$', re.IGNORECASE)
MOD_ROW = re.compile(r'^MOD\(\s*ROW\(\)\s*,\s*(\d{1,4})\s*\)\s*=\s*(\d{1,4})$', re.IGNORECASE)
SEPARATOR = '\x1f'
MAX_RANGE_CELLS = 100_000


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def table(path: Path, fields: int) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows = []
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'{path.name} line {number}: {len(parts)} fields, expected {fields}')
        rows.append(parts)
    require(bool(rows), f'{path.name} is empty')
    return rows


# --- cell references --------------------------------------------------------------------------------------


def parse_cell(ref: str) -> tuple[int, int]:
    match = re.fullmatch(r'([A-Z]{1,3})([1-9][0-9]{0,6})', ref)
    require(match is not None, f'{ref!r} is not an A1 reference')
    letters, row = match.group(1), int(match.group(2))
    column = 0
    for character in letters:
        column = column * 26 + (ord(character) - ord('A') + 1)
    return column, row


def parse_range(token: str) -> tuple[int, int, int, int]:
    if ':' not in token:
        column, row = parse_cell(token)
        return column, row, column, row
    left, right = token.split(':')
    c1, r1 = parse_cell(left)
    c2, r2 = parse_cell(right)
    return min(c1, c2), min(r1, r2), max(c1, c2), max(r1, r2)


def covers(token: str, cell: tuple[int, int]) -> bool:
    c1, r1, c2, r2 = parse_range(token)
    return c1 <= cell[0] <= c2 and r1 <= cell[1] <= r2


def to_a1(cell: tuple[int, int]) -> str:
    column, row = cell
    letters = ''
    while column:
        column, remainder = divmod(column - 1, 26)
        letters = chr(ord('A') + remainder) + letters
    return f'{letters}{row}'


# --- the rule model ----------------------------------------------------------------------------------------


class Rule:
    def __init__(self, sheet: str, block: int, sqref: str, kind: str, priority: int, row: list[str]):
        self.sheet = sheet
        self.block = block
        self.sqref = sqref
        self.kind = kind
        self.priority = priority
        self.stop = row[7]
        self.operator = None if row[8] == '-' else row[8]
        self.rank = None if row[9] == '-' else int(row[9])
        self.percent = None if row[10] == '-' else row[10] == '1'
        self.bottom = None if row[11] == '-' else row[11] == '1'
        self.formulas = [] if row[12] == '-' else row[12].split(SEPARATOR)
        self.thresholds = [] if row[13] == '-' else [
            (item.split(':')[0], None if item.split(':')[1] == '-' else float(item.split(':')[1]))
            for item in row[13].split(',')
        ]
        self.colors = [] if row[14] == '-' else row[14].split(',')
        self.format = None if row[16] == '-' else row[16].split(';')

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return f'<Rule {self.kind} p{self.priority} {self.sqref}>'


def rules_by_sheet(path: Path) -> dict[str, dict[int, tuple[str, list[Rule]]]]:
    blocks: dict[str, dict[int, tuple[str, list[Rule]]]] = {}
    for row in table(path, RULE_FIELDS):
        sheet, block_index = row[0], int(row[2])
        block = blocks.setdefault(sheet, {}).setdefault(block_index, (row[3], []))
        require(block[0] == row[3], f'{sheet}: block {block_index} has two sqref spellings')
        block[1].append(Rule(sheet, block_index, row[3], row[5], int(row[6]), row))
    return blocks


def values_by_sheet(path: Path) -> dict[str, dict[tuple[int, int], tuple[str, str]]]:
    values: dict[str, dict[tuple[int, int], tuple[str, str]]] = {}
    for row in table(path, VALUE_FIELDS):
        values.setdefault(row[0], {})[parse_cell(row[1])] = (row[2], row[3])
    return values


class Grid:
    def __init__(self, values: dict[tuple[int, int], tuple[str, str]]):
        self.values = values

    def get(self, cell: tuple[int, int]) -> tuple[str, str]:
        return self.values.get(cell, ('blank', '-'))


# --- the decision procedure --------------------------------------------------------------------------------


def number_text(value: float) -> str:
    if float(value).is_integer() and abs(value) < 1e15:
        return str(int(value))
    return repr(float(value))


def as_number(kind: str, payload: str):
    """Blank reads as zero; text is never folded into a number, because Excel does not fold it either."""
    if kind == 'blank':
        return 0.0
    if kind == 'num':
        return float(payload)
    return None


def text_of(kind: str, payload: str):
    if kind == 'text':
        return payload
    if kind == 'blank':
        return ''
    return None


def order(left: tuple[str, str], right: tuple[str, str]):
    """-1/0/+1 for `left` against `right`, or None when the pair has no defined order here."""
    if left[0] == 'error' or right[0] == 'error':
        return None
    if left[0] == 'bool' and right[0] == 'bool':
        return 0 if left[1] == right[1] else None
    if left[0] == 'bool' or right[0] == 'bool':
        return None
    # Excel's type rule: every number sorts before every text and no number ever equals a text.
    left_is_text, right_is_text = left[0] == 'text', right[0] == 'text'
    if left_is_text != right_is_text:
        other = right if left_is_text else left
        if other[0] not in ('num', 'blank'):
            return None
        return 1 if left_is_text else -1
    if left_is_text:
        x, y = text_of(*left), text_of(*right)
        if x is None or y is None:
            return None
        return 0 if x.casefold() == y.casefold() else None
    a, b = as_number(*left), as_number(*right)
    if a is not None and b is not None:
        return (a > b) - (a < b)
    return None


def equal(left: tuple[str, str], right: tuple[str, str]):
    if left[0] == 'error' or right[0] == 'error':
        return None
    if left[0] == 'bool' or right[0] == 'bool':
        if left[0] == right[0] == 'bool':
            return left[1] == right[1]
        return None
    if left[0] == 'blank' and right[0] == 'text':
        return right[1] == ''
    if left[0] == 'text' and right[0] == 'blank':
        return left[1] == ''
    if left[0] == 'text' or right[0] == 'text':
        x, y = text_of(*left), text_of(*right)
        if x is not None and y is not None:
            return x.casefold() == y.casefold()
        return False
    a, b = as_number(*left), as_number(*right)
    if a is not None and b is not None:
        return a == b
    return None


HOLDS = {
    'lessThan': lambda c: c < 0,
    'lessThanOrEqual': lambda c: c <= 0,
    'equal': lambda c: c == 0,
    'notEqual': lambda c: c != 0,
    'greaterThan': lambda c: c > 0,
    'greaterThanOrEqual': lambda c: c >= 0,
}
SYMBOLIC = {
    '<': lambda c: c < 0,
    '<=': lambda c: c <= 0,
    '=': lambda c: c == 0,
    '<>': lambda c: c != 0,
    '>': lambda c: c > 0,
    '>=': lambda c: c >= 0,
}


def operand(text: str):
    if NUMBER.match(text):
        return ('num', text)
    if len(text) >= 2 and text.startswith('"') and text.endswith('"'):
        return ('text', text[1:-1].replace('""', '"'))
    if text.upper() == 'TRUE':
        return ('bool', '1')
    if text.upper() == 'FALSE':
        return ('bool', '0')
    if CELL_REF.match(text.upper()):
        return ('ref', text.upper())
    return None


def resolve(candidate, grid: Grid):
    if candidate[0] == 'ref':
        return grid.get(parse_cell(candidate[1]))
    return candidate


def visuals(rule: Rule) -> list[str]:
    if rule.format is None:
        return []
    bold, italic, strike, underline, font_rgb, fill_rgb = rule.format
    out = []
    if '1' in (bold, italic, strike, underline) or font_rgb != '-':
        out.append(f'font:{bold}{italic}{strike}{underline}:{"-" if font_rgb == "-" else font_rgb}')
    if fill_rgb != '-':
        out.append(f'fill:{fill_rgb}')
    return out


class Unresolved(Exception):
    def __init__(self, priority: int, reason: str):
        self.priority = priority
        self.reason = reason


class No(Exception):
    pass


class Yes(Exception):
    def __init__(self, produced: list[str]):
        self.produced = produced


def numbers_in(blocks: dict[int, tuple[str, list[Rule]]], rule: Rule, grid: Grid) -> list[float]:
    out = []
    for _, (sqref, rules) in blocks.items():
        if not any(other is rule for other in rules):
            continue
        for token in sqref.split(' '):
            c1, r1, c2, r2 = parse_range(token)
            if (c2 - c1 + 1) * (r2 - r1 + 1) > MAX_RANGE_CELLS:
                continue
            for row in range(r1, r2 + 1):
                for column in range(c1, c2 + 1):
                    kind, payload = grid.get((column, row))
                    if kind == 'num':
                        out.append(float(payload))
    return out


def percentile(sorted_values: list[float], percent: float) -> float:
    if not sorted_values:
        return 0.0
    position = min(max(percent / 100.0, 0.0), 1.0) * (len(sorted_values) - 1)
    lower, upper = math.floor(position), math.ceil(position)
    if lower == upper:
        return sorted_values[lower]
    weight = position - lower
    return sorted_values[lower] * (1 - weight) + sorted_values[upper] * weight


def scope_too_large(rule: Rule, blocks) -> bool:
    for _, (sqref, rules) in blocks.items():
        if not any(other is rule for other in rules):
            continue
        for token in sqref.split(' '):
            c1, r1, c2, r2 = parse_range(token)
            if (c2 - c1 + 1) * (r2 - r1 + 1) > MAX_RANGE_CELLS:
                return True
    return False


def thresholds(rule: Rule, blocks, grid: Grid):
    values = sorted(numbers_in(blocks, rule, grid))
    if not values:
        return None
    minimum, maximum = values[0], values[-1]
    resolved = []
    for kind, value in rule.thresholds:
        if kind == 'min':
            resolved.append(minimum)
        elif kind == 'max':
            resolved.append(maximum)
        elif kind == 'num':
            resolved.append(value)
        elif kind == 'percent':
            resolved.append(minimum + (maximum - minimum) * value / 100.0)
        elif kind == 'percentile':
            resolved.append(percentile(values, value))
        else:
            return None
    return resolved


def mix(start: str, end: str, weight: float) -> str:
    weight = min(max(weight, 0.0), 1.0)
    out = ''
    for index in (0, 2, 4):
        a = int(start[index:index + 2], 16)
        b = int(end[index:index + 2], 16)
        out += f'{int(math.floor(a * (1 - weight) + b * weight + 0.5)):02X}'
    return out


def interpolate(colors: list[str], points: list[float], value: float) -> str:
    if not colors:
        return '000000'
    if len(colors) == 1:
        return colors[0].upper()
    if value <= points[0]:
        return colors[0].upper()
    if value >= points[-1]:
        return colors[-1].upper()
    for index in range(len(points) - 1):
        low, high = points[index], points[index + 1]
        if low <= value <= high:
            if high <= low:
                return colors[index].upper()
            return mix(colors[index], colors[index + 1], (value - low) / (high - low))
    return colors[-1].upper()


def decide(rule: Rule, blocks, grid: Grid, cell: tuple[int, int]) -> list[str]:
    if rule.kind == 'cellIs':
        if rule.operator is None:
            raise Unresolved(rule.priority, 'cellIs without an operator')
        left = grid.get(cell)
        right = []
        for formula in rule.formulas:
            candidate = operand(formula.strip())
            if candidate is None:
                raise Unresolved(rule.priority, f"cellIs operand '{formula}' is not a literal or a cell reference")
            right.append(resolve(candidate, grid))
        if rule.operator in ('between', 'notBetween'):
            if len(right) != 2:
                raise Unresolved(rule.priority, f'cellIs {rule.operator} needs two operands')
            low, high = order(left, right[0]), order(left, right[1])
            if low is None:
                raise Unresolved(rule.priority, f'cannot order {left} against {right[0]}')
            if high is None:
                raise Unresolved(rule.priority, f'cannot order {left} against {right[1]}')
            inside = low >= 0 and high <= 0
            if inside != (rule.operator == 'notBetween'):
                raise Yes(visuals(rule))
            raise No()
        if len(right) != 1:
            raise Unresolved(rule.priority, f'cellIs {rule.operator} needs one operand')
        comparison = order(left, right[0])
        if comparison is None:
            raise Unresolved(rule.priority, f'cannot order {left} against {right[0]}')
        if HOLDS[rule.operator](comparison):
            raise Yes(visuals(rule))
        raise No()
    if rule.kind == 'expression':
        if len(rule.formulas) != 1:
            raise Unresolved(rule.priority, 'an expression rule without exactly one formula')
        text = rule.formulas[0].strip()
        blank = IS_BLANK.match(text)
        if blank is not None:
            if grid.get(parse_cell(blank.group(1).upper()))[0] == 'blank':
                raise Yes(visuals(rule))
            raise No()
        modular = MOD_ROW.match(text)
        if modular is not None:
            divisor, remainder = int(modular.group(1)), int(modular.group(2))
            if divisor == 0:
                raise Unresolved(rule.priority, 'MOD by zero')
            if cell[1] % divisor == remainder:
                raise Yes(visuals(rule))
            raise No()
        match = COMPARISON.match(text)
        if match is None:
            raise Unresolved(rule.priority, f"expression '{text}' is outside the decided grammar")
        left_candidate = operand(match.group(1).strip())
        right_candidate = operand(match.group(3).strip())
        if left_candidate is None:
            raise Unresolved(rule.priority, f"left side '{match.group(1).strip()}' is not supported")
        if right_candidate is None:
            raise Unresolved(rule.priority, f"right side '{match.group(3).strip()}' is not supported")
        a, b = resolve(left_candidate, grid), resolve(right_candidate, grid)
        symbol = match.group(2)
        if symbol in ('=', '<>'):
            outcome = equal(a, b)
            if outcome is None:
                raise Unresolved(rule.priority, f'cannot compare {a} with {b}')
            if outcome == (symbol == '='):
                raise Yes(visuals(rule))
            raise No()
        comparison = order(a, b)
        if comparison is None:
            raise Unresolved(rule.priority, f'cannot order {a} against {b}')
        if SYMBOLIC[symbol](comparison):
            raise Yes(visuals(rule))
        raise No()
    if rule.kind == 'top10':
        if scope_too_large(rule, blocks):
            raise Unresolved(rule.priority, f'a rank-relative rule over a range larger than {MAX_RANGE_CELLS} cells')
        values = sorted(numbers_in(blocks, rule, grid))
        if not values:
            raise No()
        ordered = values if rule.bottom else list(reversed(values))
        take = math.ceil(len(ordered) * rule.rank / 100.0) if rule.percent else rule.rank
        take = max(take, 1)
        kind, payload = grid.get(cell)
        if take >= len(ordered):
            if kind == 'num':
                raise Yes(visuals(rule))
            raise No()
        cut = ordered[take - 1]
        if kind != 'num':
            raise No()
        value = float(payload)
        if (value <= cut) if rule.bottom else (value >= cut):
            raise Yes(visuals(rule))
        raise No()
    if rule.kind == 'colorScale':
        if scope_too_large(rule, blocks):
            raise Unresolved(rule.priority, f'a colour scale over a range larger than {MAX_RANGE_CELLS} cells')
        points = thresholds(rule, blocks, grid)
        if points is None or len(points) < 2:
            raise Unresolved(rule.priority, 'a colour scale whose thresholds cannot be resolved')
        if any(high < low for low, high in zip(points, points[1:])):
            raise Unresolved(rule.priority, 'a colour scale whose thresholds are not in ascending order')
        kind, payload = grid.get(cell)
        if kind == 'num':
            value = float(payload)
        elif kind == 'blank':
            value = points[0]
        else:
            raise Unresolved(rule.priority, f'a colour scale over {kind}')
        raise Yes([f'scale:{interpolate(rule.colors, points, value)}'])
    if rule.kind == 'dataBar':
        if scope_too_large(rule, blocks):
            raise Unresolved(rule.priority, f'a data bar over a range larger than {MAX_RANGE_CELLS} cells')
        points = thresholds(rule, blocks, grid)
        if points is None:
            raise Unresolved(rule.priority, 'a data bar whose thresholds cannot be resolved')
        if len(points) < 2:
            raise Unresolved(rule.priority, 'a data bar needs two thresholds')
        kind, payload = grid.get(cell)
        if kind != 'num':
            raise No()
        value = float(payload)
        low, high = points[0], points[-1]
        fraction = (1.0 if value >= high else 0.0) if high <= low else min(max((value - low) / (high - low), 0.0), 1.0)
        raise Yes([f'bar:{number_text(fraction)}'])
    raise Unresolved(rule.priority, f'unmodelled kind {rule.kind}')


def evaluate(blocks, grid: Grid, cell: tuple[int, int]):
    applicable = [
        rule
        for _, (sqref, rules) in sorted(blocks.items())
        if any(covers(token, cell) for token in sqref.split(' '))
        for rule in rules
    ]
    applicable.sort(key=lambda rule: rule.priority)
    fired, unresolved = [], []
    stopped = False
    for rule in applicable:
        try:
            decide(rule, blocks, grid, cell)
        except Unresolved as refused:
            unresolved.append(refused)
            continue
        except No:
            continue
        except Yes as hit:
            fired.append((rule, hit.produced))
            # `stop` is the raw TSV cell: '-' means absent, '0' means an explicit false, only '1' stops the scan.
            if rule.stop == '1':
                stopped = True
                break
    if not fired and unresolved:
        return 'undecided', [], [], False, applicable, unresolved[0]
    return 'decided', fired, unresolved, stopped, applicable, None


def decoration(fired) -> dict[str, str]:
    result = {'bold': '0', 'italic': '0', 'strike': '0', 'underline': '0',
              'fontRgb': '-', 'fillRgb': '-', 'scaleRgb': '-', 'bar': '-'}
    for rule, produced in fired:
        for entry in produced:
            family, _, payload = entry.partition(':')
            if family == 'font':
                bits, _, rgb = payload.partition(':')
                if bits[0] == '1':
                    result['bold'] = '1'
                if bits[1] == '1':
                    result['italic'] = '1'
                if bits[2] == '1':
                    result['strike'] = '1'
                if bits[3] == '1':
                    result['underline'] = '1'
                if rgb != '-' and result['fontRgb'] == '-':
                    result['fontRgb'] = rgb
            elif family == 'fill' and result['fillRgb'] == '-':
                result['fillRgb'] = payload
            elif family == 'scale' and result['scaleRgb'] == '-':
                result['scaleRgb'] = payload
            elif family == 'bar' and result['bar'] == '-':
                result['bar'] = payload
    return result


def expected_row(sheet: str, cell: tuple[int, int], blocks, grid: Grid) -> list[str]:
    outcome = evaluate(blocks, grid, cell)
    applicable = outcome[4]
    fired = outcome[1]
    if outcome[0] == 'undecided':
        reason = outcome[5]
        return [sheet, to_a1(cell), 'undecided', '-', '-', '0', str(reason.priority), '-', '-', '-', '-', '-',
                '-', '-', '-', '-', str(len(applicable)), reason.reason]
    painted = decoration(fired)
    return [
        sheet, to_a1(cell), 'decided',
        SEPARATOR.join(str(rule.priority) for rule, _ in fired) if fired else '-',
        SEPARATOR.join(rule.kind for rule, _ in fired) if fired else '-',
        '1' if outcome[3] else '0',
        SEPARATOR.join(str(item.priority) for item in outcome[2]) if outcome[2] else '-',
        SEPARATOR.join(entry for _, produced in fired for entry in produced) if fired else '-',
        painted['bold'], painted['italic'], painted['strike'], painted['underline'],
        painted['fontRgb'], painted['fillRgb'], painted['scaleRgb'], painted['bar'],
        str(len(applicable)),
        '-',
    ]


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    require(folder.is_dir(), f'no artifact folder {folder}')
    block_table = rules_by_sheet(folder / 'rules.tsv')
    value_table = values_by_sheet(folder / 'values.tsv')
    rows = table(folder / 'verdicts.tsv', VERDICT_FIELDS)
    totals = {'cells': 0, 'fired': 0, 'undecided': 0, 'unresolved': 0, 'sheets': len(block_table)}
    seen = set()
    for row in rows:
        sheet, cell_text = row[0], row[1]
        require(sheet in block_table, f'verdicts.tsv names a sheet with no rules: {sheet}')
        cell = parse_cell(cell_text)
        seen.add((sheet, cell))
        blocks = block_table[sheet]
        grid = Grid(value_table.get(sheet, {}))
        expected = expected_row(sheet, cell, blocks, grid)
        for index, (want, got) in enumerate(zip(expected, row)):
            # Field 17 is prose: it says *why* a rule was refused. Both sides must say something, but the wording
            # is not a contract and comparing it would only pin two dialects of the same sentence.
            if index == 17:
                # A refusal explains itself; a decided row has no explanation, and '-' is how that is spelled.
                require((got == '-') == (want == '-'),
                        f'{sheet}!{cell_text}: one side explains the refusal and the other does not '
                        f'(wrote {got!r}, recomputed {want!r})')
                continue
            require(want == got, f'{sheet}!{cell_text} field {index}: wrote {got!r}, recomputed {want!r}')
        totals['cells'] += 1
        totals['fired'] += 0 if row[3] == '-' else len(row[3].split(SEPARATOR))
        totals['undecided'] += 1 if row[2] == 'undecided' else 0
        totals['unresolved'] += 0 if row[6] == '-' else len(row[6].split(SEPARATOR))
    # Every declared rule must be exercised: a rule that appears in no row was never tested.
    exercised: set[tuple[str, int]] = set()
    for row in rows:
        for field in (3, 6):
            if row[field] == '-':
                continue
            for token in row[field].split(SEPARATOR):
                exercised.add((row[0], int(token)))
    for sheet, blocks in block_table.items():
        for index, (_, rules) in blocks.items():
            for rule in rules:
                require(
                    (sheet, rule.priority) in exercised,
                    f'{sheet}: rule {rule.priority} ({rule.kind}, block {index}) never fired and was never refused',
                )
    if require_openpyxl:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            totals.update(verify_openpyxl(folder, block_table))
    return totals


def verify_openpyxl(folder: Path, block_table) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415

    workbook = openpyxl.load_workbook(folder / 'r97.xlsx')
    totals = {'reader_sheets': 0, 'reader_rules': 0}
    for sheet, blocks in block_table.items():
        require(sheet in workbook.sheetnames, f'r97.xlsx has no sheet {sheet}')
        # openpyxl keys its ConditionalFormattingList by range, so several blocks that name the same sqref arrive
        # merged into one group. The comparison is therefore per sqref, not per block: the file is legal either way.
        declared: dict[str, list[int]] = {}
        for _, (sqref, rules) in blocks.items():
            declared.setdefault(sqref, []).extend(rule.priority for rule in rules)
        found: dict[str, list[int]] = {}
        for group in workbook[sheet].conditional_formatting:
            key = ' '.join(str(group.sqref).split())
            found.setdefault(key, []).extend(rule.priority for rule in group.rules)
        require(set(found) == set(declared),
                f'{sheet}: package carries sqrefs {sorted(found)}, rules.tsv declares {sorted(declared)}')
        for sqref, priorities in declared.items():
            require(sorted(found[sqref]) == sorted(priorities),
                    f'{sheet}:{sqref}: package priorities {sorted(found[sqref])} != declared {sorted(priorities)}')
            totals['reader_rules'] += len(priorities)
        totals['reader_sheets'] += 1
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R97 verdict verification FAILED: {failure}')
        return 1
    except (zipfile.BadZipFile, KeyError) as error:
        print(f'R97 verdict verification FAILED: unreadable package: {error}')
        return 1
    message = (f'R97 verdicts: {totals["cells"]} cell verdict(s) over {totals["sheets"]} sheet(s), '
               f'{totals["fired"]} rule hit(s), {totals["unresolved"]} unresolved rule evaluation(s), '
               f'{totals["undecided"]} cell(s) with no verdict at all; recomputed independently in Python')
    if 'reader_rules' in totals:
        message += f'; openpyxl confirmed {totals["reader_rules"]} rule(s) in {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
