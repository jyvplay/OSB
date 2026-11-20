#!/usr/bin/env python3
"""Generate the R116 trigonometry corpus: `function<TAB>x<TAB>y<TAB>expected`.

The expected column is produced by CPython's `math` module, i.e. by the platform C library (`libm`), not by the
Kotlin code under test and not by a model of it. That is what makes the fixture an oracle rather than a transcript:
`kotlin.math.sin` resolves to `java.lang.Math.sin`, a different implementation (fdlibm on the JVM), and agreement to
1e-12 relative is a measurement of two independent implementations, not a tautology.

Error rows carry the spreadsheet literal instead of a number. They encode behaviour `libm` has no opinion about —
`ASIN` outside [-1, 1], `ACOSH` below 1, `ATANH` at or beyond ±1, `ATAN2(0, 0)`, and `COSH` overflow — which is why
the domain rules are stated once in the product source and asserted here rather than derived.

Deterministic: no random module, no clock, no environment. Re-running the generator must reproduce the committed file
byte for byte, and `tools/check_r116_trig.py` fails if it does not.
"""
from __future__ import annotations

import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / 'format/ooxml/src/test/resources/r116/trig_corpus.tsv'
HEADER = 'function\tx\ty\texpected'


def uniform(low: float, high: float, count: int) -> list[float]:
    step = (high - low) / (count - 1)
    return [low + step * i for i in range(count)]


def powers(limit: int, negative: bool = True) -> list[float]:
    values = [10.0 ** k for k in range(-limit, limit + 1)]
    return values + [-v for v in values] if negative else values


def rows() -> list[tuple[str, str, str, str]]:
    out: list[tuple[str, str, str, str]] = []

    def num(name: str, x: float, y: float | None = None) -> None:
        out.append((name, repr(x), '' if y is None else repr(y), ''))

    def err(name: str, x: float, literal: str) -> None:
        out.append((name, repr(x), '', literal))

    # Circular, unit radian, over two full turns plus the exact quadrant angles.
    circular = uniform(-2.0 * math.pi, 2.0 * math.pi, 64) + [
        0.0, 1e-9, -1e-9, 1e-300, math.pi / 6, math.pi / 4, math.pi / 3, math.pi / 2,
        -math.pi / 2, math.pi, -math.pi, 2.0 * math.pi, -2.0 * math.pi, 1e6, -1e6, 12345.6789,
    ]
    for name in ('SIN', 'COS', 'TAN'):
        for x in circular:
            num(name, x)

    # Inverse circular on its full domain, including the endpoints, plus outside the domain.
    for name in ('ASIN', 'ACOS'):
        for x in uniform(-1.0, 1.0, 33) + [0.0, 1e-9, -1e-9, 0.5, -0.5]:
            num(name, x)
        err(name, 1.0000001, '#NUM!')
        err(name, -1.5, '#NUM!')

    for x in powers(6) + [0.0, 0.5, -0.5, 1.0, -1.0]:
        num('ATAN', x)

    quadrants = [(1.0, 0.0), (0.0, 1.0), (1.0, 1.0), (-1.0, 1.0), (1.0, -1.0), (-1.0, -1.0), (0.0, -1.0),
                 (-0.0, 1.0), (1e-9, -1e9), (-1e9, -1e-9), (3.0, 4.0), (-3.0, 4.0), (3.0, -4.0), (-3.0, -4.0)]
    for x, y in quadrants:
        num('ATAN2', x, y)
    err('ATAN2', 0.0, '#DIV/0!')

    # Hyperbolic on the shape of its range; COSH above ~710 overflows the double range.
    for name in ('SINH', 'COSH'):
        for x in uniform(-25.0, 25.0, 24) + [0.0, 1e-9, -1e-9, 0.5, -0.5, 1.0, -1.0, 20.0, -20.0]:
            num(name, x)
    err('COSH', 1000.0, '#NUM!')
    for x in uniform(-25.0, 25.0, 24) + [0.0, 1e-9, 1.0, -1.0, 710.0, -710.0]:
        num('TANH', x)
    for x in powers(6) + [0.0, 0.5, -0.5, 1e-300]:
        num('ASINH', x)
    for x in uniform(1.0, 50.0, 10) + [1.0, 1.0000001, 1.5, 2.0, 3.0, 7.0, 10.0, 100.0] + [
        10.0 ** k for k in range(0, 7)
    ]:
        num('ACOSH', x)
    err('ACOSH', 0.9999999, '#NUM!')
    for x in uniform(-0.999, 0.999, 24) + [0.0, 0.5, -0.5, 0.999999, -0.999999]:
        num('ATANH', x)
    err('ATANH', 1.0, '#NUM!')
    err('ATANH', -1.0, '#NUM!')
    err('ATANH', 2.0, '#NUM!')

    out.append(('PI', '', '', ''))

    # A corpus is a set of observations, not a log: a repeated probe would inflate the row count without adding
    # evidence, so the first occurrence of each (function, x, y) wins.
    unique: list[tuple[str, str, str, str]] = []
    seen: set[tuple[str, str, str]] = set()
    for row in out:
        key = (row[0], row[1], row[2])
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)
    return unique


def render() -> str:
    lines = [HEADER]
    for name, x, y, expected in rows():
        if name == 'PI':
            literal = repr(math.pi)
        else:
            literal = expected or _oracle(name, float(x), y)
        lines.append('\t'.join((name, x, y, literal)))
    return '\n'.join(lines) + '\n'


def _oracle(name: str, x: float, y: str) -> str:
    if name == 'SIN':
        return repr(math.sin(x))
    if name == 'COS':
        return repr(math.cos(x))
    if name == 'TAN':
        return repr(math.tan(x))
    if name == 'ASIN':
        return repr(math.asin(x))
    if name == 'ACOS':
        return repr(math.acos(x))
    if name == 'ATAN':
        return repr(math.atan(x))
    if name == 'ATAN2':
        # Spreadsheet order is ATAN2(x_num, y_num); the mathematical convention is atan2(y, x).
        return repr(math.atan2(float(y), x))
    if name == 'SINH':
        return repr(math.sinh(x))
    if name == 'COSH':
        return repr(math.cosh(x))
    if name == 'TANH':
        return repr(math.tanh(x))
    if name == 'ASINH':
        return repr(math.asinh(x))
    if name == 'ACOSH':
        return repr(math.acosh(x))
    if name == 'ATANH':
        return repr(math.atanh(x))
    raise SystemExit(f'no oracle for {name}')


def main() -> int:
    text = render()
    CORPUS.parent.mkdir(parents=True, exist_ok=True)
    CORPUS.write_text(text, encoding='utf-8')
    total = len(text.strip().splitlines()) - 1
    errors = sum(1 for line in text.splitlines()[1:] if line.split('\t')[3].startswith('#'))
    print(f'R116_TRIG_CORPUS_WRITTEN rows={total} error_rows={errors} path={CORPUS.relative_to(ROOT)}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
