#!/usr/bin/env python3
"""Independent verification for the R76 spreadsheet number/date/text format observations.

The production renderer (`NumberFormatRender`) is exercised by the R76 Kotlin probe, which writes
every observation to ``formats.tsv``. This file verifies those observations twice, by two different
mechanisms, and refuses to pass on either alone:

1. **Re-derivation** (always on, no third-party code): a small, format-specific oracle written from
   the ECMA-376 §18.8.31 semantics and Microsoft's published examples. It recomputes scale commas,
   elapsed-time totals, the 1900 serial-date mapping (including day zero and the fictitious
   1900-02-29), sub-second rounding, and the refusal set, then compares exact strings.

2. **Differential against an independent implementation** (``--require-reference``): the same corpus
   is replayed through ``ssf``, the Apache-2.0 Python port of SheetJS's number-format engine, which
   shares no code or authorship with this repository. Every difference must be covered by an entry
   in ``docs/verification/r76-format-divergences.json``; an undeclared difference fails, and so does
   a declared difference that no longer occurs (a stale adjudication).

Host-only. Nothing here is Excel or LibreOffice recalculation evidence, and this script says so in
its output rather than implying parity.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIVERGENCES = ROOT / 'docs/verification/r76-format-divergences.json'
MIN_ROWS = 200
MAX_OUTPUT = 4096
REFUSED = 'REFUSED'
CLASSES = {'number', 'date', 'time', 'refusal'}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def load_rows(path: Path) -> list[dict]:
    require(path.is_file(), f'missing observations: {path}')
    rows = []
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        require(bool(line.strip()), f'blank line at {index}')
        fields = line.split('\t')
        require(len(fields) == 4, f'line {index} has {len(fields)} fields, expected 4')
        kind, value, fmt, rendered = fields
        require(kind in CLASSES, f'line {index}: unknown class {kind!r}')
        require(bool(fmt), f'line {index}: empty format')
        try:
            number = float(value)
        except ValueError as error:
            raise Fail(f'line {index}: value {value!r} is not a number') from error
        require(number == number and number not in (float('inf'), float('-inf')),
                f'line {index}: value {value!r} is not finite')
        require(len(rendered) <= MAX_OUTPUT, f'line {index}: output exceeds {MAX_OUTPUT} characters')
        rows.append({'class': kind, 'value': number, 'format': fmt, 'rendered': rendered, 'line': index})
    return rows


def structural(rows: list[dict]) -> dict:
    require(len(rows) >= MIN_ROWS, f'only {len(rows)} observations, expected at least {MIN_ROWS}')
    keys = [(r['value'], r['format']) for r in rows]
    require(len(set(keys)) == len(keys), 'duplicate (value, format) observations')
    for kind in sorted(CLASSES):
        require(any(r['class'] == kind for r in rows), f'no observations in class {kind!r}')
    for row in rows:
        if row['class'] == 'refusal':
            require(row['rendered'] == REFUSED,
                    f"line {row['line']}: refusal class rendered {row['rendered']!r}")
        else:
            require(row['rendered'] != REFUSED,
                    f"line {row['line']}: {row['format']!r} refused inside the {row['class']} class")
    eur = [r for r in rows if '€' in r['format']]
    require(eur, 'no euro currency annotation survived the probe (encoding regression)')
    require(all('€' in r['rendered'] for r in eur), 'euro annotation did not render the euro sign')
    return {
        'rows': len(rows),
        'refused': sum(1 for r in rows if r['rendered'] == REFUSED),
        'classes': {k: sum(1 for r in rows if r['class'] == k) for k in sorted(CLASSES)},
    }


# --------------------------------------------------------------------------------------------
# 1. Re-derivation oracle
# --------------------------------------------------------------------------------------------
EXCEL_EPOCH = date(1899, 12, 30)
WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September',
          'October', 'November', 'December']


def excel_date(serial: int) -> tuple[int, int, int, int]:
    """(year, month, day, weekday) in Excel's 1900 system, weekday 0 = Sunday."""
    weekday = (serial - 1) % 7
    if serial == 60:
        return 1900, 2, 29, weekday
    if serial == 0:
        return 1900, 1, 0, weekday
    if 1 <= serial <= 59:
        d = date(1899, 12, 31) + timedelta(days=serial)
        return d.year, d.month, d.day, weekday
    d = EXCEL_EPOCH + timedelta(days=serial)
    return d.year, d.month, d.day, weekday


