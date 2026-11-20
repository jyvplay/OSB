#!/usr/bin/env python3
"""Generate the R119 legacy-statistics corpus: `function<TAB>a<TAB>b<TAB>c<TAB>expected`.

R119 adds the legacy statistical aliases the engine was missing — `VAR` (sample variance, the rename of
`VAR.S`), `VARP` (population variance, `VAR.P`), `STDEVP` (population standard deviation, `STDEV.P`),
`MODE` (`MODE.SNGL`), `COVARIANCE` (population covariance, `COVARIANCE.P`) — and `FORECAST` /
`FORECAST.LINEAR`, the least-squares prediction `intercept + slope * x`. Before this round every one of
those names returned `#NAME?` from a formula.

The expected column is produced here from the documented definitions (Microsoft's function reference for
each name; ECMA-376 18.17.7 for the aggregate and regression semantics) and is *independently* recomputed
in `tools/test_r119_legacy_stats.py` through CPython's `statistics` module (`variance`, `pvariance`,
`stdev`, `pstdev`, `linear_regression`) and a separate transcription of the pairing rules. Two
implementations agreeing is evidence; one implementation writing its own fixture is a transcript.

Columns: `a` is the first array (the data for the single-array functions, `known_y` for `COVARIANCE` /
`FORECAST`), `b` is the second array (`known_x`, empty for the single-array functions), `c` is the
`FORECAST` prediction point (empty otherwise). A token is a number, `TRUE`/`FALSE`, a double-quoted text
string (no commas inside), `#N/A` (an error cell), or `EMPTY` (an empty cell).

Deterministic: no random module, no clock, no environment. Re-running the generator must reproduce the
committed file byte for byte, and `tools/check_r119_legacy_stats.py` fails if it does not.
"""
from __future__ import annotations

import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r119/legacy_stats_corpus.tsv'
HEADER = 'function\ta\tb\tc\texpected'

SINGLE_NAMES = ('VAR', 'VARP', 'STDEVP', 'MODE')
PAIR_NAMES = ('COVARIANCE',)
FORECAST_NAMES = ('FORECAST', 'FORECAST.LINEAR')
NAMES = SINGLE_NAMES + PAIR_NAMES + FORECAST_NAMES

# Single-array datasets: sizes from one to eleven, ties (the MODE boundary), a constant dataset,
# negatives, decimals, and mixed-type rows (text/logicals/empty are ignored by the aggregate kernels;
# an error cell propagates for VAR/VARP/STDEVP but is ignored inside a range by MODE).
SINGLE_DATASETS: list[list[str]] = [
    ['1', '2', '3', '4'],
    ['2', '4', '6'],
    ['10', '20', '30', '40', '50', '60', '70'],
    ['3', '1', '4', '1', '5', '9', '2', '6', '5', '3', '5'],
    ['1', '2'],
    ['0', '0', '0', '0'],
    ['-5', '-1', '0', '1', '5'],
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'],
    ['100', '200', '300'],
    ['7', '7', '7', '7', '7'],
    ['5'],
    ['42', '42'],
    ['1', '2', '3'],
    ['1.5', '2.5', '3.5', '4.5'],
    ['-2.5', '0.5', '2.5'],
    ['1', '"x"', '3'],
    ['TRUE', '2', '3'],
    ['FALSE', 'FALSE', '5'],
    ['1', '2', '3', 'EMPTY'],
    ['"a"', '"b"'],
    ['TRUE', 'FALSE'],
    ['EMPTY'],
    ['1', '#N/A', '3'],
    ['2', '2', '3', '3', '4'],
    ['9', '9', '1', '1', '1'],
    ['0.1', '0.2', '0.3'],
    ['1000', '2000', '3000', '4000'],
    ['-1', '-2', '-3', '-4'],
    ['"5"', '2', '3'],
    ['"10"', '1', '2', '3'],
    ['1', '1', '2'],
    ['3', '3', '3', '2'],
    ['5', '4', '4', '4'],
    ['8', '8', '9', '9', '9'],
    ['6', '6', '6', '6', '1'],
    ['2', '3', '3', '3', '3'],
]

