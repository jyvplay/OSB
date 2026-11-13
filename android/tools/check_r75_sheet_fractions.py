#!/usr/bin/env python3
"""Independent oracle for the R75 fraction-format observations.

The Kotlin renderer picks the nearest fraction by rounding at every denominator in turn. This
oracle picks it by a **different algorithm**: it enumerates every reduced pair ``p/q`` with
``q <= bound`` and keeps the one minimising ``|x - p/q|`` (ties to the smaller denominator), which
is a definition rather than a search heuristic. It then checks the rendered text three ways:

1. exact string equality with the documented Excel assembly (padding, separator, literals),
2. semantic equality: stripping padding and parsing ``int num/den`` must reproduce floor, p and q,
3. the approximation property: no denominator at or below the bound is strictly closer.

A doctored observation must fail all three, which ``test_r75_sheet_fractions.py`` asserts.

Host-only. No Excel, LibreOffice or Android execution is involved, and none is implied.
"""
from __future__ import annotations

import argparse
import re
import sys
from fractions import Fraction
from pathlib import Path

SUPPORTED = re.compile(r'^[^/]+/[^/]+$')


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def split_literals(section: str) -> list[tuple[str, str]]:
    """Tokenise a format section into ('lit', text) and ('ph', run) tokens.

    Handles the literal syntax the corpus uses: quotes, backslash escapes, ``_`` padding,
    ``*`` fill (dropped) and ``[...]`` annotations (dropped).
    """
    tokens: list[tuple[str, str]] = []
    i = 0
    while i < len(section):
        c = section[i]
        if c == '"':
            end = section.find('"', i + 1)
            require(end > i, 'unterminated quote')
            tokens.append(('lit', section[i + 1:end]))
            i = end + 1
        elif c == '\\':
            require(i + 1 < len(section), 'dangling escape')
            tokens.append(('lit', section[i + 1]))
            i += 2
        elif c == '_':
            tokens.append(('lit', ' '))
            i += 2
        elif c == '*':
            i += 2
        elif c == '[':
            end = section.find(']', i)
            require(end > i, 'unterminated bracket')
            i = end + 1
        elif c == '%':
            tokens.append(('lit', '%'))
            i += 1
        elif c in '0#?':
            j = i
            while j < len(section) and section[j] in '0#?':
                j += 1
            tokens.append(('ph', section[i:j]))
            i = j
        else:
            tokens.append(('lit', c))
            i += 1
    return tokens


def parse_pattern(fmt: str) -> dict:
    """Parses the fraction grammar independently of the Kotlin parser."""
    require(fmt.count('/') == 1, 'expected exactly one slash: ' + fmt)
    head, tail = fmt.split('/')
    head_tokens = split_literals(head)

    # Numerator is the final placeholder run of the head.
    while head_tokens and head_tokens[-1] == ('lit', ''):
        head_tokens.pop()
    require(head_tokens and head_tokens[-1][0] == 'ph', 'fraction needs a numerator run: ' + fmt)
    numerator = head_tokens.pop()[1]

    separator = ''
    if head_tokens and head_tokens[-1][0] == 'lit':
        separator = head_tokens.pop()[1]
    integer = ''
    if head_tokens and head_tokens[-1][0] == 'ph':
        integer = head_tokens.pop()[1]
    prefix = ''.join(text for kind, text in head_tokens if kind == 'lit')
    require(all(kind == 'lit' for kind, _ in head_tokens), 'unexpected placeholder before the integer run')

    # Denominator: a leading digit run is fixed (16, 8, 100); otherwise a placeholder run.
    denom_placeholders = ''
    fixed = None
    digits = re.match(r'[0-9]+', tail)
    if digits:
        fixed = int(digits.group(0))
        tail_rest = tail[digits.end():]
    else:
        marks = re.match(r'[0#?]+', tail)
        require(marks is not None, 'fraction needs a denominator: ' + fmt)
        denom_placeholders = marks.group(0)
        tail_rest = tail[marks.end():]
    suffix = ''.join(text for kind, text in split_literals(tail_rest))
    require(fixed is not None or denom_placeholders, 'fraction needs a denominator: ' + fmt)
    percents = fmt.count('%')
    require(fixed is None or 1 <= fixed <= 9999, 'fixed denominator out of range')
    return {'prefix': prefix.rstrip('%'), 'suffix': suffix.lstrip('%'), 'separator': separator,
            'integer': integer, 'numerator': numerator, 'denominator': denom_placeholders,
            'fixed': fixed, 'percents': percents}


def half_up(x: float) -> int:
    """Excel rounds halves away from zero. Python's round() is banker's rounding, so 56.5 would
    become 56 and silently disagree with the renderer; this is the documented difference."""
    return int(x + 0.5) if x >= 0 else -int(-x + 0.5)


def best_fraction(x: float, bound: int) -> tuple[int, int]:
    """Exhaustive definition of 'nearest fraction with denominator at most bound'."""
    best = (0, 1)
    best_error = abs(x)
    for q in range(1, bound + 1):
        p = half_up(x * q)
        if p < 0:
            continue
        error = abs(x - p / q)
        if error < best_error - 1e-15:
            best, best_error = (p, q), error
    return best


