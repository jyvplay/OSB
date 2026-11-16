#!/usr/bin/env python3
"""Second, independent implementation of R93's AutoFilter row predicate.

`XlsxAutoFilter.matches` in Kotlin decides whether a cell survives a column filter. This module re-derives
every decision in the corpus `filter_verdicts.tsv` from the *declared* criterion (the table stores the
criterion, not the answer) and compares. Two implementations in two languages, written against the same
documented contract; neither of them is Excel, so agreement means "this code does what it says", not "Excel
agrees".

The contract, exactly as both implementations state it:

* a **blank** cell is undecided — Excel's blanks handling is not modelled;
* a **boolean** cell is undecided — `CT_CustomFilter` operands are text, and Excel compares booleans in a way
  this model does not represent;
* a **number** is compared numerically against a numeric operand; a non-numeric operand leaves it undecided;
* a **text** cell is compared case-insensitively with `*` (any run) and `?` (one character) as wildcards;
  an ordering operator (`greaterThan` and friends) against text is undecided because it needs a collation;
* a two-criterion `<customFilters>` joins with `and` or `or`; if either half is undecided the whole is
  undecided;
* the number spelling used for the text comparison of a numeric cell is the integral spelling when the value
  has no fractional part and is within ±1e15, otherwise the float spelling (Kotlin `Double.toString` and
  Python `repr` agree on the corpus values but are not identical for exponent ranges outside it).

Host-only. Utility: `python3 tools/check_r93_filter_semantics.py testing/build/r93/artifacts`
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

UNIT = '\u001f'
RECORD = '\u001e'
FIELDS = 7
APPLIED_FIELDS = 6
FILTER_FIELDS = 9
UNDECIDED = 'undecided'
NUMERIC = None  # filled by numeric_operand


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def numeric_operand(text: str) -> float | None:
    """Kotlin `String.toDoubleOrNull` for the subset that is a plain decimal literal (no inf/nan/underscores)."""
    body = text.strip()
    if not body:
        return None
    sign = 1.0
    if body[0] in '+-':
        sign = -1.0 if body[0] == '-' else 1.0
        body = body[1:]
    if not body:
        return None
    mantissa, _, exponent = body.partition('e') if 'e' in body else body.partition('E')
    if mantissa.count('.') > 1:
        return None
    whole, _, fraction = mantissa.partition('.')
    if not (whole.isdigit() or fraction.isdigit()):
        return None
    if whole and not whole.isdigit():
        return None
    if fraction and not fraction.isdigit():
        return None
    if exponent:
        exp = exponent[1:] if exponent[0] in '+-' else exponent
        if not exp.isdigit():
            return None
    return sign * float(body)


def number_spelling(value: float) -> str:
    if value % 1.0 == 0.0 and abs(value) <= 1e15:
        return str(int(value))
    return repr(value)


def glob(pattern: str, text: str) -> bool:
    """`*` any run, `?` one character, case-insensitive; dynamic programming over prefixes (not Kotlin's pointers)."""
    p = pattern.lower()
    t = text.lower()
    reachable = [False] * (len(t) + 1)
    reachable[0] = True
    for token in p:
        following = [False] * (len(t) + 1)
        if token == '*':
            following[0] = reachable[0]
            for index in range(1, len(t) + 1):
                following[index] = reachable[index] or following[index - 1]
        elif token == '?':
            for index in range(1, len(t) + 1):
                following[index] = reachable[index - 1]
        else:
            for index in range(1, len(t) + 1):
                following[index] = reachable[index - 1] and t[index - 1] == token
        reachable = following
    return reachable[len(t)]


def decide(operator: str, operand: str, kind: str, value: str) -> bool | None:
    if kind == 'num':
        expected = numeric_operand(operand)
        if expected is None:
            return None
        actual = float(value)
        return {
            'greaterThan': actual > expected,
            'greaterThanOrEqual': actual >= expected,
            'lessThan': actual < expected,
            'lessThanOrEqual': actual <= expected,
            'equal': actual == expected,
            'notEqual': actual != expected,
        }[operator]
    if operator not in {'equal', 'notEqual'}:
        return None
    hit = glob(operand, value)
    return hit if operator == 'equal' else not hit


def predicate(kind: str, payload: str, join: str, value_kind: str, value: str) -> str:
    if value_kind == 'blank' or value_kind == 'bool':
        return UNDECIDED
    if value_kind not in {'num', 'txt'}:
        raise Fail(f'unknown value kind {value_kind!r}')
    if kind == 'values':
        values = payload.split(UNIT) if payload else []
        require(values, 'a values rule without values')
        text = number_spelling(float(value)) if value_kind == 'num' else value
        return 'true' if any(glob(item, text) for item in values) else 'false'
    require(kind == 'custom', f'unknown criterion kind {kind!r}')
    criteria = payload.split(UNIT) if payload else []
    require(1 <= len(criteria) <= 2, f'a custom rule with {len(criteria)} criteria')
    results = []
    for item in criteria:
        operator, _, operand = item.partition('=')
        results.append(decide(operator, operand, value_kind, value))
    if any(result is None for result in results):
        return UNDECIDED
    if len(results) == 1:
        # A single criterion must not declare a join.
        require(join == '-', f'a one-criterion rule declares join {join!r}')
        return 'true' if results[0] else 'false'
    require(join in {'and', 'or'}, f'a two-criterion rule declares join {join!r}')
    combined = (results[0] and results[1]) if join == 'and' else (results[0] or results[1])
    return 'true' if combined else 'false'


def unescape(field: str) -> str:
    out = []
    index = 0
    while index < len(field):
        if field[index] == '\\' and index + 1 < len(field) and field[index + 1] in 'tn':
            out.append('\t' if field[index + 1] == 't' else '\n')
            index += 2
        else:
            out.append(field[index])
            index += 1
    return ''.join(out)


def read_corpus(path: Path) -> list[list[str]]:
    require(path.is_file(), f'missing verdict corpus {path}')
    rows = []
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS,
                f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}: {line!r}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def verify(path: Path) -> dict[str, int]:
    rows = read_corpus(path)
    seen: dict[tuple[str, str, str], str] = {}
    decided = 0
    undecided = 0
    for rule, kind, payload, join, value_kind, raw_value, expected in rows:
        require(expected in {'true', 'false', UNDECIDED}, f'{rule}: unknown verdict {expected!r}')
        value = '-' if value_kind == 'blank' else unescape(raw_value)
        got = predicate(kind, payload, join, value_kind, value)
        require(got == expected,
                f'{rule} on {value_kind} {value!r}: Kotlin said {expected}, this implementation says {got}')
        key = (kind, payload, join)
        seen.setdefault(key, rule)
        require(seen[key] == rule, f'the corpus mixes rules under one id: {key}')
        if expected == UNDECIDED:
            undecided += 1
        else:
            decided += 1
    require(len(seen) >= 7, f'the corpus only covers {len(seen)} rule(s)')
    return {'verdicts': len(rows), 'rules': len(seen), 'decided': decided, 'undecided': undecided}