# Paired datasets for COVARIANCE: (known_y, known_x) with the same length, plus the mismatch and
# non-numeric-pair rows below.
PAIR_DATASETS: list[tuple[list[str], list[str]]] = [
    (['1', '2', '3', '4'], ['2', '4', '6', '8']),
    (['5', '3', '8'], ['1', '2', '3']),
    (['1', '2'], ['3', '4']),
    (['10', '20', '30', '40', '50'], ['5', '4', '3', '2', '1']),
    (['2', '2', '2'], ['1', '2', '3']),
    (['1', '2', '3', '4', '5', '6'], ['6', '5', '4', '3', '2', '1']),
    (['0', '0', '0'], ['1', '2', '3']),
    (['7'], ['3']),
    (['1', '2', '3'], ['1', '2']),
    (['1', '"x"', '3'], ['1', '2', '3']),
    (['1', '2', '3'], ['1', '#N/A', '3']),
    (['-1', '0', '1'], ['2', '2', '2']),
    (['3', '6', '9', '12'], ['1', '2', '3', '4']),
    (['1', '1', '2', '3', '5'], ['1', '2', '3', '4', '5']),
    (['4', '3', '2', '1'], ['1', '2', '3', '4']),
    (['0', '5', '10'], ['2', '4', '6']),
    (['8', '7', '6', '5'], ['1', '3', '5', '7']),
    (['2', '4'], ['1', '3']),
    (['6', '4', '2'], ['3', '2', '1']),
    (['1.5', '2.5', '3.5'], ['0.5', '1.5', '2.5']),
]

# FORECAST datasets: (known_y, known_x, prediction points). Includes an exact line, noisy data, a
# constant known_x (refused), a single pair (refused), a mismatch (refused), and non-numeric pairs.
FORECAST_DATASETS: list[tuple[list[str], list[str], list[str]]] = [
    (['2', '4', '6', '8'], ['1', '2', '3', '4'], ['0', '2.5', '5', '10']),
    (['3', '5', '7'], ['1', '2', '3'], ['1', '2', '4']),
    (['1', '2', '3', '4', '5'], ['5', '4', '3', '2', '1'], ['0', '3', '6']),
    (['10', '20', '30', '40', '50', '60', '70'], ['1', '2', '3', '4', '5', '6', '7'], ['3.5', '8']),
    (['5', '5', '5'], ['1', '2', '3'], ['2']),
    (['9'], ['4'], ['4']),
    (['1', '2', '3'], ['1', '2'], ['2']),
    (['1', '"x"', '3'], ['1', '2', '3'], ['2']),
    (['1', '2', '3'], ['1', '#N/A', '3'], ['2']),
    (['0', '1', '2', '3'], ['0', '1', '2', '3'], ['-1', '1.5', '4']),
    (['100', '200'], ['10', '20'], ['15']),
    (['2.5', '3.5', '4.5', '5.5'], ['1', '2', '3', '4'], ['2.5']),
    (['1', '2'], ['5', '5'], ['3']),
    (['3'], ['7'], ['1']),
]


def numeric_values(tokens: list[str]) -> list[float] | str:
    """The aggregate-kernel view of one array: numbers only, text/logicals/empty ignored, errors propagate."""
    out: list[float] = []
    for token in tokens:
        if token == 'EMPTY':
            continue
        if token == '#N/A':
            return '#N/A'
        if token in ('TRUE', 'FALSE') or token.startswith('"'):
            continue
        out.append(float(token))
    return out


def single_expected(name: str, tokens: list[str]) -> float | str:
    if name == 'MODE':
        # MODE.SNGL collects numbers only; an error cell inside a range is ignored, not propagated.
        nums = [float(t) for t in tokens if t not in ('EMPTY', '#N/A', 'TRUE', 'FALSE') and not t.startswith('"')]
        if not nums:
            return '#N/A'
        counts: dict[float, int] = {}
        for value in nums:
            counts[value] = counts.get(value, 0) + 1
        best_value: float | None = None
        best_count = 1
        for value, count in counts.items():
            if count > best_count:
                best_value, best_count = value, count
        return '#N/A' if best_value is None else best_value
    values = numeric_values(tokens)
    if isinstance(values, str):
        return values
    n = len(values)
    if name == 'VAR':
        if n < 2:
            return '#DIV/0!'
        mean = sum(values) / n
        return sum((v - mean) ** 2 for v in values) / (n - 1)
    if name == 'VARP':
        if n < 1:
            return '#DIV/0!'
        mean = sum(values) / n
        return sum((v - mean) ** 2 for v in values) / n
    if name == 'STDEVP':
        if n < 1:
            return '#DIV/0!'
        mean = sum(values) / n
        return math.sqrt(sum((v - mean) ** 2 for v in values) / n)
    raise AssertionError(name)