def round_half_up(x: float) -> int:
    from decimal import Decimal, ROUND_HALF_UP
    return int(Decimal(repr(x)).quantize(Decimal('1'), rounding=ROUND_HALF_UP))


REFUSAL_FORMATS = {'[>100]General', '[>=1000]#,##0;[<1000]0', '00:00', '0:00', 'ss.0000', '[hx]:mm'}


class NoRule(Exception):
    """The corpus row exercises a format this re-derivation does not model (counted, not hidden)."""


def quantize(value: float, decimals: int) -> str:
    from decimal import Decimal, ROUND_HALF_UP
    exp = '1' if decimals == 0 else '1.' + '0' * decimals
    return str(Decimal(repr(value)).quantize(Decimal(exp), rounding=ROUND_HALF_UP))


def grouped(text: str) -> str:
    sign = ''
    if text.startswith('-'):
        sign, text = '-', text[1:]
    whole, _, frac = text.partition('.')
    whole = f'{int(whole):,}'
    return sign + whole + ('.' + frac if frac else '')


def scientific(value: float, decimals: int, signed: bool, exp_width: int = 2) -> str:
    if value == 0:
        return ('0' + ('.' + '0' * decimals if decimals else '') + 'E' + ('+' if signed else '')
                + '0' * exp_width)
    from decimal import Decimal, ROUND_HALF_UP
    d = Decimal(repr(abs(value)))
    exponent = d.adjusted()
    mantissa = d.scaleb(-exponent).quantize(Decimal('1.' + '0' * decimals) if decimals else Decimal('1'),
                                            rounding=ROUND_HALF_UP)
    if mantissa >= 10:
        mantissa = mantissa.scaleb(-1).quantize(Decimal('1.' + '0' * decimals) if decimals else Decimal('1'),
                                                rounding=ROUND_HALF_UP)
        exponent += 1
    body = format(mantissa, 'f')
    sign = ('+' if exponent >= 0 else '-') if signed else ('-' if exponent < 0 else '')
    return f'{body}E{sign}{abs(exponent):0{exp_width}d}'


