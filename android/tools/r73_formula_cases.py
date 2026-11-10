#!/usr/bin/env python3
"""Independent R73 oracle for the new spreadsheet functions.

Every expected value is computed here from the published spreadsheet semantics
using the Python standard library (decimal, math, statistics, datetime, re) and
hand-written format/text/dates logic. Nothing reads the Kotlin implementation or
its output, so a disagreement means one of the two implementations is wrong.

Outputs cases.tsv (id, formula) for the Kotlin probe and expected.tsv
(id, kind, value) for the comparator. Host-only; no Excel or LibreOffice here.
"""
from __future__ import annotations

import argparse
import calendar
import datetime as dt
import math
import re
import statistics
from decimal import Decimal, ROUND_UP, ROUND_DOWN, ROUND_HALF_UP
from pathlib import Path

HEADER = ('id', 'formula')

# The evaluation environment is duplicated verbatim in R73FormulaProbe.kt.
NUMBERS = {
    'A1': 3.0, 'A2': -2.0, 'B1': 1.0, 'B2': 2.0, 'B3': 2.0, 'B4': 3.0, 'B5': 3.0, 'B6': 3.0,
    'C1': 10.0, 'C2': 20.0, 'C3': 30.0, 'C4': 40.0, 'C5': 50.0, 'C6': 60.0,
    'E1': 44927.0, 'E2': 44928.0, 'E3': 44957.0, 'E4': 43831.0,
    'E5': 44927.0, 'E6': 44562.0, 'E7': 44197.0, 'E8': 45292.0,
    'F1': 0.75, 'F2': 0.5, 'F3': 0.0000001,
}
TEXTS = {
    'A3': '5', 'A6': 'Hello', 'A7': '  spaced  ', 'A8': '1,234.5',
    'A9': '2023-01-01', 'A10': '50%', 'A11': '(12)',
    'D1': 'a', 'D2': 'b', 'D3': 'b', 'D4': 'c', 'D5': 'c',
    'H1': 'apple', 'H2': 'Apple pie', 'H3': 'a*b', 'H4': 'a?c',
}
BOOLS = {'A4': True, 'G1': False}
BLANKS = {'A5', 'D6'}


def n(value: float) -> tuple[str, str]:
    return ('N', repr(float(value)))


def t(value: str) -> tuple[str, str]:
    return ('T', value)


def b(value: bool) -> tuple[str, str]:
    return ('B', 'true' if value else 'false')


def e(literal: str) -> tuple[str, str]:
    return ('E', literal)


def refusal_reason(fragment: str) -> tuple[str, str]:
    return ('R', fragment)


# ---------------------------------------------------------------- independent formatter
MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
          'September', 'October', 'November', 'December']
WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
EPOCH = dt.date(1899, 12, 30)


def split_sections(fmt: str) -> list[str]:
    out, cur, quote, bracket = [], [], False, False
    i = 0
    while i < len(fmt):
        ch = fmt[i]
        if ch == '\\' and not quote:
            cur.append(ch)
            i += 1
            if i < len(fmt):
                cur.append(fmt[i])
        elif ch == '"':
            quote = not quote
            cur.append(ch)
        elif ch == '[' and not quote:
            bracket = True
            cur.append(ch)
        elif ch == ']' and not quote:
            bracket = False
            cur.append(ch)
        elif ch == ';' and not quote and not bracket:
            out.append(''.join(cur))
            cur = []
        else:
            cur.append(ch)
        i += 1
    out.append(''.join(cur))
    return out


def strip_colour(section: str) -> str:
    while section.startswith('['):
        end = section.index(']')
        inner = section[1:end]
        if not inner.isalpha():
            raise ValueError('condition')
        section = section[end + 1:]
    return section


def literal_only(section: str) -> str:
    out, i = [], 0
    while i < len(section):
        ch = section[i]
        if ch == '"':
            end = section.index('"', i + 1)
            out.append(section[i + 1:end])
            i = end + 1
        elif ch == '\\':
            out.append(section[i + 1])
            i += 2
        elif ch == '_':
            out.append(' ')
            i += 2
        elif ch == '*':
            i += 2
        elif ch == '[':
            i = section.index(']', i) + 1
        else:
            out.append(ch)
            i += 1
    return ''.join(out)


def is_date(fmt: str) -> bool:
    stripped = re.sub(r'"[^"]*"', '', fmt)
    stripped = re.sub(r'\\.', '', stripped)
    if re.search(r'[yd]', stripped, re.I):
        return True
    return bool(re.search(r'[hs]', stripped, re.I)) and bool(re.search(r'h.*[ms]|[ms]', stripped, re.I))


def choose_section(sections: list[str], value: float) -> str:
    if len(sections) == 1:
        return sections[0]
    if len(sections) == 2:
        return sections[1] if value < 0 else sections[0]
    if value > 0:
        return sections[0]
    if value < 0:
        return sections[1]
    return sections[2]


def general(value: float) -> str:
    if value == int(value) and abs(value) < 1e15:
        return str(int(value))
    return repr(float(value))