def paired_values(y_tokens: list[str], x_tokens: list[str]) -> tuple[list[float], list[float]] | str:
    """The paired view shared by COVARIANCE and FORECAST: same-length check, error propagation, skip non-numeric."""
    if len(y_tokens) != len(x_tokens):
        return '#N/A'
    ys: list[float] = []
    xs: list[float] = []
    for y, x in zip(y_tokens, x_tokens):
        if y == '#N/A' or x == '#N/A':
            return '#N/A'
        if y in ('TRUE', 'FALSE', 'EMPTY') or x in ('TRUE', 'FALSE', 'EMPTY'):
            continue
        if y.startswith('"') or x.startswith('"'):
            continue
        ys.append(float(y))
        xs.append(float(x))
    return ys, xs


def covariance_expected(y_tokens: list[str], x_tokens: list[str]) -> float | str:
    paired = paired_values(y_tokens, x_tokens)
    if isinstance(paired, str):
        return paired
    ys, xs = paired
    n = len(xs)
    if n < 1:
        return '#DIV/0!'
    mx = sum(xs) / n
    my = sum(ys) / n
    return sum((xs[i] - mx) * (ys[i] - my) for i in range(n)) / n


def forecast_expected(y_tokens: list[str], x_tokens: list[str], x: float) -> float | str:
    paired = paired_values(y_tokens, x_tokens)
    if isinstance(paired, str):
        return paired
    ys, xs = paired
    n = len(xs)
    if n < 2:
        return '#DIV/0!'
    mx = sum(xs) / n
    my = sum(ys) / n
    sxx = sum((v - mx) ** 2 for v in xs)
    if sxx == 0.0:
        return '#DIV/0!'
    sxy = sum((xs[i] - mx) * (ys[i] - my) for i in range(n))
    slope = sxy / sxx
    return (my - slope * mx) + slope * x


def rows() -> list[tuple[str, str, str, str, str]]:
    out: list[tuple[str, str, str, str, str]] = []
    for tokens in SINGLE_DATASETS:
        data = ','.join(tokens)
        for name in SINGLE_NAMES:
            expected = single_expected(name, tokens)
            out.append((name, data, '', '', expected if isinstance(expected, str) else repr(expected)))
    for y_tokens, x_tokens in PAIR_DATASETS:
        y_text, x_text = ','.join(y_tokens), ','.join(x_tokens)
        expected = covariance_expected(y_tokens, x_tokens)
        out.append(('COVARIANCE', y_text, x_text, '', expected if isinstance(expected, str) else repr(expected)))
    for y_tokens, x_tokens, points in FORECAST_DATASETS:
        y_text, x_text = ','.join(y_tokens), ','.join(x_tokens)
        for name in FORECAST_NAMES:
            for point in points:
                expected = forecast_expected(y_tokens, x_tokens, float(point))
                out.append((name, y_text, x_text, point, expected if isinstance(expected, str) else repr(expected)))
    # A corpus is a set of observations, not a log: the first occurrence of each row wins.
    unique: list[tuple[str, str, str, str, str]] = []
    seen: set[tuple[str, str, str, str]] = set()
    for row in out:
        if row[:4] in seen:
            continue
        seen.add(row[:4])
        unique.append(row)
    return unique


def render() -> str:
    lines = [HEADER]
    for name, a, b, c, expected in rows():
        lines.append('\t'.join((name, a, b, c, expected)))
    return '\n'.join(lines) + '\n'


def main() -> int:
    text = render()
    CORPUS.parent.mkdir(parents=True, exist_ok=True)
    CORPUS.write_text(text, encoding='utf-8')
    total = len(text.strip().splitlines()) - 1
    errors = sum(1 for line in text.splitlines()[1:] if line.split('\t')[4].startswith('#'))
    print(f'R119_LEGACY_STATS_CORPUS_WRITTEN rows={total} error_rows={errors} path={CORPUS.relative_to(ROOT)}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