def read_table(path: Path, fields: int) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows = []
    # Newlines only: `applied_filters.tsv` uses \x1e between records, and `str.splitlines()` splits there.
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'{path.name} line {number}: {len(parts)} fields, expected {fields}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def column_of(letters: str) -> int:
    value = 0
    for ch in letters:
        value = value * 26 + (ord(ch) - ord('A') + 1)
    return value


def split_ref(ref: str) -> tuple[int, int, int]:
    """(first column, first row, last row) of an A1:B8 range."""
    def parts(cell: str) -> tuple[str, int]:
        letters = ''.join(ch for ch in cell if ch.isalpha())
        digits = ''.join(ch for ch in cell if ch.isdigit())
        return letters, int(digits)

    first, _, rest = ref.partition(':')
    first_letters, first_row = parts(first)
    last_letters, last_row = parts(rest or first)
    return column_of(first_letters), first_row, last_row


def verify_applied(folder: Path) -> dict[str, int]:
    """Recompute which rows an applied filter excludes, from the table and the criterion, independently."""
    applied = read_table(folder / 'applied_filters.tsv', APPLIED_FIELDS)
    # A list, not a dict keyed by (file, sheet): one sheet has several filterColumn rows.
    filters = read_table(folder / 'autofilters.tsv', FILTER_FIELDS)
    hidden_total = 0
    undecided_total = 0
    for name, sheet, hidden_field, undecided_field, rows_field, table_field in applied:
        declared_hidden = {int(item) for item in hidden_field.split(',') if item}
        declared_undecided = {int(item) for item in undecided_field.split(',') if item}
        declared_rows = [int(item) for item in rows_field.split(',') if item]
        columns = [row for row in filters if row[0] == name and row[1] == sheet]
        require(columns, f'{name}:{sheet} carries hidden rows but no declared filter')
        refs = {row[2] for row in columns}
        require(len(refs) == 1, f'{name}:{sheet} declares several refs')
        ref = refs.pop()
        first_column, first_row, last_row = split_ref(ref)
        require(declared_rows == list(range(first_row, last_row + 1)),
                f'{name}:{sheet} declares rows {declared_rows} for {ref}')
        table = [[cell for cell in record.split(UNIT)] for record in table_field.split(RECORD)]
        require(len(table) == last_row - first_row + 1,
                f'{name}:{sheet} table has {len(table)} row(s), range has {last_row - first_row + 1}')
        criteria = {}
        for row in columns:
            col_id = int(row[3])
            require(row[4] in {'values', 'custom'}, f'{name}:{sheet} unknown criterion kind {row[4]!r}')
            criteria[col_id] = row
        hidden = set()
        undecided = set()
        for offset, record in enumerate(table):
            row_number = first_row + offset
            if row_number == first_row:
                continue  # the header row is never hidden
            exclude = False
            unknown = False
            for col_id, row in sorted(criteria.items()):
                index = col_id  # the table starts at the range's first column
                cell = record[index] if index < len(record) else ''
                kind = row[4]
                payload = row[5]
                join = row[6]
                if not cell:
                    verdict = UNDECIDED
                elif cell[0] == 'n':
                    verdict = predicate(kind, payload, join, 'num', cell[1:])
                elif cell[0] == 't':
                    verdict = predicate(kind, payload, join, 'txt', cell[1:])
                else:
                    raise Fail(f'{name}:{sheet} row {row_number} has an untyped cell {cell!r}')
                if verdict == 'false':
                    exclude = True
                elif verdict == UNDECIDED:
                    unknown = True
            if exclude:
                hidden.add(row_number)
            elif unknown:
                undecided.add(row_number)
        require(hidden == declared_hidden,
                f'{name}:{sheet} declares hidden {sorted(declared_hidden)}, this implementation computes '
                f'{sorted(hidden)}')
        require(undecided == declared_undecided,
                f'{name}:{sheet} declares undecided {sorted(declared_undecided)}, this implementation computes '
                f'{sorted(undecided)}')
        require(not (hidden & undecided), f'{name}:{sheet} computes a row both hidden and undecided')
        require(first_row not in hidden, f'{name}:{sheet} would hide the header row')
        hidden_total += len(hidden)
        undecided_total += len(undecided)
    return {'applied_sheets': len(applied), 'applied_hidden': hidden_total,
            'applied_undecided': undecided_total}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder / 'filter_verdicts.tsv')
        totals.update(verify_applied(args.folder))
    except Fail as failure:
        print(f'R93 filter semantics FAILED: {failure}')
        return 1
    print(f'R93 predicate agreement: {totals["verdicts"]} verdicts over {totals["rules"]} rule(s), '
          f'{totals["decided"]} decided and {totals["undecided"]} undecided, identical in Kotlin and Python; '
          f'{totals["applied_sheets"]} applied filter(s) recomputed to {totals["applied_hidden"]} hidden and '
          f'{totals["applied_undecided"]} undecided row(s)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