def _numeric(value: float, fmt: str) -> str:
    """Re-derives the numeric classes the corpus uses, from the documented placeholder rules."""
    if fmt == '0.00':
        return quantize(value, 2)
    if fmt == '0.000':
        return quantize(value, 3)
    if fmt == '000':
        return quantize(value, 0).zfill(3)
    if fmt == '???':
        return quantize(value, 0).rjust(3)
    if fmt in {'#,##0', '#,##0.00'}:
        decimals = 2 if fmt.endswith('.00') else 0
        return grouped(quantize(value, decimals))
    if fmt == '#,##0.0,,':
        return grouped(quantize(value / 1e6, 1))
    if fmt == '#,##0.000,,"M"':
        return grouped(quantize(value / 1e6, 3)) + 'M'
    if fmt == '#,##0,':
        return grouped(quantize(value / 1e3, 0))
    if fmt == '$#,##0.00':
        text = grouped(quantize(abs(value), 2))
        return ('-' if value < 0 else '') + '$' + text
    if fmt == '_($* #,##0.00_)':
        # The automatic minus is emitted before the section prefix, so a negative value is
        # "- $1,234.57 " (sign, then the `_(` width space, then the currency symbol).
        text = grouped(quantize(abs(value), 2))
        return ('-' if value < 0 else '') + ' $' + text + ' '
    if fmt == '#,##0.00_);(#,##0.00)':
        if value < 0:
            return '(' + grouped(quantize(abs(value), 2)) + ')'
        return grouped(quantize(value, 2)) + ' '
    if fmt == '#,##0.00_);[Red](#,##0.00)':
        if value < 0:
            return '(' + grouped(quantize(abs(value), 2)) + ')'
        return grouped(quantize(value, 2)) + ' '
    if fmt == '#.##':
        # `#` suppresses a leading zero and never pads, but the separator itself is kept.
        text = quantize(value, 2).rstrip('0')
        whole, _, frac = text.partition('.')
        return ('' if whole == '0' else whole) + '.' + frac
    if fmt == '0.0%':
        return quantize(value * 100, 1) + '%'
    if fmt == '#,###.00%':
        return quantize(value * 100, 2) + '%'
    if fmt == '0%':
        return quantize(value * 100, 0) + '%'
    if fmt in {'0.00E+00', '0.00E-00', '0.0E+0'}:
        decimals = 1 if fmt.startswith('0.0E') else 2
        exp_width = len(fmt.split('E')[1].lstrip('+-'))
        return scientific(value, decimals, signed='+' in fmt, exp_width=exp_width)
    if fmt == '"Total: "0.00':
        return 'Total: ' + quantize(value, 2)
    if fmt == '0" units"':
        return quantize(value, 0) + ' units'
    if fmt == '"X"0"Y"':
        return 'X' + quantize(value, 0) + 'Y'
    if fmt == '[$-409]0.00':
        return quantize(value, 2)
    if fmt == '[$-409]#,##0':
        return grouped(quantize(value, 0))
    if fmt == '[Blue]#,##0.00':
        return grouped(quantize(value, 2))
    if fmt == '[$€-407]#,##0.00':
        return '€' + grouped(quantize(value, 2))
    if fmt == '@':
        return general_text(value)
    if fmt == 'General':
        return general_text(value)
    if fmt == '0.00;0.00':
        return quantize(abs(value), 2)
    raise NoRule(fmt)


def general_text(value: float) -> str:
    """Excel's `General`: plain decimal while it fits eleven characters, else six significant digits."""
    from decimal import Decimal, ROUND_HALF_UP
    if value == 0:
        return '0'
    decimal = Decimal(repr(value))
    plain = format(decimal.normalize(), 'f')
    if len(plain) <= 11:
        return plain
    exponent = decimal.adjusted()
    mantissa = decimal.scaleb(-exponent).quantize(Decimal('1.00000'), rounding=ROUND_HALF_UP)
    if abs(mantissa) >= 10:
        mantissa = mantissa.scaleb(-1).quantize(Decimal('1.00000'), rounding=ROUND_HALF_UP)
        exponent += 1
    body = format(mantissa.normalize(), 'f')
    return f'{body}E{"+" if exponent >= 0 else "-"}{abs(exponent):02d}'


