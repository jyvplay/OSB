#!/usr/bin/env python3
"""R91 differential oracle for the data-validation *predicate*, not just its bytes.

The probe writes `validation_verdicts.tsv`: one row per (rule, value) pair with the JVM's decision
(`true` / `false` / `undecided`). This script reads the rule parameters from the same table — not from the
Kotlin source — and recomputes every decision here, in Python, with `decimal.Decimal` so the numeric
comparisons are exact rather than binary-floating-point approximations. Any disagreement is a failure.

The two implementations were written against the same *written* semantics (the docstring of
`XlsxValidation.accepts`), not against each other, so agreement is evidence that the DOCUMENTED rules were
implemented, and disagreement localises which rule is wrong. It cannot prove the semantics match Excel;
only Excel can.

Host-only. No spreadsheet application is involved.
"""
from __future__ import annotations

import argparse
import sys
from decimal import Decimal, InvalidOperation
from pathlib import Path

FIELDS = 8
NUMERIC = ('whole', 'decimal', 'date', 'time')
BINARY_OPS = ('between', 'notBetween')
SINGLE_OPS = ('equal', 'notEqual', 'lessThan', 'lessThanOrEqual', 'greaterThan', 'greaterThanOrEqual')


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def unescape(value: str) -> str:
    return value.replace('\\t', '\t').replace('\\n', '\n')


def literal(formula: str) -> Decimal | None:
    """A literal number operand; `DATE(...)`, `$A$1`, `Sheet!A1` are not decidable without a formula engine."""
    text = formula.strip()
    if not text:
        return None
    if any(ch.isalpha() or ch in '($!' for ch in text):
        return None
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


def list_items(formula: str) -> list[str] | None:
    text = formula.strip()
    if len(text) < 2 or not text.startswith('"') or not text.endswith('"'):
        return None
    body = text[1:-1]
    if '"' in body:
        return None
    return [item.strip() for item in body.split(',')]


def spreadsheet_spelling(value: Decimal) -> str:
    if value == value.to_integral_value() and abs(value) <= Decimal('1e15'):
        return str(int(value))
    return format(value.normalize(), 'f') if value.as_tuple().exponent < 0 else str(value)


def utf16_length(text: str) -> int:
    return len(text.encode('utf-16-le')) // 2


def compare(operator: str, actual: Decimal, formula1: str, formula2: str | None) -> bool | None:
    low = literal(formula1)
    if low is None:
        return None
    if operator not in BINARY_OPS:
        if operator not in SINGLE_OPS:
            return None
        return {
            'equal': actual == low,
            'notEqual': actual != low,
            'lessThan': actual < low,
            'lessThanOrEqual': actual <= low,
            'greaterThan': actual > low,
            'greaterThanOrEqual': actual >= low,
        }[operator]
    if formula2 is None:
        return None
    high = literal(formula2)
    if high is None:
        return None
    within = low <= actual <= high
    return within if operator == 'between' else not within


def accepts(type_token: str, operator: str, formula1: str, formula2: str | None,
            kind: str, value: str) -> str:
    """`'true'` / `'false'` / `'undecided'`, mirroring the documented semantics exactly."""
    if type_token == 'custom':
        return 'undecided'
    if type_token == 'list':
        items = list_items(formula1)
        if items is None:
            return 'undecided'
        if kind == 'txt':
            text = value
        elif kind == 'num':
            try:
                text = spreadsheet_spelling(Decimal(value))
            except InvalidOperation:
                return 'undecided'
        else:
            return 'undecided'
        return 'true' if any(item.lower() == text.lower() for item in items) else 'false'
    if type_token == 'textLength':
        if kind != 'txt':
            return 'undecided'
        decision = compare(operator, Decimal(utf16_length(value)), formula1, formula2)
    elif type_token in NUMERIC:
        if kind != 'num':
            return 'undecided'
        try:
            number = Decimal(value)
        except InvalidOperation:
            return 'undecided'
        if type_token == 'whole' and number % 1 != 0:
            return 'false'
        decision = compare(operator, number, formula1, formula2)
    else:
        return 'undecided'
    return 'undecided' if decision is None else ('true' if decision else 'false')


def verify(folder: Path) -> dict[str, int]:
    path = folder / 'validation_verdicts.tsv'
    require(path.is_file(), f'missing decision corpus {path}')
    rows = 0
    decided = 0
    undecided = 0
    rules = set()
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        rule_id, type_token, operator, formula1, formula2, kind, value, verdict = parts
        expected = accepts(type_token, operator, formula1,
                           None if formula2 == '-' else unescape(formula2), kind, unescape(value))
        require(expected == verdict,
                f'{rule_id} line {number} ({type_token}, {kind} {unescape(value)!r}): '
                f'JVM said {verdict!r}, the Python oracle says {expected!r}')
        rows += 1
        rules.add(rule_id)
        if verdict == 'undecided':
            undecided += 1
        else:
            decided += 1
    require(rows > 0, 'the decision corpus is empty')
    require(undecided > 0, 'no row exercised the "undecided" path, so the refusal contract is untested')
    require(decided > 0, 'no row produced a decision')
    return {'verdicts': rows, 'decided': decided, 'undecided': undecided, 'rules': len(rules)}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder)
    except Fail as failure:
        print(f'R91 validation-semantics verification FAILED: {failure}')
        return 1
    print(f'R91 decision corpus: {totals["verdicts"]} verdicts over {totals["rules"]} rules '
          f'({totals["decided"]} decided, {totals["undecided"]} refused as undecidable) recomputed in Python '
          'with Decimal arithmetic and identical to the JVM. Not Excel evidence.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