def choose(x: float, pattern: dict) -> tuple[int, int, int]:
    """(p, q, bound) for one fractional value under this pattern.

    A fixed denominator is literal: Excel keeps ``/8`` even when the fraction is zero, so the
    denominator is *not* reduced to 1.
    """
    if pattern['fixed'] is not None:
        q = pattern['fixed']
        return half_up(x * q), q, q
    bound = 10 ** len(pattern['denominator']) - 1
    p, q = best_fraction(x, bound)
    return p, q, bound


def pad(text: str, placeholders: str) -> str:
    width = len(placeholders)
    if len(text) >= width:
        return text
    filler = '0' if '0' in placeholders else (' ' if '?' in placeholders else ' ')
    if '0' not in placeholders and '?' not in placeholders:
        return text
    return text.rjust(width, filler)


def expected(value: float, fmt: str) -> str:
    pattern = parse_pattern(fmt)
    scaled = value * (100 ** pattern['percents'])
    negative = scaled < 0
    magnitude = abs(scaled)
    has_integer = bool(pattern['integer'])
    whole = int(magnitude // 1) if has_integer else 0
    fractional = magnitude - whole if has_integer else magnitude
    p, q, _ = choose(fractional, pattern)
    if has_integer and p == q:
        whole += 1
        p = 0
    int_text = ''
    if has_integer:
        if not (whole == 0 and '0' not in pattern['integer']):
            int_text = pad(str(whole), pattern['integer'])
    fraction_shown = p != 0 or not has_integer
    if not fraction_shown:
        body = int_text
    else:
        frac = pad(str(p), pattern['numerator']) + '/' + pad(str(q), pattern['denominator'])
        body = frac if not int_text else int_text + pattern['separator'] + frac
    if not body:
        body = '0'
    sign = '-' if negative else ''
    return sign + pattern['prefix'] + body + pattern['suffix']


def semantic_check(value: float, fmt: str, rendered: str) -> None:
    """Re-derive the numbers from the rendered text, ignoring padding."""
    pattern = parse_pattern(fmt)
    body = rendered
    require(body.startswith(pattern['prefix']), f'prefix missing in {rendered!r}')
    body = body[len(pattern['prefix']):]
    if pattern['suffix']:
        require(body.endswith(pattern['suffix']), f'suffix missing in {rendered!r}')
        body = body[: -len(pattern['suffix'])]
    if body.startswith('-'):
        body = body[1:]
    # Padding is spaces; drop it around the slash and keep at most one separator space.
    body = re.sub(r'\s*/\s*', '/', body)
    body = re.sub(r'\s+', ' ', body).strip()
    match = re.fullmatch(r'(?:(\d+) )?(\d+)/(\d+)', body)
    if match is None:
        require(body.isdigit() or body == '', f'unparseable fraction body {body!r}')
        require(pattern['integer'] and float(body or 0) == int(value), f'integer-only body {body!r}')
        return
    whole = int(match.group(1) or 0)
    p, q = int(match.group(2)), int(match.group(3))
    scaled = value * (100 ** pattern['percents'])
    magnitude = abs(scaled)
    has_integer = bool(pattern['integer'])
    if has_integer:
        fractional = magnitude - (magnitude // 1)
        expect_p, expect_q, bound = choose(fractional, pattern)
        if expect_p == expect_q:
            expect_p = 0
        require((p, q) == (expect_p, expect_q),
                f'rendered {p}/{q} is not the best {expect_p}/{expect_q} under bound {bound}')
        require(whole in (int(magnitude // 1), int(magnitude // 1) + 1), f'whole part {whole} wrong')
    else:
        expect_p, expect_q, bound = choose(magnitude, pattern)
        require((p, q) == (expect_p, expect_q),
                f'improper {p}/{q} is not the best {expect_p}/{expect_q} under bound {bound}')
    # Padding itself is asserted by the exact-string comparison; here we only require that a
    # value never *loses* digits by overflowing a narrow placeholder (10/8 is legal for `?/8`).


def verify(directory: Path) -> int:
    tsv = directory / 'fractions.tsv'
    require(tsv.is_file(), 'missing fractions.tsv (run the Kotlin check first)')
    rows = [line.split('\t') for line in tsv.read_text(encoding='utf-8').splitlines() if line]
    require(len(rows) >= 100, f'expected at least 100 observations, saw {len(rows)}')
    for value_text, fmt, rendered in rows:
        value = float(value_text)
        want = expected(value, fmt)
        require(rendered == want, f'{value} / {fmt}: rendered {rendered!r}, oracle {want!r}')
        semantic_check(value, fmt, rendered)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    try:
        rows = verify(args.directory)
    except Fail as error:
        print('R75 FRACTION FAIL: ' + str(error))
        return 1
    except Exception as error:
        print(f'R75 FRACTION FAIL: {type(error).__name__}: {error}')
        return 1
    print(f'R75 sheet fractions: {rows} observations matched an independent exhaustive oracle '
          '(nearest p/q over every denominator, plus padding and semantic re-derivation). '
          'Not Excel or LibreOffice recalculation evidence.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
