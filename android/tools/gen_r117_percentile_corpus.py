#!/usr/bin/env python3
"""Generate the R117 percentile/aggregate corpus: `function<TAB>data<TAB>k<TAB>expected`.

R117 adds the `*.INC`/`*.EXC` percentile variants and the `A`-suffixed aggregate family
(AVERAGEA/MAXA/MINA/VARA/VARPA/STDEVA/STDEVPA) to the formula engine. The expected column is produced
here from the documented definitions — ECMA-376 18.17.7 for the percentile rank, Microsoft's function
reference for the `A` family — and is *independently* recomputed in `tools/test_r117_percentile.py`
through CPython's `statistics.quantiles` (inclusive/exclusive) and a separate transcription of the
`A`-family counting rules. Two implementations agreeing is evidence; one implementation writing its own
fixture is a transcript.

Where the spreadsheet and the statistics library disagree, the corpus encodes the spreadsheet:
`PERCENTILE.EXC`/`QUARTILE.EXC` refuse ranks whose position falls outside the data (`#NUM!`), whereas
`statistics.quantiles` extrapolates linearly. The refusal is the documented behaviour, stated once in
the product source and asserted here.

`data` is a comma-separated token list. A token is a number, `TRUE`/`FALSE`, a double-quoted text
string (no commas inside), `#N/A` (an error cell), or `EMPTY` (an empty cell). `k` is the percentile
rank, empty for the `A` family.

Deterministic: no random module, no clock, no environment. Re-running the generator must reproduce the
committed file byte for byte, and `tools/check_r117_percentile.py` fails if it does not.
"""
from __future__ import annotations

import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r117/percentile_corpus.tsv'
HEADER = 'function\tdata\tk\texpected'

PERCENTILE_NAMES = ('PERCENTILE', 'PERCENTILE.INC', 'PERCENTILE.EXC', 'QUARTILE', 'QUARTILE.INC', 'QUARTILE.EXC')
A_NAMES = ('AVERAGEA', 'MAXA', 'MINA', 'VARA', 'VARPA', 'STDEVA', 'STDEVPA')

# All-numeric datasets for the percentile functions, from two elements to eleven, with ties, a
# constant dataset, negatives, and a single element (the boundary the two methods treat differently).
DATASETS: list[list[float]] = [
    [1.0, 2.0, 3.0, 4.0],
    [2.0, 4.0, 6.0],
    [10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0],
    [3.0, 1.0, 4.0, 1.0, 5.0, 9.0, 2.0, 6.0, 5.0, 3.0, 5.0],
    [1.0, 2.0],
    [0.0, 0.0, 0.0, 0.0],
    [-5.0, -1.0, 0.0, 1.0, 5.0],
    [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0],
    [100.0, 200.0, 300.0],
    [7.0, 7.0, 7.0, 7.0, 7.0],
    [5.0],
    [42.0, 42.0],
]

PERCENTILE_KS = [0.0, 0.05, 0.1, 0.25, 0.3, 0.5, 0.7, 0.75, 0.9, 0.95, 1.0]
QUARTILE_KS = [0.0, 1.0, 2.0, 3.0, 4.0]

# Mixed-type datasets for the `A` family: text (numeric-looking and not), logicals, an empty cell,
# an error cell, a single value, and nothing but empties.
A_DATASETS: list[list[str]] = [
    ['1', '2', '3', '4'],
    ['1', '"x"', '3'],
    ['TRUE', '2', '3'],
    ['FALSE', 'FALSE', '5'],
    ['1', '2', '"5"'],
    ['"a"', '"b"'],
    ['TRUE', 'FALSE'],
    ['1', '2', '3', 'EMPTY'],
    ['10', '20', '30', '40'],
    ['5'],
    ['1', '2'],
    ['0', '0', '0'],
    ['-3', '7'],
    ['2.5', '3.5', '"text"', 'TRUE', '10'],
    ['EMPTY'],
    ['1', '#N/A', '3'],
    ['3', '1', '4', '1', '5'],
    ['"0"', '10'],
    ['TRUE', 'TRUE', 'FALSE'],
    ['100', '"x"', 'TRUE', 'FALSE', 'EMPTY'],
    ['-1', '-2', '-3'],
    ['7', '7', '7', '7'],
    ['0.5', '1.5', '2.5'],
    ['"hello"', '42', 'TRUE'],
]


def percentile(data: list[float], k: float, exclusive: bool) -> float | str:
    """The documented percentile rank; `#NUM!` where the spreadsheet refuses."""
    ordered = sorted(data)
    n = len(ordered)
    if not exclusive and n == 1:
        return ordered[0]
    pos = k * (n + 1) - 1.0 if exclusive else k * (n - 1)
    if exclusive and (pos < 0.0 or pos > n - 1):
        return '#NUM!'
    lo = math.floor(pos)
    hi = math.ceil(pos)
    frac = pos - lo
    return ordered[lo] + frac * (ordered[hi] - ordered[lo])