def format_date(value: float, fmt: str) -> str:
    day = math.floor(value)
    if day < 0:
        raise ValueError('serial')
    needs_civil = bool(re.search(r'[yd]', fmt, re.I))
    if needs_civil and day < 61:
        raise ValueError('serial window')
    date = EPOCH + dt.timedelta(days=int(day))
    seconds = round((value - day) * 86400)
    if seconds >= 86400:
        date += dt.timedelta(days=1)
        seconds -= 86400
    hour, minute, second = seconds // 3600, (seconds % 3600) // 60, seconds % 60
    twelve = bool(re.search(r'AM/PM|A/P', fmt, re.I))
    out, i, previous = [], 0, None
    while i < len(fmt):
        ch = fmt[i]
        low = ch.lower()
        if ch == '"':
            end = fmt.index('"', i + 1)
            out.append(fmt[i + 1:end]); i = end + 1
        elif ch == '\\':
            out.append(fmt[i + 1]); i += 2
        elif ch == '[':
            end = fmt.index(']', i)
            inner = fmt[i + 1:end].lower()
            total = int(day * 86400) + seconds
            unit = inner[0]
            out.append(str(total // 3600 if unit == 'h' else total // 60 if unit == 'm' else total).zfill(len(inner)))
            previous = unit
            i = end + 1
        elif low in 'yd':
            run = 1
            while i + run < len(fmt) and fmt[i + run].lower() == low:
                run += 1
            if low == 'y':
                out.append(str(date.year) if run >= 3 else f'{date.year % 100:02d}')
            else:
                out.append(str(date.day) if run == 1 else f'{date.day:02d}' if run == 2
                           else WEEKDAYS[date.weekday()][:3] if run == 3 else WEEKDAYS[date.weekday()])
            i += run
        elif low == 'm':
            run = 1
            while i + run < len(fmt) and fmt[i + run].lower() == 'm':
                run += 1
            rest = fmt[i + run:]
            as_minute = previous == 'h' or rest.lower().startswith('s')
            if as_minute:
                out.append(str(minute) if run == 1 else str(minute).zfill(run))
                previous = 'm'
            else:
                out.append(str(date.month) if run == 1 else f'{date.month:02d}' if run == 2
                           else MONTHS[date.month - 1][:3] if run == 3 else MONTHS[date.month - 1])
            i += run
        elif low == 'h':
            run = 1
            while i + run < len(fmt) and fmt[i + run].lower() == 'h':
                run += 1
            shown = ((hour + 11) % 12) + 1 if twelve else hour
            out.append(str(shown) if run == 1 else str(shown).zfill(run))
            previous = 'h'
            i += run
        elif low == 's':
            run = 1
            while i + run < len(fmt) and fmt[i + run].lower() == 's':
                run += 1
            out.append(str(second) if run == 1 else str(second).zfill(run))
            previous = 's'
            i += run
        elif fmt[i:i + 5].upper() == 'AM/PM':
            out.append('AM' if hour < 12 else 'PM'); i += 5
        elif fmt[i:i + 3].upper() == 'A/P':
            out.append('A' if hour < 12 else 'P'); i += 3
        else:
            out.append(ch); i += 1
    return ''.join(out)


def parse_number_pattern(section: str):
    prefix, suffix = [], []
    integer, fraction = [], []
    seen = False
    point = False
    grouping = False
    scale = 0
    percents = 0
    exponent = None
    exponent_digits = 0
    i = 0

    def literal(text: str):
        (suffix if seen else prefix).append(text)

    while i < len(section):
        ch = section[i]
        if ch == '"':
            end = section.index('"', i + 1)
            literal(section[i + 1:end]); i = end + 1
        elif ch == '\\':
            literal(section[i + 1]); i += 2
        elif ch == '_':
            literal(' '); i += 2
        elif ch == '*':
            i += 2
        elif ch == '[':
            i = section.index(']', i) + 1
        elif ch == '%':
            percents += 1; literal('%'); i += 1
        elif ch in '0#?':
            seen = True
            if exponent is not None:
                exponent_digits += 1
            elif point:
                fraction.append(ch)
            else:
                integer.append(ch)
            i += 1
        elif ch == '.':
            if point or not seen:
                raise ValueError('decimal point')
            point = True; i += 1
        elif ch == ',':
            if not seen or point:
                literal(','); i += 1
            else:
                commas = 0
                j = i
                while j < len(section) and section[j] == ',':
                    commas += 1; j += 1
                if j < len(section) and section[j] in '0#?':
                    grouping = True
                else:
                    scale += commas
                i = j
        elif ch in 'Ee':
            if exponent is not None or not seen:
                raise ValueError('exponent')
            if section[i + 1] not in '+-':
                raise ValueError('exponent sign')
            exponent = section[i + 1]; i += 2
        elif ch in '/@':
            raise ValueError('unsupported token')
        else:
            literal(ch); i += 1
    if not seen:
        raise ValueError('no placeholders')
    return dict(prefix=''.join(prefix), suffix=''.join(suffix), integer=''.join(integer),
                fraction=''.join(fraction), grouping=grouping, scale=scale, percents=percents,
                exponent=exponent, exponent_digits=exponent_digits)


def group_digits(digits: str) -> str:
    if len(digits) <= 3:
        return digits
    parts = []
    while len(digits) > 3:
        parts.insert(0, digits[-3:])
        digits = digits[:-3]
    parts.insert(0, digits)
    return ','.join(parts)


def fixed_part(value: Decimal, pattern) -> str:
    magnitude = abs(value)
    quant = Decimal(1).scaleb(-len(pattern['fraction']))
    rounded = magnitude.quantize(quant, rounding=ROUND_HALF_UP)
    text = format(rounded, 'f')
    int_part, _, frac_part = text.partition('.')
    minimum = pattern['integer'].count('0')
    if int_part == '0' and minimum == 0 and '0' not in pattern['integer']:
        int_part = ''
    else:
        int_part = int_part.lstrip('0') or '0'
        if len(int_part) < minimum:
            int_part = int_part.zfill(minimum)
        if pattern['grouping']:
            int_part = group_digits(int_part)
    frac = []
    last = -1
    for idx, placeholder in enumerate(pattern['fraction']):
        digit = frac_part[idx] if idx < len(frac_part) else '0'
        frac.append(digit)
        if placeholder in '0#' and digit != '0':
            last = idx
        elif placeholder == '0':
            last = idx
    kept = []
    for idx, placeholder in enumerate(pattern['fraction']):
        if idx > last and placeholder in '#?':
            continue
        kept.append(' ' if placeholder == '?' and frac[idx] == '0' else frac[idx])
    frac_text = ''.join(kept)
    if not frac_text:
        return int_part if int_part else '0'
    return f'{int_part}.{frac_text}' if int_part else f'.{frac_text}'


def scientific_part(value: Decimal, pattern) -> str:
    magnitude = abs(value)
    exponent = 0
    if magnitude != 0:
        while magnitude >= 10:
            magnitude /= 10
            exponent += 1
        while magnitude < 1:
            magnitude *= 10
            exponent -= 1
    quant = Decimal(1).scaleb(-len(pattern['fraction']))
    magnitude = magnitude.quantize(quant, rounding=ROUND_HALF_UP)
    if magnitude >= 10:
        magnitude /= 10
        exponent += 1
    esign = ''
    if pattern['exponent'] == '+':
        esign = '+' if exponent >= 0 else '-'
    elif pattern['exponent'] == '-' and exponent < 0:
        esign = '-'
    return f"{format(magnitude, 'f')}E{esign}{abs(exponent):0{pattern['exponent_digits']}d}"


def render_number(value: float, fmt: str) -> str:
    sections = [strip_colour(s) for s in split_sections(fmt)]
    section = choose_section(sections, value)
    if not section or section.lower() == 'general':
        return general(value)
    if is_date(section):
        return format_date(value, section)
    if not re.search(r'(?<!["\\])[0#?]', section):
        return literal_only(section)
    pattern = parse_number_pattern(section)
    scaled = Decimal(repr(value))
    for _ in range(pattern['percents']):
        scaled *= 100
    for _ in range(pattern['scale']):
        scaled /= 1000
    if pattern['exponent']:
        body = scientific_part(scaled, pattern)
    else:
        body = fixed_part(scaled, pattern)
    if len(sections) == 1 and scaled < 0:
        return '-' + pattern['prefix'] + body + pattern['suffix']
    return pattern['prefix'] + body + pattern['suffix']


def render_text(value: str, fmt: str) -> str:
    sections = [strip_colour(s) for s in split_sections(fmt)]
    if len(sections) == 4:
        section = sections[3]
        if not section:
            return value
        return literal_only(section).replace('@', value) if '@' in section else literal_only(section)
    section = sections[0]
    if not section or section.lower() == 'general' or section == '@':
        return value
    raise ValueError('date format on text')


# ---------------------------------------------------------------- independent functions
def excel_round(value: float, digits: int, *, up: bool, down: bool) -> float:
    quant = Decimal(1).scaleb(-digits)
    mode = ROUND_UP if up else ROUND_DOWN
    base = Decimal(repr(value)).quantize(Decimal(1), rounding=ROUND_HALF_UP)
    if abs(value) < 1e15:
        base = Decimal(repr(value))
    rounded = base.quantize(quant, rounding=mode)
    return float(rounded)


def serial_to_date(serial: float) -> dt.date:
    return EPOCH + dt.timedelta(days=int(serial))


def date_to_serial(date: dt.date) -> int:
    return (date - EPOCH).days


def days360(start: dt.date, end: dt.date, european: bool) -> int:
    """SpreadsheetML DAYS360 with the NASD (US) and European end-of-month rules."""
    d1, d2 = start.day, end.day
    if european:
        d1, d2 = min(d1, 30), min(d2, 30)
    else:
        start_last = start.day == calendar.monthrange(start.year, start.month)[1]
        if start_last:
            d1 = 30
        end_last = end.day == calendar.monthrange(end.year, end.month)[1]
        if end_last and d1 < 30:
            # The ending date becomes the first day of the following month.
            if end.month == 12:
                end = dt.date(end.year + 1, 1, 1)
            else:
                end = dt.date(end.year, end.month + 1, 1)
            d2 = 1
        elif end_last or d2 == 31:
            d2 = 30
    return (end.year - start.year) * 360 + (end.month - start.month) * 30 + (d2 - d1)


def year_frac(start: dt.date, end: dt.date, basis: int) -> float:
    actual = (end - start).days
    if basis == 0:
        return days360(start, end, False) / 360
    if basis == 4:
        return days360(start, end, True) / 360
    if basis == 2:
        return actual / 360
    if basis == 3:
        return actual / 365
    if start.year == end.year:
        days = 366 if (start.year % 4 == 0 and (start.year % 100 != 0 or start.year % 400 == 0)) else 365
        return actual / days
    span_start = dt.date(start.year, 1, 1)
    span_end = dt.date(end.year + 1, 1, 1)
    years = end.year - start.year + 1
    return actual / ((span_end - span_start).days / years)


def week_number(date: dt.date, kind: int) -> int:
    if kind == 21:
        return date.isocalendar()[1]
    first = {1: 6, 17: 6, 2: 0, 11: 0, 12: 1, 13: 2, 14: 3, 15: 4, 16: 5}[kind]
    jan1 = dt.date(date.year, 1, 1)
    offset = (jan1.weekday() - first) % 7
    return (date.timetuple().tm_yday + offset - 1) // 7 + 1


def wildcard(pattern: str) -> re.Pattern:
    out, i = [], 0
    while i < len(pattern):
        ch = pattern[i]
        if ch == '~':
            i += 1
            out.append(re.escape(pattern[i]))
        elif ch == '*':
            out.append('.*')
        elif ch == '?':
            out.append('.')
        else:
            out.append(re.escape(ch))
        i += 1
    return re.compile(''.join(out), re.IGNORECASE)


def parse_date_text(text: str) -> dt.date | None:
    text = text.strip()
    for pattern, order in ((r'^(\d{4})-(\d{1,2})-(\d{1,2})$', 'ymd'),
                           (r'^(\d{1,2})/(\d{1,2})/(\d{2,4})$', 'mdy'),
                           (r'^(\d{1,2})-([A-Za-z]{3,9})-(\d{2,4})$', 'dMy'),
                           (r'^([A-Za-z]{3,9})\s+(\d{1,2}),?\s+(\d{4})$', 'Mdy')):
        match = re.match(pattern, text)
        if not match:
            continue
        groups = match.groups()
        try:
            if order == 'ymd':
                return dt.date(int(groups[0]), int(groups[1]), int(groups[2]))
            if order == 'mdy':
                return dt.date(expand(int(groups[2])), int(groups[0]), int(groups[1]))
            if order == 'dMy':
                return dt.date(expand(int(groups[2])), month_of(groups[1]), int(groups[0]))
            return dt.date(int(groups[2]), month_of(groups[0]), int(groups[1]))
        except ValueError:
            return None
    return None


def expand(year: int) -> int:
    if year >= 100:
        return year
    return 2000 + year if year <= 29 else 1900 + year


def month_of(name: str) -> int:
    for index, month in enumerate(MONTHS):
        if month.lower().startswith(name.lower()[:3]):
            return index + 1
    raise ValueError(name)


def parse_value_text(text: str) -> float | None:
    raw = text.strip()
    if not raw:
        return None
    negative = False
    body = raw
    if body.startswith('(') and body.endswith(')'):
        negative = True
        body = body[1:-1].strip()
    percent = 0
    while body.endswith('%'):
        percent += 1
        body = body[:-1].strip()
    for symbol in ('$', '€', '£', '¥'):
        if body.startswith(symbol):
            body = body[len(symbol):].strip()
    if body.startswith('-'):
        negative = not negative
        body = body[1:].strip()
    elif body.startswith('+'):
        body = body[1:].strip()
    if not body:
        return None
    if re.fullmatch(r'\d{1,3}(,\d{3})+(\.\d+)?', body):
        body = body.replace(',', '')
    try:
        value = float(body)
    except ValueError:
        date = parse_date_text(raw)
        if date is None:
            return None
        value = date_to_serial(date)
        if value < 61:
            return None
    for _ in range(percent):
        value /= 100
    return -value if negative else value


def norm_inv(p: float, mean: float, sd: float) -> float:
    return mean + sd * statistics.NormalDist().inv_cdf(p)


def binom_inv(trials: int, p: float, alpha: float) -> int:
    cumulative = 0.0
    for k in range(trials + 1):
        cumulative += math.comb(trials, k) * (p ** k) * ((1 - p) ** (trials - k))
        if cumulative >= alpha - 1e-15:
            return k
    return trials


def formula_cases():
    """Yields (id, formula, expected, tolerance) tuples."""
    out = []

    def add(case_id, formula, expected, tol=0.0):
        out.append((case_id, formula, expected, tol))

    # Rounding family
    for value, digits in ((2.675, 2), (-2.675, 2), (1.004, 2), (-1.004, 2), (15.0, -1), (-15.0, -1), (0.5, 0), (-0.5, 0)):
        add(f'roundup-{value}-{digits}', f'=ROUNDUP({value},{digits})',
            n(excel_round(value, digits, up=True, down=False)))
        add(f'rounddown-{value}-{digits}', f'=ROUNDDOWN({value},{digits})',
            n(excel_round(value, digits, up=False, down=True)))
        add(f'trunc-{value}-{digits}', f'=TRUNC({value},{digits})',
            n(excel_round(value, digits, up=False, down=True)))
    add('roundup-zero-digits', '=ROUNDUP(3.2)', n(4.0))
    add('trunc-one-arg', '=TRUNC(3.9)', n(3.0))

    # LOG / PRODUCT
    for value, base in ((8.0, 2.0), (100.0, 10.0), (2.718281828459045, 2.718281828459045), (0.5, 2.0)):
        add(f'log-{value}-{base}', f'=LOG({value},{base})', n(math.log(value, base)), 1e-12)
    add('log-default', '=LOG(1000)', n(3.0), 1e-12)
    add('log-negative', '=LOG(-1)', e('#NUM!'))
    add('log-base-one', '=LOG(8,1)', e('#DIV/0!'))
    add('product-range', '=PRODUCT(B1:B6)', n(108.0))
    add('product-mixed', '=PRODUCT(A1:A2)', n(-6.0))
    add('product-empty', '=PRODUCT()', e('#VALUE!'))

    # Text functions
    add('rept-basic', '=REPT("ab",3)', t('ababab'))
    add('rept-zero', '=REPT("x",0)', t(''))
    add('rept-negative', '=REPT("x",-1)', e('#VALUE!'))
    add('replace-basic', '=REPLACE("abcdef",2,3,"XY")', t('aXYef'))
    add('replace-start-one', '=REPLACE("abc",1,0,"Z")', t('Zabc'))
    add('replace-beyond', '=REPLACE("abc",9,2,"Z")', t('abcZ'))
    add('replace-bad-start', '=REPLACE("abc",0,1,"Z")', e('#VALUE!'))
    add('search-basic', '=SEARCH("b","abc")', n(2.0))
    add('search-case', '=SEARCH("B","abc")', n(2.0))
    add('search-start', '=SEARCH("b","abcb",3)', n(4.0))
    add('search-wild-star', '=SEARCH("a*c","abc")', n(1.0))
    add('search-wild-question', '=SEARCH("a?c","abc")', n(1.0))
    add('search-literal-star', '=SEARCH("a~*b","a*b")', n(1.0))
    add('search-missing', '=SEARCH("z","abc")', e('#VALUE!'))
    add('search-empty', '=SEARCH("","abc",2)', n(2.0))

    # VALUE
    add('value-plain', '=VALUE("123.5")', n(123.5))
    add('value-grouped', '=VALUE(A8)', n(1234.5))
    add('value-percent', '=VALUE(A10)', n(0.5))
    add('value-currency', '=VALUE("$1,000")', n(1000.0))
    add('value-parentheses', '=VALUE(A11)', n(-12.0))
    add('value-date', '=VALUE(A9)', n(44927.0))
    add('value-text', '=VALUE("abc")', e('#VALUE!'))
    add('value-number', '=VALUE(A1)', n(3.0))

    # TEXT rendering
    for value, fmt in ((1.5, '0.00'), (-1.5, '0.00'), (1234567.891, '#,##0.00'), (0.256, '0.0%'),
                       (12345678.0, '#,##0,,"M"'), (-1234.5, '$#,##0.00'), (5.0, '000'),
                       (0.5, '#.##'), (1234.0, '0.00E+00'), (-1234.5, '0.0;(0.0)'),
                       (0.0, '0.0;-0.0;"zero"'), (44927.0, 'yyyy-mm-dd'),
                       (44927.5, 'dddd, mmmm d, yyyy h:mm AM/PM'), (0.75, 'hh:mm:ss'), (2.0, '0.00')):
        add(f'text-case-{len([c for c in out if c[0].startswith("text-case")]) + 1}',
            f'=TEXT({value},"{fmt.replace(chr(34), chr(34) * 2)}")', t(render_number(value, fmt)))
    add('text-text-value', '=TEXT("abc","0.00")', t('abc'))
    add('text-at-format', '=TEXT("abc","@")', t('abc'))
    add('text-bad-format', '=TEXT(1,"0.00/")', e('#VALUE!'))

    # Time and date
    add('time-basic', '=TIME(12,30,15)', n((12 * 3600 + 30 * 60 + 15) / 86400), 1e-12)
    add('time-wrap', '=TIME(25,0,0)', n((25 * 3600 % 86400) / 86400), 1e-12)
    add('time-negative', '=TIME(-1,0,0)', e('#NUM!'))
    add('hour', '=HOUR(F1)', n(18.0))
    add('minute', '=MINUTE(F1)', n(0.0))
    add('second-fraction', '=SECOND(0.5000115740740741)', n(1.0))
    add('hour-date-part', '=HOUR(E1+0.75)', n(18.0))
    add('hour-negative', '=HOUR(-0.5)', e('#NUM!'))
    add('datevalue-iso', '=DATEVALUE("2023-01-01")', n(44927.0))
    add('datevalue-slash', '=DATEVALUE("1/31/2023")', n(44957.0))
    add('datevalue-named', '=DATEVALUE("March 15, 2020")', n(date_to_serial(dt.date(2020, 3, 15))))
    add('datevalue-bad', '=DATEVALUE("not a date")', e('#VALUE!'))
    add('days-basic', '=DAYS(E3,E1)', n(30.0))
    add('days-negative', '=DAYS(E1,E3)', n(-30.0))
    for european, expected in ((False, days360(dt.date(2023, 1, 1), dt.date(2023, 1, 31), False)),
                                (True, days360(dt.date(2023, 1, 1), dt.date(2023, 1, 31), True))):
        add(f'days360-{european}', f'=DAYS360(E1,E3,{"TRUE" if european else "FALSE"})', n(float(expected)))
    add('days360-default', '=DAYS360(E1,E3)', n(float(days360(dt.date(2023, 1, 1), dt.date(2023, 1, 31), False))))
    for basis in range(5):
        add(f'yearfrac-{basis}', f'=YEARFRAC(E1,E3,{basis})',
            n(year_frac(dt.date(2023, 1, 1), dt.date(2023, 1, 31), basis)), 1e-12)
    add('yearfrac-leap', '=YEARFRAC(DATE(2020,1,1),DATE(2020,7,1),1)', n(182 / 366), 1e-12)
    add('yearfrac-multi', '=YEARFRAC(DATE(2019,7,1),DATE(2020,7,1),1)', n(366 / 365.5), 1e-9)
    add('yearfrac-bad-basis', '=YEARFRAC(E1,E3,5)', e('#NUM!'))
    for kind in (1, 2, 11, 12, 13, 14, 15, 16, 17, 21):
        add(f'weeknum-{kind}', f'=WEEKNUM(44957,{kind})', n(float(week_number(dt.date(2023, 1, 31), kind))))
    add('weeknum-iso-jan1', '=WEEKNUM(E1,21)', n(float(week_number(dt.date(2023, 1, 1), 21))))
    add('weeknum-bad-type', '=WEEKNUM(E1,5)', e('#NUM!'))

    # Information
    add('isna-true', '=ISNA(NA())', b(True))
    add('isna-false', '=ISNA(1/0)', b(False))
    add('isna-value', '=ISNA(A1)', b(False))
    add('nonttext', '=ISNONTEXT(A1)', b(True))
    add('isntext-text', '=ISNONTEXT(A6)', b(False))
    add('iseven', '=ISEVEN(4)', b(True))
    add('iseven-odd', '=ISEVEN(3)', b(False))
    add('isodd-negative', '=ISODD(-3)', b(True))
    add('na-literal', '=NA()', e('#N/A'))
    add('error-type-div0', '=ERROR.TYPE(1/0)', n(2.0))
    add('error-type-na', '=ERROR.TYPE(NA())', n(7.0))
    add('error-type-value', '=ERROR.TYPE("a"+1)', n(3.0))
    add('error-type-not-error', '=ERROR.TYPE(1)', e('#N/A'))
    add('type-number', '=TYPE(1)', n(1.0))
    add('type-text', '=TYPE("a")', n(2.0))
    add('type-bool', '=TYPE(TRUE)', n(4.0))
    add('type-error', '=TYPE(1/0)', n(16.0))
    add('type-range', '=TYPE(A1:A3)', n(64.0))
    add('countblank', '=COUNTBLANK(A1:A9)', n(1.0))
    add('countblank-empty-text', '=COUNTBLANK(A1:A6)', n(1.0))

    # Statistics
    samples = [NUMBERS[f'B{i}'] for i in range(1, 7)]
    add('stdev-s', '=STDEV.S(B1:B6)', n(statistics.stdev(samples)), 1e-12)
    add('stdev-p', '=STDEV.P(B1:B6)', n(statistics.pstdev(samples)), 1e-12)
    add('var-s', '=VAR.S(B1:B6)', n(statistics.variance(samples)), 1e-12)
    add('var-p', '=VAR.P(B1:B6)', n(statistics.pvariance(samples)), 1e-12)
    add('stdev-s-single', '=STDEV.S(A1)', e('#DIV/0!'))
    add('var-p-single', '=VAR.P(A1)', n(0.0))
    add('norm-inv', '=NORM.INV(0.95,0,1)', n(norm_inv(0.95, 0, 1)), 2e-9)
    add('norm-inv-mean', '=NORM.INV(0.5,10,2)', n(norm_inv(0.5, 10, 2)), 1e-9)
    add('norm-inv-bad-p', '=NORM.INV(1,0,1)', e('#NUM!'))
    add('norm-inv-bad-sd', '=NORM.INV(0.5,0,0)', e('#NUM!'))
    add('binom-inv-half', '=BINOM.INV(10,0.5,0.5)', n(float(binom_inv(10, 0.5, 0.5))))
    add('binom-inv-zero', '=BINOM.INV(10,0.5,0)', n(0.0))
    add('binom-inv-bad', '=BINOM.INV(10,1.5,0.5)', e('#NUM!'))
    add('mode-basic', '=MODE.SNGL(B1:B6)', n(3.0))
    add('mode-none', '=MODE.SNGL(1,2,3)', e('#N/A'))
    add('mode-single-arg', '=MODE.SNGL(2)', e('#N/A'))

    # Criteria aggregates
    rows = {f'D{i}': TEXTS.get(f'D{i}') for i in range(1, 7)}
    c_values = {f'C{i}': NUMBERS[f'C{i}'] for i in range(1, 7)}
    b_values = {f'B{i}': NUMBERS[f'B{i}'] for i in range(1, 7)}
    add('maxifs-text', '=MAXIFS(C1:C6,D1:D6,"b")',
        n(max(c_values[f'C{i}'] for i in range(1, 7) if rows[f'D{i}'] == 'b')))
    add('minifs-number', '=MINIFS(C1:C6,B1:B6,2)',
        n(min(c_values[f'C{i}'] for i in range(1, 7) if b_values[f'B{i}'] == 2)))
    add('maxifs-compare', '=MAXIFS(C1:C6,B1:B6,">2")',
        n(max(c_values[f'C{i}'] for i in range(1, 7) if b_values[f'B{i}'] > 2)))
    add('minifs-none', '=MINIFS(C1:C6,D1:D6,"zzz")', n(0.0))

    # LOOKUP over an ascending vector
    lookup_vec = [NUMBERS[f'B{i}'] for i in range(1, 7)]
    result_vec = [NUMBERS[f'C{i}'] for i in range(1, 7)]
    for needle in (2.0, 3.0, 0.5, 1.0):
        chosen = -1
        for index, value in enumerate(lookup_vec):
            if value <= needle:
                chosen = index
            else:
                break
        expected = e('#N/A') if chosen < 0 else n(result_vec[chosen])
        add(f'lookup-{needle}', f'=LOOKUP({needle},B1:B6,C1:C6)', expected)
    add('lookup-two-args', '=LOOKUP(2,B1:B6)', n(2.0))
    add('lookup-text-vector', '=LOOKUP("b",D1:D5,C1:C5)', n(30.0))
    add('lookup-shape-mismatch', '=LOOKUP(2,B1:B6,C1:C5)', e('#N/A'))

    # ---- extended rounding, text, date and information coverage ----
    for value in (0.1, -0.1, 1234.5678, -1234.5678, 9.999, 0.00049):
        for digits in (0, 1, 3, -2):
            add(f'roundup-x-{value}-{digits}', f'=ROUNDUP({value},{digits})',
                n(excel_round(value, digits, up=True, down=False)))
            add(f'rounddown-x-{value}-{digits}', f'=ROUNDDOWN({value},{digits})',
                n(excel_round(value, digits, up=False, down=True)))
    add('log-one', '=LOG(1)', n(0.0))
    add('log-below-one', '=LOG(0.5,2)', n(-1.0), 1e-12)
    add('log-text', '=LOG("abc",2)', e('#VALUE!'))
    add('product-ignores-text', '=PRODUCT(A1:A3)', n(-6.0))
    add('rept-boundary-ok', '=REPT("ab",16383)', t('ab' * 16383))
    add('rept-boundary-over', '=REPT("ab",16384)', e('#VALUE!'))
    add('replace-unicode', '=REPLACE("héllo",2,1,"e")', t('hello'))
    add('replace-at-end', '=REPLACE("abc",4,0,"d")', t('abcd'))
    add('replace-remove-all', '=REPLACE("abc",1,3,"")', t(''))
    add('search-unicode', '=SEARCH("é","café")', n(4.0))
    add('search-tilde-question', '=SEARCH("a~?c","a?c")', n(1.0))
    add('search-start-past', '=SEARCH("a","abc",9)', e('#VALUE!'))
    add('search-start-zero', '=SEARCH("a","abc",0)', e('#VALUE!'))
    add('value-spaces', '=VALUE("  12  ")', n(12.0))
    add('value-plus', '=VALUE("+5")', n(5.0))
    add('value-scientific', '=VALUE("1e3")', n(1000.0))
    add('value-double-percent', '=VALUE("50%%")', n(0.005))
    add('value-bad-grouping', '=VALUE("1,23")', e('#VALUE!'))
    add('value-empty', '=VALUE("")', e('#VALUE!'))
    for value, fmt in ((1.0, '0.000'), (12345.0, '#,##0'), (-1234.5, '$#,##0;($#,##0)'),
                       (0.5, '0%'), (1234.0, '0.00E-00'), (44927.0, 'mmm-yy'),
                       (44927.0, 'd/m/yyyy'), (0.75, 'hh:mm'), (1.5, '[h]:mm'),
                       (0.0, '0.0;;'), (3.0, 'General'), (7.0, '0'), (1234567.0, '#,###'),
                       (42.0, '00000'), (-0.6, '0.0'), (123456.789, '#,##0.0')):
        add(f'text-x-{len([c for c in out if c[0].startswith("text-x")]) + 1}',
            f'=TEXT({value},"{fmt.replace(chr(34), chr(34) * 2)}")', t(render_number(value, fmt)))
    add('text-nested-number', '=TEXT(ROUNDUP(2.1,0),"0.0")', t('3.0'))
    add('datevalue-leap', '=DATEVALUE("2020-02-29")', n(date_to_serial(dt.date(2020, 2, 29))))
    add('datevalue-invalid-leap', '=DATEVALUE("2023-02-29")', e('#VALUE!'))
    add('datevalue-us', '=DATEVALUE("12/31/1999")', n(date_to_serial(dt.date(1999, 12, 31))))
    add('datevalue-dash-name', '=DATEVALUE("5-Mar-2020")', n(date_to_serial(dt.date(2020, 3, 5))))
    add('days-fractional', '=DAYS(44927.9,44927.1)', n(0.0))
    for start, end, european in ((dt.date(2022, 12, 31), dt.date(2023, 1, 31), False),
                                  (dt.date(2023, 1, 31), dt.date(2023, 3, 31), False),
                                  (dt.date(2023, 1, 31), dt.date(2023, 3, 31), True),
                                  (dt.date(2023, 2, 28), dt.date(2023, 3, 31), False)):
        start_serial, end_serial = date_to_serial(start), date_to_serial(end)
        add(f'days360-x-{start}-{end}-{european}',
            f'=DAYS360({start_serial},{end_serial},{"TRUE" if european else "FALSE"})',
            n(float(days360(start, end, european))))
    for basis, start, end in ((1, dt.date(2024, 1, 1), dt.date(2024, 7, 1)),
                               (1, dt.date(2023, 7, 1), dt.date(2023, 12, 31)),
                               (2, dt.date(2022, 1, 1), dt.date(2023, 1, 1)),
                               (3, dt.date(2022, 1, 1), dt.date(2023, 1, 1)),
                               (0, dt.date(2022, 12, 31), dt.date(2023, 1, 31))):
        add(f'yearfrac-x-{basis}-{start}-{end}',
            f'=YEARFRAC({date_to_serial(start)},{date_to_serial(end)},{basis})',
            n(year_frac(start, end, basis)), 1e-9)
    for serial, date in ((44562.0, dt.date(2022, 1, 1)), (44197.0, dt.date(2021, 1, 1)),
                          (45292.0, dt.date(2024, 1, 1)), (44927.0, dt.date(2023, 1, 1))):
        for kind in (1, 2, 11, 17, 21):
            add(f'weeknum-x-{serial}-{kind}', f'=WEEKNUM({serial},{kind})',
                n(float(week_number(date, kind))))
    add('isna-nested', '=ISNA(NA())', b(True))
    add('error-type-num', '=ERROR.TYPE(SQRT(-1))', n(6.0))
    add('type-empty-ref', '=TYPE(Z99)', n(1.0))
    add('countblank-two-ranges', '=COUNTBLANK(A1:A9,B1:B6)', n(1.0))
    add('stdev-s-text-range', '=STDEV.S(A1:A4)', n(statistics.stdev([3.0, -2.0])), 1e-12)
    add('var-s-text-range', '=VAR.S(A1:A4)', n(statistics.variance([3.0, -2.0])), 1e-12)
    for p_value in (0.001, 0.025, 0.5, 0.975, 0.999):
        add(f'norm-inv-x-{p_value}', f'=NORM.INV({p_value},0,1)', n(norm_inv(p_value, 0, 1)), 2e-9)
    add('norm-inv-scaled', '=NORM.INV(0.9,100,15)', n(norm_inv(0.9, 100, 15)), 1e-8)
    add('norm-inv-text', '=NORM.INV(0.5,0,"x")', e('#VALUE!'))
    for trials, p_value, alpha in ((10, 0.5, 0.01), (10, 0.5, 0.99), (5, 0.2, 0.5), (20, 0.7, 0.9)):
        add(f'binom-inv-x-{trials}-{p_value}-{alpha}', f'=BINOM.INV({trials},{p_value},{alpha})',
            n(float(binom_inv(trials, p_value, alpha))))
    add('binom-inv-p0', '=BINOM.INV(10,0,0.5)', n(0.0))
    add('binom-inv-p1', '=BINOM.INV(10,1,0.5)', n(10.0))
    add('mode-unique', '=MODE.SNGL(C1:C6)', e('#N/A'))
    add('maxifs-multi', '=MAXIFS(C1:C6,B1:B6,">1",D1:D6,"c")',
        n(max(c_values[f'C{i}'] for i in range(1, 7) if b_values[f'B{i}'] > 1 and rows[f'D{i}'] == 'c')))
    add('minifs-wildcard', '=MINIFS(C1:C6,D1:D6,"*")',
        n(min(c_values[f'C{i}'] for i in range(1, 7) if rows[f'D{i}'] is not None)))
    add('maxifs-blank-criteria', '=MAXIFS(C1:C6,D1:D6,"")', n(60.0))
    add('lookup-text-exact', '=LOOKUP("b",D1:D5,C1:C5)', n(30.0))
    add('lookup-text-between', '=LOOKUP("bb",D1:D5,C1:C5)', n(30.0))
    add('lookup-text-above', '=LOOKUP("d",D1:D5,C1:C5)', n(50.0))
    add('lookup-text-below', '=LOOKUP("0",D1:D5,C1:C5)', e('#N/A'))
    add('composition-roundup', '=ROUNDUP(A1,0)*2', n(6.0))
    add('composition-text-concat', '=TEXT(1.5,"0.0")&"!"', t('1.5!'))
    add('composition-maxmin', '=MAXIFS(C1:C6,D1:D6,"b")-MINIFS(C1:C6,D1:D6,"b")', n(10.0))
    add('propagation-log-text', '=1+LOG("a",2)', e('#VALUE!'))
    add('propagation-days-text', '=DAYS("x",E1)', e('#VALUE!'))

    # ---- dynamic arrays and aggregation: SORT/SORTBY/UNIQUE/FILTER/AGGREGATE/ISREF ----
    def num_cell(value: float) -> tuple[str, float]:
        return ('N', float(value))

    def text_cell(value: str) -> tuple[str, str]:
        return ('T', value)

    def grid_value(rows_of_cells) -> tuple[str, str]:
        encoded = ';'.join(','.join(encode_cell(cell) for cell in row) for row in rows_of_cells)
        return ('G', encoded)

    def encode_cell(cell) -> str:
        kind = cell[0]
        if kind == 'N':
            return 'N:' + repr(float(cell[1]))
        if kind == 'T':
            return 'T:' + cell[1].replace('\\', '\\\\').replace(',', '\\,').replace(';', '\\;')
        if kind == 'B':
            return 'B:' + ('true' if cell[1] else 'false')
        if kind == 'E':
            return 'E:' + cell[1]
        return 'BLANK'

    def add_grid(case_id: str, formula: str, rows_of_cells):
        out.append((case_id, formula, grid_value(rows_of_cells), 0.0))

    b_column = [NUMBERS[f'B{i}'] for i in range(1, 7)]
    c_column = [NUMBERS[f'C{i}'] for i in range(1, 7)]
    d_column = [TEXTS[f'D{i}'] for i in range(1, 6)]

    def sort_units(units, key_index, order):
        return sorted(units, key=lambda unit: unit[key_index] if isinstance(unit[key_index], float) else unit[key_index].lower()) if order == 1 else \
               sorted(units, key=lambda unit: unit[key_index] if isinstance(unit[key_index], float) else unit[key_index].lower(), reverse=True)

    add_grid('grid-sort-numbers', '=SORT(B1:B6)', [[num_cell(u[0])] for u in sort_units([[v] for v in b_column], 0, 1)])
    add_grid('grid-sort-numbers-desc', '=SORT(B1:B6,1,-1)', [[num_cell(u[0])] for u in sort_units([[v] for v in b_column], 0, -1)])
    add_grid('grid-sort-text', '=SORT(D1:D5)', [[text_cell(u[0])] for u in sort_units([[v] for v in d_column], 0, 1)])
    add_grid('grid-sort-desc-text', '=SORT(D1:D5,1,-1)', [[text_cell(u[0])] for u in sort_units([[v] for v in d_column], 0, -1)])
    add_grid('grid-sort-by-col', '=SORT(B1:B3,1,1,TRUE)', [[num_cell(u[0])] for u in sort_units([[v] for v in b_column[:3]], 0, 1)])
    add('grid-sort-index-zero', '=SORT(B1:B6,0)', e('#VALUE!'))
    add('grid-sort-bad-order', '=SORT(B1:B6,1,2)', e('#VALUE!'))
    add('grid-sort-index-past', '=SORT(B1:B3,2)', e('#VALUE!'))
    pairs = list(zip(b_column, c_column))
    add_grid('grid-sortby-desc', '=SORTBY(B1:B6,C1:C6,-1)',
             [[num_cell(pair[0])] for pair in sorted(pairs, key=lambda pair: pair[1], reverse=True)])
    add_grid('grid-sortby-asc', '=SORTBY(B1:B6,C1:C6,1)',
             [[num_cell(pair[0])] for pair in sorted(pairs, key=lambda pair: pair[1])])
    add('grid-sortby-shape', '=SORTBY(B1:B6,C1:C5)', e('#VALUE!'))
    unique_b = list(dict.fromkeys(b_column))
    add_grid('grid-unique-numbers', '=UNIQUE(B1:B6)', [[num_cell(v)] for v in unique_b])
    add_grid('grid-unique-text', '=UNIQUE(D1:D5)', [[text_cell(v)] for v in dict.fromkeys(d_column)])
    once = [v for v in dict.fromkeys(d_column) if d_column.count(v) == 1]
    add_grid('grid-unique-once', '=UNIQUE(D1:D5,FALSE,TRUE)', [[text_cell(v)] for v in once])
    add_grid('grid-unique-once-one', '=UNIQUE(B1:B6,FALSE,TRUE)', [[num_cell(v)] for v in b_column if b_column.count(v) == 1])
    add('grid-unique-none-once', '=UNIQUE(B2:B6,FALSE,TRUE)', e('#N/A'))
    add_grid('grid-filter-gt', '=FILTER(C1:C6,C1:C6>25)', [[num_cell(v)] for v in c_column if v > 25])
    add('grid-filtilter-empty', '=FILTER(C1:C6,C1:C6>100)', e('#N/A'))
    add('grid-filter-default', '=FILTER(C1:C6,C1:C6>100,"none")', t('none'))
    add('grid-filter-shape', '=FILTER(C1:C6,B1:B3)', e('#VALUE!'))
    add('agg-sum', '=AGGREGATE(9,0,B1:B6)', n(sum(b_column)))
    add('agg-average', '=AGGREGATE(1,0,B1:B6)', n(sum(b_column) / len(b_column)))
    add('agg-max', '=AGGREGATE(4,0,B1:B6)', n(max(b_column)))
    add('agg-min', '=AGGREGATE(5,0,B1:B6)', n(min(b_column)))
    add('agg-count', '=AGGREGATE(2,0,B1:B6)', n(float(len(b_column))))
    add('agg-product', '=AGGREGATE(6,0,B1:B6)', n(float(__import__('math').prod(b_column))))
    add('agg-stdev', '=AGGREGATE(8,0,B1:B6)', n(statistics.stdev(b_column)), 1e-12)
    add('agg-varp', '=AGGREGATE(10,0,B1:B6)', n(statistics.pvariance(b_column)), 1e-12)
    add('agg-ignore-text-blank', '=AGGREGATE(9,0,A1:A6)', n(NUMBERS['A1'] + NUMBERS['A2']))
    add('agg-bad-function', '=AGGREGATE(20,0,B1:B6)', e('#VALUE!'))
    add('agg-bad-option', '=AGGREGATE(9,9,B1:B6)', e('#VALUE!'))
    add('isref-cell', '=ISREF(B1)', b(True))
    add('isref-range-single', '=ISREF(B1:B1)', b(True))
    add('isref-range-multi', '=ISREF(B1:B2)', b(False))
    add('isref-literal', '=ISREF(5)', b(False))
    add('isref-text', '=ISREF("B1")', b(False))

    # ---- AGGREGATE functions 12..19 and the sheet-level formula-source functions ----
    import math as _math

    def _sorted(values):
        return sorted(values)

    def _percentile_inc(values, fraction):
        ordered = _sorted(values)
        pos = fraction * (len(ordered) - 1)
        lo = int(_math.floor(pos))
        hi = int(_math.ceil(pos))
        return ordered[lo] + (pos - lo) * (ordered[hi] - ordered[lo])

    def _percentile_exc(values, fraction):
        ordered = _sorted(values)
        h = fraction * (len(ordered) + 1)
        j = int(_math.floor(h))
        frac = h - j
        if frac == 0:
            return ordered[j - 1]
        return ordered[j - 1] + frac * (ordered[min(j, len(ordered) - 1)] - ordered[j - 1])

    add('agg-median', '=AGGREGATE(12,0,B1:B6)', n(statistics.median(b_column)))
    add('agg-mode', '=AGGREGATE(13,0,B1:B6)', n(3.0))
    add('agg-large', '=AGGREGATE(14,0,B1:B6,2)', n(sorted(b_column, reverse=True)[1]))
    add('agg-small', '=AGGREGATE(15,0,B1:B6,2)', n(sorted(b_column)[1]))
    add('agg-large-out-of-range', '=AGGREGATE(14,0,B1:B6,9)', e('#NUM!'))
    add('agg-small-zero', '=AGGREGATE(15,0,B1:B6,0)', e('#NUM!'))
    add('agg-large-fraction', '=AGGREGATE(14,0,B1:B6,1.5)', e('#NUM!'))
    add('agg-percentile-inc', '=AGGREGATE(16,0,C1:C6,0.25)', n(_percentile_inc(c_column, 0.25)))
    add('agg-percentile-inc-mid', '=AGGREGATE(16,0,C1:C6,0.1)', n(_percentile_inc(c_column, 0.1)), 1e-12)
    add('agg-quartile-inc', '=AGGREGATE(17,0,C1:C6,3)', n(_percentile_inc(c_column, 0.75)), 1e-12)
    add('agg-quartile-inc-bad', '=AGGREGATE(17,0,C1:C6,5)', e('#NUM!'))
    add('agg-percentile-exc', '=AGGREGATE(18,0,C1:C6,0.25)', n(_percentile_exc(c_column, 0.25)), 1e-12)
    add('agg-percentile-exc-edge', '=AGGREGATE(18,0,C1:C6,0.01)', e('#NUM!'))
    add('agg-quartile-exc', '=AGGREGATE(19,0,C1:C6,2)', n(_percentile_exc(c_column, 0.5)), 1e-12)
    add('agg-quartile-exc-edge', '=AGGREGATE(19,0,C1:C6,4)', e('#NUM!'))
    add('agg-median-ignores-text', '=AGGREGATE(12,0,A1:A6)', n(0.5))
    add('agg-median-mode-arity', '=AGGREGATE(12,0,B1:B6,2)', e('#VALUE!'))

    # Sheet-level cases run inside a real evaluateSheet call so formula sources exist.
    add('sheet-formulatext-sum', '=FORMULATEXT(B7)', t('=SUM(B1:B6)'))
    add('sheet-formulatext-text', '=FORMULATEXT(B8)', t('=TEXT(A1,"0.0")'))
    add('sheet-formulatext-plain', '=FORMULATEXT(C7)', e('#N/A'))
    add('sheet-formulatext-literal', '=FORMULATEXT(5)', e('#VALUE!'))
    add('sheet-isformula-formula', '=ISFORMULA(B7)', b(True))
    add('sheet-isformula-plain', '=ISFORMULA(C7)', b(False))
    add('sheet-isformula-number', '=ISFORMULA(B1)', b(False))
    return out


KIND_ORDER = ('N', 'T', 'B', 'E', 'R')


def build(directory: Path):
    if (directory / 'cases.tsv').exists() or (directory / 'expected.tsv').exists():
        raise SystemExit('Refusing to overwrite existing fixtures in ' + str(directory))
    directory.mkdir(parents=True, exist_ok=True)
    cases = formula_cases()
    ids = [case[0] for case in cases]
    if len(ids) != len(set(ids)):
        raise SystemExit('duplicate case id')
    with open(directory / 'cases.tsv', 'w', encoding='utf-8') as handle:
        for case_id, formula, _expected, _tol in cases:
            handle.write(f'{case_id}\t{formula}\n')
    with open(directory / 'expected.tsv', 'w', encoding='utf-8') as handle:
        for case_id, _formula, expected, tol in cases:
            kwargs, value = expected
            handle.write(f'{case_id}\t{kwargs}\t{value}\t{tol}\n')
    print(f'R73 oracle cases: {len(cases)} formulas, independent expected values; host-only')


def read_tsv(path: Path, columns: tuple[str, ...]) -> list[dict[str, str]]:
    rows = []
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines()):
        if not line:
            continue
        fields = line.split('\t')
        if len(fields) != len(columns):
            raise ValueError(f'{path.name} line {index + 1}: expected {len(columns)} fields')
        rows.append(dict(zip(columns, fields)))
    return rows


def observations(path: Path) -> dict[str, str]:
    seen = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if not line:
            continue
        fields = line.split('|')
        if len(fields) != 3 or not fields[1] or not fields[2]:
            raise ValueError('malformed observation ' + line)
        case_id, scalar, grid = fields
        if case_id in seen:
            raise ValueError('duplicate observation ' + case_id)
        seen[case_id] = (scalar, grid)
    return seen


def matches(observed, expected: dict[str, str]) -> bool:
    kind, value = expected['kind'], expected['value']
    if kind == 'G':
        scalar, grid = observed
        return grid == value
    observed = observed[0]
    tol = float(expected['tolerance'])
    if kind == 'R':
        return observed.startswith('R:') and value in observed
    if kind == 'N':
        if not observed.startswith('N:'):
            return False
        try:
            actual = float(observed[2:])
        except ValueError:
            return False
        wanted = float(value)
        if tol == 0.0:
            return actual == wanted
        return abs(actual - wanted) <= tol * max(1.0, abs(wanted))
    if kind == 'T':
        return observed == 'T:' + value
    if kind == 'B':
        return observed == 'B:' + value
    if kind == 'E':
        return observed == 'E:' + value
    raise ValueError('unknown kind ' + kind)


def verify(directory: Path) -> int:
    cases = read_tsv(directory / 'cases.tsv', HEADER)
    expected_rows = read_tsv(directory / 'expected.tsv', ('id', 'kind', 'value', 'tolerance'))
    expected = {row['id']: row for row in expected_rows}
    if len(expected) != len(cases) or any(case['id'] not in expected for case in cases):
        raise SystemExit('expected.tsv does not cover cases.tsv')
    seen = observations(directory / 'candidate.txt')
    if set(seen) != {case['id'] for case in cases}:
        raise SystemExit('candidate observations do not match the case list')
    mismatches = [(case['id'], seen[case['id']], expected[case['id']])
                  for case in cases if not matches(seen[case['id']], expected[case['id']])]
    for case_id, actual, want in mismatches[:40]:
        print(f'MISMATCH {case_id}: got {actual[0]} grid={actual[1]} want {want["kind"]}:{want["value"]}')
    if mismatches:
        print(f'{len(mismatches)} mismatches out of {len(cases)} cases')
        return 1
    print(f'R73 essentials: {len(cases)} formulas matched the independent oracle; host-only')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('build', 'verify'))
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    if args.mode == 'build':
        build(args.directory)
    else:
        raise SystemExit(verify(args.directory))


if __name__ == '__main__':
    main()
