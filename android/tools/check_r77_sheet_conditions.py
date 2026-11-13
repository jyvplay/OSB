"""R77 independent verification of spreadsheet number-format conditions.

The production renderer is `dev.fusion.ooxml.NumberFormatRender` (Kotlin, host JVM). This module
re-derives every observation in `testing/build/r77conditions/artifacts/formats.tsv` from the
documented grammar with an implementation that shares no code with it, and it does two further
things a self-check cannot do:

1. **Control differential.** Rows whose format carries no condition are replayed through the
   Apache-2.0 `ssf` port of SheetJS and must agree exactly, so the harness itself is exercised
   against an outside implementation.
2. **Reference limitation experiment.** `ssf` ignores bracket conditions, so it cannot adjudicate
   the conditional rows. That claim is not asserted from memory: the pinned observations in
   `docs/verification/r77-condition-divergences.json` are re-executed here and must reproduce,
   which is what downgrades the conditional part of the claim to "specification-derived".

Exit code is non-zero unless every check passes; `--require-reference` makes a missing `ssf`
a failure instead of a silently weaker run.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RECORD = ROOT / 'docs/verification/r77-condition-divergences.json'
REFUSED = 'REFUSED'
MIN_ROWS = 40
MAX_OUTPUT = 4096
CLASSES = {'number', 'refusal'}
OPERATORS = ('<=', '>=', '<>', '=', '<', '>')
DECIMAL = re.compile(r'-?(\d+(\.\d*)?|\.\d+)')


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


# --------------------------------------------------------------------------------------------
# Grammar
# --------------------------------------------------------------------------------------------
def split_sections(fmt: str) -> list[str]:
    """Splits on `;` outside quotes, escapes and brackets (ECMA-376 section 18.8.31)."""
    out, current, i, quoted, depth = [], [], 0, False, 0
    while i < len(fmt):
        c = fmt[i]
        if quoted:
            current.append(c)
            if c == '"':
                quoted = False
            i += 1
            continue
        if c == '"':
            quoted = True
            current.append(c)
        elif c == '\\':
            require(i + 1 < len(fmt), 'dangling escape')
            current.append(fmt[i:i + 2])
            i += 2
            continue
        elif c == '[':
            depth += 1
            current.append(c)
        elif c == ']':
            depth -= 1
            require(depth >= 0, 'unbalanced bracket')
            current.append(c)
        elif c == ';' and depth == 0:
            out.append(''.join(current))
            current = []
        else:
            current.append(c)
        i += 1
    require(not quoted, 'unterminated quoted literal')
    require(depth == 0, 'unbalanced bracket')
    out.append(''.join(current))
    return out


def annotations(section: str):
    """Yields (kind, payload) for the leading bracket run; stops at the first other character."""
    i = 0
    while i < len(section) and section[i] == '[':
        end = section.find(']', i)
        require(end > i, 'unterminated bracket')
        yield section[i + 1:end].strip()
        i = end + 1


def condition_of(section: str) -> tuple[str, float] | None:
    found = None
    for inner in annotations(section):
        for op in OPERATORS:
            if inner.startswith(op):
                text = inner[len(op):].strip()
                if not DECIMAL.fullmatch(text):
                    raise Fail(f'malformed condition threshold in [{inner}]')
                threshold = float(text)
                require(found is None, f'two conditions in one section: {section!r}')
                found = (op, threshold)
                break
    return found


def holds(condition: tuple[str, float], value: float) -> bool:
    op, threshold = condition
    if op == '<':
        return value < threshold
    if op == '<=':
        return value <= threshold
    if op == '>':
        return value > threshold
    if op == '>=':
        return value >= threshold
    if op == '=':
        return value == threshold
    if op == '<>':
        return value != threshold
    raise Fail(f'unknown operator {op!r}')


def strip_annotations(section: str) -> str:
    """Removes conditions and colours, turns `[$€-407]` into the literal `€`, keeps `[h]`."""
    out, i = [], 0
    while i < len(section):
        c = section[i]
        if c == '"':
            end = section.find('"', i + 1)
            require(end > i, 'unterminated quoted literal')
            out.append(section[i:end + 1])
            i = end + 1
        elif c == '\\':
            out.append(section[i:i + 2])
            i += 2
        elif c != '[':
            out.append(c)
            i += 1
        else:
            end = section.find(']', i)
            require(end > i, 'unterminated bracket')
            inner = section[i + 1:end].strip()
            i = end + 1
            if condition_of('[' + inner + ']') is not None:
                continue
            if inner.upper() in COLOURS or inner.upper().startswith('COLOR'):
                continue
            if set(inner.lower()) == {'h'} or set(inner.lower()) == {'m'} or set(inner.lower()) == {'s'}:
                out.append('[' + inner + ']')
                continue
            if inner.startswith('$'):
                body = inner[1:]
                dash = body.rfind('-')
                symbol = body if dash < 0 else body[:dash]
                if dash >= 0:
                    require(body[dash + 1:].isdigit(), f'malformed locale id in [{inner}]')
                out.append('"' + symbol + '"' if symbol else '')
                continue
            raise Fail(f'unsupported annotation [{inner}]')
    return ''.join(out)


COLOURS = {
    'BLACK', 'BLUE', 'CYAN', 'GREEN', 'MAGENTA', 'RED', 'WHITE', 'YELLOW',
}


def select(sections: list[str], value: float) -> int:
    """Independent selection: build the candidate list, then take the first satisfied entry."""
    conditions = [condition_of(section) for section in sections]
    if all(c is None for c in conditions):
        if len(sections) == 1:
            return 0
        if len(sections) == 2:
            return 1 if value < 0 else 0
        return 0 if value > 0 else (1 if value < 0 else 2)
    declared = [(index, c) for index, c in enumerate(conditions) if c is not None]
    unconditional = [index for index, c in enumerate(conditions) if c is None]
    require(len(declared) <= 2, f'{len(declared)} conditions declared, at most 2 are defined')
    require(not unconditional or max(declared, key=lambda p: p[0])[0] < min(unconditional),
            'a condition cannot follow an unconditional section')
    matches = [index for index, c in declared if holds(c, value)]
    if matches:
        return matches[0]
    require(unconditional, 'no condition matched and every section carries one')
    return unconditional[-1]


# --------------------------------------------------------------------------------------------
# Template rendering (the subset this corpus uses)
# --------------------------------------------------------------------------------------------
def quantize(value: float, decimals: int) -> str:
    exponent = Decimal(1) if decimals == 0 else Decimal('1.' + '0' * decimals)
    return format(Decimal(repr(value)).quantize(exponent, rounding=ROUND_HALF_UP), 'f')


def group_integer(digits: str) -> str:
    out, count = [], 0
    for c in reversed(digits):
        out.append(c)
        count += 1
        if count % 3 == 0 and count != len(digits):
            out.append(',')
    return ''.join(reversed(out))


def render_template(template: str, value: float, auto_sign: bool) -> str:
    """Renders one section. Only literals, `#`/`0`/`?`, `.`, `,`, `%` and quotes are defined."""
    literals: list[tuple[int, str]] = []   # (pattern position, text)
    pattern: list[str] = []
    percent = False
    i = 0
    pending = ''

    def flush(pos: int) -> None:
        nonlocal pending
        if pending:
            literals.append((pos, pending))
            pending = ''

    while i < len(template):
        c = template[i]
        if c == '"':
            end = template.find('"', i + 1)
            require(end > i, 'unterminated quoted literal')
            pending += template[i + 1:end]
            i = end + 1
        elif c == '\\':
            require(i + 1 < len(template), 'dangling escape')
            pending += template[i + 1]
            i += 2
        elif c in '#0?':
            flush(len(pattern))
            pattern.append(c)
            i += 1
        elif c in '.,':
            flush(len(pattern))
            pattern.append(c)
            i += 1
        elif c in '_*':
            require(i + 1 < len(template), 'dangling fill character')
            pending += ' '
            i += 2
        elif c == '%':
            flush(len(pattern))
            percent = True
            pending += '%'
            i += 1
        elif c == '@':
            flush(len(pattern))
            pending += '@'
            i += 1
        else:
            pending += c
            i += 1
    flush(len(pattern))

    if not pattern:
        return ''.join(text for _, text in literals).replace('@', format(value, 'g'))

    text = ''.join(pattern)
    scale_commas = 0
    while text.endswith(','):
        scale_commas += 1
        text = text[:-1]
    integer_part, _, fraction_part = text.partition('.')
    grouping = ',' in integer_part
    integer_placeholders = integer_part.replace(',', '')
    decimals = fraction_part.count('0') + fraction_part.count('#') + fraction_part.count('?')

    magnitude = abs(value)
    if percent:
        magnitude *= 100
    for _ in range(scale_commas):
        magnitude /= 1000

    rendered = quantize(magnitude, decimals)
    whole, _, fraction = rendered.partition('.')
    minimum_integer = integer_placeholders.count('0')
    whole = whole.rjust(minimum_integer, '0') if minimum_integer else whole
    if not minimum_integer and whole == '0':
        whole = ''
    if grouping:
        whole = group_integer(whole)
    number = whole + ('.' + fraction if decimals else '')

    prefix = ''.join(text for pos, text in literals if pos == 0)
    suffix = ''.join(text for pos, text in literals if pos != 0)
    sign = ''
    if value < 0 and auto_sign and not prefix.endswith('-'):
        sign = '-'
    return sign + prefix + number + suffix


def rederive(value: float, fmt: str) -> str:
    sections = split_sections(fmt)
    require(len(sections) <= 4, f'{len(sections)} sections')
    chosen = select(sections, value)
    template = strip_annotations(sections[chosen])
    conditional = any(condition_of(s) is not None for s in sections)
    auto_sign = len(sections) == 1 and not conditional
    return render_template(template, value, auto_sign)


# --------------------------------------------------------------------------------------------
# Corpus, controls and the pinned reference experiment
# --------------------------------------------------------------------------------------------
def load_rows(path: Path) -> list[dict]:
    require(path.is_file(), f'missing observations at {path}')
    rows = []
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        require(bool(line.strip()), f'blank line {index}')
        fields = line.split('\t')
        require(len(fields) == 4, f'line {index}: {len(fields)} fields, expected 4')
        kind, value, fmt, rendered = fields
        require(kind in CLASSES, f'line {index}: unknown class {kind!r}')
        require(len(rendered) <= MAX_OUTPUT, f'line {index}: output too long')
        rows.append({'class': kind, 'value': float(value), 'format': fmt,
                     'rendered': rendered, 'line': index})
    return rows


def structural(rows: list[dict]) -> dict:
    require(len(rows) >= MIN_ROWS, f'only {len(rows)} observations, expected {MIN_ROWS}+')
    pairs = [(r['value'], r['format']) for r in rows]
    require(len(set(pairs)) == len(pairs), 'duplicate (value, format) observations')
    refusals = [r for r in rows if r['class'] == 'refusal']
    require(len(refusals) >= 6, f'only {len(refusals)} refusal observations')
    for row in rows:
        if row['class'] == 'refusal':
            require(row['rendered'] == REFUSED, f"line {row['line']}: refusal rendered {row['rendered']!r}")
        else:
            require(row['rendered'] != REFUSED, f"line {row['line']}: rendered row is a refusal")
    def has_condition(fmt: str) -> bool:
        try:
            return any(condition_of(section) is not None for section in split_sections(fmt))
        except Fail:
            # Shapes the grammar refuses still count as conditional when they carry a bracket
            # annotation; the refusal itself is checked by rederive_all.
            return '[' in fmt

    conditional = {r['format'] for r in rows if has_condition(r['format'])}
    controls = {r['format'] for r in rows if '[' not in r['format']}
    require(len(controls) >= 2, 'the corpus needs unconditional control formats')
    return {'rows': len(rows), 'refusals': len(refusals),
            'formats': len({r['format'] for r in rows}), 'conditional_formats': len(conditional),
            'control_formats': len(controls)}


def rederive_all(rows: list[dict]) -> dict:
    checked = 0
    for row in rows:
        if row['class'] == 'refusal':
            try:
                rendered = rederive(row['value'], row['format'])
            except Fail:
                checked += 1
                continue
            raise Fail(f"line {row['line']}: {row['format']!r} was expected to refuse but re-derives to {rendered!r}")
        expected = rederive(row['value'], row['format'])
        require(row['rendered'] == expected,
                f"line {row['line']}: render({row['value']}, {row['format']!r}) = {row['rendered']!r}, "
                f're-derivation says {expected!r}')
        checked += 1
    require(checked == len(rows), f'only {checked} of {len(rows)} rows were re-derived')
    return {'rederived': checked}


def load_reference():
    try:
        import ssf  # type: ignore
    except Exception as error:  # pragma: no cover
        raise Fail(f'the independent ssf reference is not importable: {error}') from error
    engine = ssf.SSF(locale='en-US')
    return lambda fmt, value: engine.format(fmt, value)


def control_differential(rows: list[dict], oracle) -> dict:
    compared = 0
    for row in rows:
        if '[' in row['format'] or row['class'] == 'refusal':
            continue
        reference = oracle(row['format'], row['value'])
        require(reference == row['rendered'],
                f"control {row['format']!r} at {row['value']}: ours {row['rendered']!r}, ssf {reference!r}")
        compared += 1
    require(compared >= 5, f'only {compared} control rows were compared with the reference')
    return {'control_rows_compared': compared}


def reference_limitation(record: dict, oracle) -> dict:
    pinned = record.get('reference_pinned_observations')
    require(isinstance(pinned, list) and len(pinned) >= 3, 'the record needs pinned reference observations')
    for entry in pinned:
        actual = oracle(entry['format'], float(entry['value']))
        require(actual == entry['reference'],
                f"reference changed for {entry['format']!r}: recorded {entry['reference']!r}, now {actual!r}")
    return {'pinned_reference_observations': len(pinned)}


def verify(directory: Path, require_reference: bool, oracle=None, record=None) -> dict:
    rows = load_rows(directory / 'formats.tsv')
    shape = structural(rows)
    derived = rederive_all(rows)
    record_data = json.loads(RECORD.read_text(encoding='utf-8')) if record is None else record
    require(record_data.get('schema', '').startswith('r77-'), 'unexpected record schema')
    if oracle is None:
        try:
            oracle = load_reference()
        except Fail:
            if require_reference:
                raise
            oracle = None
    report = dict(shape, **derived)
    if oracle is None:
        report['control_differential'] = 'unavailable (ssf missing; claim downgraded)'
        report['reference_limitation'] = 'unavailable'
    else:
        report.update(control_differential(rows, oracle))
        report.update(reference_limitation(record_data, oracle))
    return report


def summarise(report: dict) -> str:
    return (
        f"R77 conditions: {report['rows']} observations over {report['formats']} formats "
        f"({report['conditional_formats']} conditional, {report['control_formats']} control), "
        f"{report['refusals']} declared refusals; {report['rederived']} re-derived independently"
        + (f"; {report['control_rows_compared']} control rows match ssf exactly and "
           f"{report['pinned_reference_observations']} pinned observations reproduce ssf's "
           f"ignoring of bracket conditions" if 'control_rows_compared' in report else '')
        + '. Specification-derived: Excel did not run, so conditional formatting of real cells is not claimed.'
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-reference', action='store_true')
    args = parser.parse_args(argv)
    try:
        report = verify(args.artifacts, args.require_reference)
    except Fail as error:
        print(f'R77 condition verification FAILED: {error}', file=sys.stderr)
        return 1
    print(summarise(report))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