def _rederive(value: float, fmt: str) -> str:
    if fmt in REFUSAL_FORMATS:
        raise ValueError('declared refusal: ' + fmt)
    if fmt == 'yyyy-mm-dd':
        if value < 0 or value > 2_958_465:
            raise ValueError('serial date outside the supported 1900 window')
        y, m, d, _ = excel_date(int(value))
        return f'{y:04d}-{m:02d}-{d:02d}'
    if fmt == 'm/d/yy':
        y, m, d, _ = excel_date(int(value))
        return f'{m}/{d}/{y % 100:02d}'
    if fmt == 'dddd':
        return WEEKDAYS[excel_date(int(value))[3]]
    if fmt == 'mmmmm':
        return MONTHS[excel_date(int(value))[1] - 1][0]
    if fmt in {'d-mmm-yy', '[$-409]d-mmm-yy'}:
        y, m, d, _ = excel_date(int(value))
        return f'{d}-{MONTHS[m - 1][:3]}-{y % 100:02d}'
    if fmt == 'dddd, mmmm d, yyyy':
        y, m, d, w = excel_date(int(value))
        return f'{WEEKDAYS[w]}, {MONTHS[m - 1]} {d}, {y:04d}'
    if fmt == 'mmm-yy':
        y, m, _d, _w = excel_date(int(value))
        return f'{MONTHS[m - 1][:3]}-{y % 100:02d}'
    if fmt == 'mmmm':
        return MONTHS[excel_date(int(value))[1] - 1]

    frac_digits = 2 if fmt.endswith('.00') else 3 if fmt.endswith('.000') else 0
    per_second = 10 ** frac_digits
    ticks = round_half_up(value * 86_400 * per_second)
    _days, ticks = divmod(ticks, 86_400 * per_second)
    seconds, fraction = divmod(ticks, per_second)
    hour, minute, second = seconds // 3600, seconds % 3600 // 60, seconds % 60
    if fmt == 'hh:mm':
        return f'{hour:02d}:{minute:02d}'
    if fmt == 'mm:ss':
        return f'{minute:02d}:{second:02d}'
    if fmt == 'h:mm:ss':
        return f'{hour}:{minute:02d}:{second:02d}'
    if fmt == 'h:mm:ss.00':
        return f'{hour}:{minute:02d}:{second:02d}.{fraction:02d}'
    if fmt == 'hh:mm:ss.00':
        return f'{hour:02d}:{minute:02d}:{second:02d}.{fraction:02d}'
    if fmt == 'mm:ss.000':
        return f'{minute:02d}:{second:02d}.{fraction:03d}'
    if fmt == 'h:mm AM/PM':
        shown = 12 if hour % 12 == 0 else hour % 12
        return f'{shown}:{minute:02d} {"AM" if hour < 12 else "PM"}'
    if fmt == 'A/P':
        return 'A' if hour < 12 else 'P'

    total = round_half_up(value * 86_400)
    bracket = re.match(r'^\[([hms]+)\]', fmt)
    if bracket and fmt in {'[h]:mm:ss', '[mm]:ss', '[hh]:mm'}:
        token, width = bracket.group(1)[0], len(bracket.group(1))
        head = {'h': total // 3600, 'm': total // 60, 's': total}[token]
        body = str(head).zfill(width)
        tail = fmt[len(bracket.group(0)):]
        if tail == ':mm:ss':
            return f'{body}:{total % 3600 // 60:02d}:{total % 60:02d}'
        if tail == ':ss':
            return f'{body}:{total % 60:02d}'
        if tail == ':mm':
            return f'{body}:{total % 3600 // 60:02d}'
    return _numeric(value, fmt)

    return _numeric(value, fmt)


def render_rederived(value: float, fmt: str) -> str:
    if fmt in REFUSAL_FORMATS:
        raise ValueError('declared refusal: ' + fmt)
    return _rederive(value, fmt)


def rederive(rows: list[dict]) -> dict:
    checked = skipped = 0
    for row in rows:
        try:
            expected = render_rederived(row['value'], row['format'])
        except NoRule:
            skipped += 1
            continue
        except ValueError as error:
            require(row['class'] == 'refusal',
                    f"line {row['line']}: {row['format']!r} was not rendered ({error}) but is not a declared refusal")
            require(row['rendered'] == REFUSED,
                    f"line {row['line']}: {row['format']!r} should refuse ({error})")
            checked += 1
            continue
        require(row['rendered'] == expected,
                f"line {row['line']}: render({row['value']}, {row['format']!r}) = "
                f"{row['rendered']!r}, re-derivation says {expected!r}")
        checked += 1
    require(checked >= 100, f'only {checked} rows were re-derived; the oracle has gone blind')
    return {'rederived': checked, 'not_rederived': skipped}


# --------------------------------------------------------------------------------------------
# 2. Differential against the independent ssf implementation
# --------------------------------------------------------------------------------------------
def load_divergences(path: Path = DIVERGENCES) -> dict:
    data = json.loads(path.read_text(encoding='utf-8'))
    require(isinstance(data.get('divergences'), list), 'divergence file needs a "divergences" list')
    for entry in data['divergences']:
        for field in ('id', 'formats', 'reason', 'reference'):
            require(field in entry, f'divergence entry missing {field}: {entry}')
        require(entry['formats'], f'divergence {entry["id"]} lists no formats')
    return data


def differential(rows: list[dict], oracle, divergences: dict) -> dict:
    declared = {}
    for entry in divergences['divergences']:
        for fmt in entry['formats']:
            declared.setdefault(fmt, []).append(entry['id'])
    used: set[str] = set()
    compared = agreed = 0
    for row in rows:
        if row['rendered'] == REFUSED:
            continue
        try:
            reference = oracle(row['format'], row['value'])
        except Exception as error:  # oracle limitation, not a candidate failure
            raise Fail(f"reference oracle failed on {row['format']!r}: {error}") from error
        compared += 1
        if reference == row['rendered']:
            agreed += 1
            continue
        entry_ids = declared.get(row['format'])
        require(bool(entry_ids),
                f"line {row['line']}: undeclared difference for {row['format']!r} at {row['value']}: "
                f"ours {row['rendered']!r}, reference {reference!r}")
        used.update(entry_ids)
    stale = sorted({entry['id'] for entry in divergences['divergences']} - used)
    require(not stale, f'stale divergences (declared but never observed): {stale}')
    return {'compared': compared, 'agreed': agreed, 'differences': compared - agreed,
            'divergences': sorted(used)}


def load_reference():
    try:
        import ssf  # type: ignore
    except Exception as error:  # pragma: no cover - environment dependent
        raise Fail(f'the independent ssf reference is not importable: {error}') from error
    engine = ssf.SSF(locale='en-US')

    def oracle(fmt: str, value: float) -> str:
        return engine.format(fmt, value)

    return oracle


def verify(artifacts: Path, require_reference: bool, oracle=None, divergence_table=None) -> dict:
    rows = load_rows(artifacts / 'formats.tsv')
    report = {'structure': structural(rows)}
    report['rederivation'] = rederive(rows)
    if oracle is None and require_reference:
        oracle = load_reference()
    if oracle is None:
        report['differential'] = None
        report['claim'] = 're-derivation only (independent ssf reference not loaded)'
    else:
        table = divergence_table if divergence_table is not None else load_divergences()
        report['differential'] = differential(rows, oracle, table)
        report['claim'] = 're-derivation plus an independent implementation, with adjudicated differences'
    return report


def summarise(report: dict) -> str:
    structure = report['structure']
    lines = [
        f"R76 sheet formats: {structure['rows']} observations "
        f"({structure['refused']} declared refusals; classes {structure['classes']}).",
        f"{report['rederivation']['rederived']} rows re-derived from the 1900 date system and "
        f"elapsed-time rules (independent of the Kotlin implementation).",
    ]
    differential = report.get('differential')
    if differential is None:
        lines.append('No differential reference was loaded: this run verifies re-derivation only.')
    else:
        lines.append(
            f"{differential['compared']} rows replayed through the independent ssf implementation: "
            f"{differential['agreed']} identical, {differential['differences']} adjudicated "
            f"difference(s) {differential['divergences']}."
        )
    lines.append('Not Excel or LibreOffice recalculation evidence; no spreadsheet application ran.')
    return '\n'.join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path, help='directory containing formats.tsv')
    parser.add_argument('--require-reference', action='store_true',
                        help='fail instead of downgrading when the ssf reference is unavailable')
    parser.add_argument('--json', action='store_true', help='emit the report as JSON')
    args = parser.parse_args(argv)
    try:
        report = verify(args.artifacts, args.require_reference)
    except Fail as error:
        print(f'R76 FORMAT CHECK FAILED: {error}', file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print(summarise(report))
    if report.get('differential') is None and args.require_reference:
        print('R76 FORMAT CHECK FAILED: reference required but not loaded', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