def percentile_expected(name: str, data: list[float], k: float) -> float | str:
    exclusive = name.endswith('.EXC')
    if name.startswith('QUARTILE'):
        q = int(k)
        if exclusive:
            if q not in (1, 2, 3):
                return '#NUM!'
        elif q not in (0, 1, 2, 3, 4):
            return '#NUM!'
        k = q * 0.25
    else:
        if exclusive:
            if k <= 0.0 or k >= 1.0:
                return '#NUM!'
        elif k < 0.0 or k > 1.0:
            return '#NUM!'
    return percentile(data, k, exclusive)


def a_values(tokens: list[str]) -> list[float] | str:
    """The `A`-family counting rules: text as 0, TRUE as 1, FALSE as 0, empty skipped, errors propagate."""
    out: list[float] = []
    for token in tokens:
        if token == 'EMPTY':
            continue
        if token == '#N/A':
            return '#N/A'
        if token == 'TRUE':
            out.append(1.0)
        elif token == 'FALSE':
            out.append(0.0)
        elif token.startswith('"'):
            out.append(0.0)
        else:
            out.append(float(token))
    return out


def a_expected(name: str, tokens: list[str]) -> float | str:
    values = a_values(tokens)
    if isinstance(values, str):
        return values
    if name in ('VARA', 'STDEVA'):
        if len(values) < 2:
            return '#DIV/0!'
        mean = sum(values) / len(values)
        var = sum((v - mean) ** 2 for v in values) / (len(values) - 1)
        return math.sqrt(var) if name == 'STDEVA' else var
    if name in ('VARPA', 'STDEVPA'):
        if not values:
            return '#DIV/0!'
        mean = sum(values) / len(values)
        var = sum((v - mean) ** 2 for v in values) / len(values)
        return math.sqrt(var) if name == 'STDEVPA' else var
    if name == 'AVERAGEA':
        if not values:
            return '#DIV/0!'
        return sum(values) / len(values)
    if name == 'MAXA':
        return max(values) if values else 0.0
    return min(values) if values else 0.0


def rows() -> list[tuple[str, str, str, str]]:
    out: list[tuple[str, str, str, str]] = []
    for data in DATASETS:
        data_text = ','.join(repr(v) for v in data)
        for name in ('PERCENTILE', 'PERCENTILE.INC', 'PERCENTILE.EXC'):
            for k in PERCENTILE_KS:
                expected = percentile_expected(name, data, k)
                out.append((name, data_text, repr(k), expected if isinstance(expected, str) else repr(expected)))
        for name in ('QUARTILE', 'QUARTILE.INC', 'QUARTILE.EXC'):
            for q in QUARTILE_KS:
                expected = percentile_expected(name, data, q)
                out.append((name, data_text, repr(q), expected if isinstance(expected, str) else repr(expected)))
    # Refusals outside the rank domain, for every variant.
    for data in DATASETS[:4]:
        data_text = ','.join(repr(v) for v in data)
        for name in ('PERCENTILE', 'PERCENTILE.INC'):
            for k in (-0.1, 1.1):
                out.append((name, data_text, repr(k), '#NUM!'))
        for name in ('QUARTILE', 'QUARTILE.INC'):
            for q in (-1.0, 5.0):
                out.append((name, data_text, repr(q), '#NUM!'))
    for tokens in A_DATASETS:
        data_text = ','.join(tokens)
        for name in A_NAMES:
            expected = a_expected(name, tokens)
            out.append((name, data_text, '', expected if isinstance(expected, str) else repr(expected)))
    # A corpus is a set of observations, not a log: the first occurrence of each (function, data, k) wins.
    unique: list[tuple[str, str, str, str]] = []
    seen: set[tuple[str, str, str]] = set()
    for row in out:
        if row[:3] in seen:
            continue
        seen.add(row[:3])
        unique.append(row)
    return unique


def render() -> str:
    lines = [HEADER]
    for name, data, k, expected in rows():
        lines.append('\t'.join((name, data, k, expected)))
    return '\n'.join(lines) + '\n'


def main() -> int:
    text = render()
    CORPUS.parent.mkdir(parents=True, exist_ok=True)
    CORPUS.write_text(text, encoding='utf-8')
    total = len(text.strip().splitlines()) - 1
    errors = sum(1 for line in text.splitlines()[1:] if line.split('\t')[3].startswith('#'))
    print(f'R117_PERCENTILE_CORPUS_WRITTEN rows={total} error_rows={errors} path={CORPUS.relative_to(ROOT)}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
