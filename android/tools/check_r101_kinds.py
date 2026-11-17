#!/usr/bin/env python3
"""Independent verification for R101: the last six conditional-format rule kinds.

Four lanes, none of them an application:

1. **raw OPC** — every `<cfRule>` in `kinds.tsv` is found by `sqref` and `priority` in `r101.xlsx` and its
   attributes are compared one by one: `type`, `timePeriod`, the presence or absence of `dxfId`, the absence of a
   `<formula>` on the kinds that state no formula, and — for an `iconSet` — its `<iconSet>` child: the set name,
   the three flags *including their absence* when the schema default applies, and the exact `<cfvo>` boundaries
   (count, type, value);
2. **openpyxl 3.1.5** — the same twenty rules through a real reader (`rule.timePeriod`, `rule.iconSet.iconSet`,
   `.showValue`, `.percent`, `.reverse`, `.cfvo[]`), plus the *documented refusal*: openpyxl's `ST_TimePeriod`
   enumeration carries only the ten presets its own user interface offers, so it raises on a workbook holding
   `thisYear` or `firstQuarter`. That limitation is asserted, not hidden;
3. **the presets** — `r101-presets.xlsx` must carry all seventeen presets, in enum order, one per rule, at the raw
   XML level (the only lane that can read them all);
4. **the evaluator** — `verdicts.tsv` is recomputed in Python from `rules.tsv`, `values.tsv` and `calendar.tsv`:
   the blank and error predicates, three icon sets (including a five-icon set and a reversed one) and ten
   `timePeriod` presets, with dates converted from serials by `datetime` rather than by this project's own
   arithmetic, so a shared bug in serial arithmetic cannot hide.

Host-only. No Excel, LibreOffice, renderer or device ran: an icon index is a claim about which icon a value earns,
never about pixels.

Utility: `python3 tools/check_r101_kinds.py testing/build/r101/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import math
import sys
import zipfile
from datetime import date, timedelta
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
KIND_FIELDS = 10
RULE_FIELDS = 8
VALUE_FIELDS = 3
VERDICT_FIELDS = 3
CALENDAR_FIELDS = 4

PRESETS = [
    'today', 'yesterday', 'tomorrow', 'last7Days', 'thisMonth', 'lastMonth', 'nextMonth',
    'thisWeek', 'lastWeek', 'nextWeek', 'thisYear', 'lastYear', 'nextYear',
    'firstQuarter', 'secondQuarter', 'thirdQuarter', 'fourthQuarter',
]
# openpyxl 3.1.5's own ST_TimePeriod enumeration: exactly the ten presets its UI offers.
OPENPYXL_PRESETS = {
    'today', 'yesterday', 'tomorrow', 'last7Days', 'thisMonth', 'lastMonth', 'nextMonth',
    'thisWeek', 'lastWeek', 'nextWeek',
}
ATTRIBUTE_ONLY = {
    'containsBlanks', 'notContainsBlanks', 'containsErrors', 'notContainsErrors',
    'timePeriod', 'duplicateValues', 'uniqueValues', 'aboveAverage', 'containsText',
    'notContainsText', 'beginsWith', 'endsWith', 'top10',
}
MAX_RANGE_CELLS = 100_000
PHANTOM_SERIAL = 60


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
    require(rows, f'{path.name} is empty')
    return rows


def open_part(folder: Path, name: str, part: str) -> ElementTree.Element:
    require((folder / name).is_file(), f'missing workbook {name}')
    with zipfile.ZipFile(folder / name) as archive:
        return ElementTree.fromstring(archive.read(part))


def rule_nodes(folder: Path, name: str) -> dict[str, dict[int, ElementTree.Element]]:
    root = open_part(folder, name, 'xl/worksheets/sheet1.xml')
    blocks: dict[str, dict[int, ElementTree.Element]] = {}
    for node in root.findall(f'{S}conditionalFormatting'):
        rules = {}
        for rule in node.findall(f'{S}cfRule'):
            priority = rule.get('priority')
            require(priority is not None and priority.isdigit(), f'{name}: a rule without an integer priority')
            rules[int(priority)] = rule
        blocks[node.get('sqref')] = rules
    return blocks


def on_off(value: str | None) -> str:
    return '-' if value is None else ('1' if value in ('1', 'true') else '0')


def boundaries(raw: str) -> list[tuple[str, float | None]]:
    """`percent:33.0;num:6` -> [(kind, value)]; `-` -> []."""
    if raw == '-':
        return []
    out = []
    for piece in raw.split(';'):
        if ':' in piece:
            kind, value = piece.split(':')
            out.append((kind, float(value)))
        else:
            out.append((piece, None))
    return out


# ------------------------------------------------------------------------------ lanes 1 and 3: raw OPC

def check_raw(folder: Path, rows: list[list[str]]) -> int:
    blocks = rule_nodes(folder, 'r101.xlsx')
    # A table that describes fewer rules than the file holds verifies less than it claims, so completeness is
    # asserted here rather than assumed: every rule in the package must be named by a row.
    in_file = {(sqref, priority) for sqref, rules in blocks.items() for priority in rules}
    described = {(row[0], int(row[1])) for row in rows}
    require(len(described) == len(rows), 'kinds.tsv names the same (sqref, priority) twice')
    require(in_file == described, (
        f'{len(in_file)} rule(s) in r101.xlsx but {len(described)} described; uncovered: '
        f'{sorted(in_file - described)}, described but absent: {sorted(described - in_file)}'))
    for sqref, priority, kind, period, icons, show, percent, reverse, bounds, token in rows:
        priority = int(priority)
        require(sqref in blocks, f'no conditionalFormatting block for {sqref}')
        require(priority in blocks[sqref], f'{sqref}: no cfRule with priority {priority}')
        node = blocks[sqref][priority]
        require(node.get('type') == kind, f'{sqref}/{priority}: type {node.get("type")} != {kind}')
        if kind == 'timePeriod':
            require(node.get('timePeriod') == period, (
                f'{sqref}/{priority}: timePeriod {node.get("timePeriod")} != {period}'))
        else:
            require(node.get('timePeriod') is None, f'{sqref}/{priority}: a {kind} rule wrote timePeriod')
        if kind in ATTRIBUTE_ONLY:
            require(len(list(node)) == 0, f'{sqref}/{priority}: a {kind} rule must carry attributes only')
        if kind == 'iconSet':
            child = node.find(f'{S}iconSet')
            require(child is not None, f'{sqref}/{priority}: an iconSet rule has no <iconSet> child')
            require(child.get('iconSet') == icons, (
                f'{sqref}/{priority}: iconSet name {child.get("iconSet")} != {icons}'))
            require(on_off(child.get('showValue')) == show, (
                f'{sqref}/{priority}: showValue {child.get("showValue")} != {show}'))
            require(on_off(child.get('percent')) == percent, (
                f'{sqref}/{priority}: percent {child.get("percent")} != {percent}'))
            require(on_off(child.get('reverse')) == reverse, (
                f'{sqref}/{priority}: reverse {child.get("reverse")} != {reverse}'))
            actual = [(cfvo.get('type'), None if cfvo.get('val') is None else float(cfvo.get('val')))
                      for cfvo in child.findall(f'{S}cfvo')]
            require(actual == boundaries(bounds), (
                f'{sqref}/{priority}: boundaries {actual} != {boundaries(bounds)}'))
            require(len(actual) == int(icons[0]) - 1, (
                f'{sqref}/{priority}: {icons} takes {int(icons[0]) - 1} boundaries, wrote {len(actual)}'))
        else:
            require(node.find(f'{S}iconSet') is None, f'{sqref}/{priority}: a {kind} rule wrote an <iconSet>')

        dxf = node.get('dxfId')
        if token == '-':
            require(dxf is None, f'{sqref}/{priority}: a format was declared as absent but dxfId={dxf}')
            require(kind not in ('cellIs', 'expression', 'top10'),
                    f'{sqref}/{priority}: a {kind} rule must carry a dxf')
        else:
            require(dxf is not None and dxf.isdigit(), f'{sqref}/{priority}: a format was declared with no dxfId')
    return len(rows)


def check_presets(folder: Path) -> int:
    blocks = rule_nodes(folder, 'r101-presets.xlsx')
    rules = blocks.get('A1:A9')
    require(rules is not None, 'r101-presets.xlsx has no A1:A9 block')
    require(len(rules) == len(PRESETS), f'{len(rules)} preset rules, expected {len(PRESETS)}')
    for index, preset in enumerate(PRESETS, start=1):
        node = rules.get(index)
        require(node is not None, f'preset rule {index} is missing')
        require(node.get('type') == 'timePeriod', f'preset {index} has type {node.get("type")}')
        require(node.get('timePeriod') == preset, (
            f'preset {index} is {node.get("timePeriod")}, expected {preset}'))
    return len(rules)


# ------------------------------------------------------------------------------ lane 2: openpyxl

def check_openpyxl(folder: Path, rows: list[list[str]]) -> int:
    import openpyxl
    require(openpyxl.__version__ == '3.1.5', f'expected openpyxl 3.1.5, found {openpyxl.__version__}')
    workbook = openpyxl.load_workbook(folder / 'r101.xlsx')
    require('AllKinds' in workbook.sheetnames, f'unexpected sheets: {workbook.sheetnames}')
    found = {}
    for block in workbook['AllKinds'].conditional_formatting:
        for rule in block.rules:
            found[rule.priority] = rule
    for sqref, priority, kind, period, icons, show, percent, reverse, bounds, token in rows:
        priority = int(priority)
        require(priority in found, f'openpyxl did not read priority {priority}')
        rule = found[priority]
        require(rule.type == kind, f'openpyxl type {rule.type} != {kind} for priority {priority}')
        if kind == 'timePeriod':
            require(rule.timePeriod == period, f'openpyxl timePeriod {rule.timePeriod} != {period}')
        if kind == 'iconSet':
            child = rule.iconSet
            require(child is not None, f'openpyxl read no <iconSet> for priority {priority}')
            require(child.iconSet == icons, f'openpyxl set {child.iconSet} != {icons}')
            expect = {'-': None, '1': True, '0': False}
            require(child.showValue == expect[show], (
                f'openpyxl showValue {child.showValue} != {expect[show]} for priority {priority}'))
            require(child.percent == expect[percent], f'openpyxl percent {child.percent} != {expect[percent]}')
            require(child.reverse == expect[reverse], f'openpyxl reverse {child.reverse} != {expect[reverse]}')
            actual = [(cfvo.type, cfvo.val) for cfvo in child.cfvo]
            want = [(kind_name, value) for kind_name, value in boundaries(bounds)]
            require(actual == want, f'openpyxl boundaries {actual} != {want} for priority {priority}')
        if token == '-':
            require(rule.dxfId is None, f'openpyxl dxfId {rule.dxfId} where none was declared')
    # The documented limitation: seven schema presets are outside openpyxl's enumeration, and it raises.
    try:
        openpyxl.load_workbook(folder / 'r101-presets.xlsx')
    except ValueError as error:
        require('could not read worksheets' in str(error), f'unexpected openpyxl failure: {error}')
    else:
        raise Fail('openpyxl read a workbook holding thisYear/firstQuarter, which its enumeration refuses')
    return len(rows)


# ------------------------------------------------------------------------------ lane 4: the evaluator

def column_index(letters: str) -> int:
    value = 0
    for char in letters:
        value = value * 26 + (ord(char.upper()) - ord('A') + 1)
    return value


def letters(index: int) -> str:
    text = ''
    while index > 0:
        index, remainder = divmod(index - 1, 26)
        text = chr(ord('A') + remainder) + text
    return text


REF = __import__('re').compile(r'^([A-Za-z]{1,3})([0-9]{1,7})$')


def refs(sqref: str) -> list[str]:
    out = []
    for token in sqref.split(' '):
        pieces = token.split(':')
        require(len(pieces) in (1, 2), f'unsupported range {token}')
        corners = []
        for piece in pieces:
            match = REF.match(piece)
            require(match is not None, f'unsupported cell reference {piece}')
            corners.append((column_index(match.group(1)), int(match.group(2))))
        (left, top), (right, bottom) = corners[0], corners[-1]
        for row in range(min(top, bottom), max(top, bottom) + 1):
            for col in range(min(left, right), max(left, right) + 1):
                out.append(f'{letters(col)}{row}')
    return out


def plain(value: float) -> str:
    if not math.isfinite(value):
        return repr(value)
    if value == math.floor(value) and abs(value) < 1e15:
        return str(int(value))
    return repr(float(value))


def text_of(value) -> str | None:
    if value is None:
        return ''
    kind, payload = value
    if kind == 't':
        return payload
    if kind == 'n':
        return plain(payload)
    if kind == 'b':
        return 'TRUE' if payload else 'FALSE'
    return None


def day_from_serial(serial: int, date1904: bool):
    """The civil date a whole-day serial names, or None when it names no real day."""
    if date1904:
        if not 0 <= serial <= 2_957_003:
            return None
        return date(1904, 1, 1) + timedelta(days=serial)
    if not 1 <= serial <= 2_958_465:
        return None
    if serial == PHANTOM_SERIAL:
        return None            # Excel's phantom 1900-02-29 is not a day
    if serial < PHANTOM_SERIAL:
        return date(1899, 12, 31) + timedelta(days=serial)
    return date(1899, 12, 30) + timedelta(days=serial)


def week_start(serial: int, start: str, date1904: bool) -> int | None:
    day = day_from_serial(serial, date1904)
    if day is None:
        return None
    offset = day.weekday() if start == 'monday' else (day.weekday() + 1) % 7
    return serial - offset


def percentile(sorted_values: list[float], percent: float) -> float:
    if not sorted_values:
        return 0.0
    position = max(0.0, min(1.0, percent / 100.0)) * (len(sorted_values) - 1)
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return sorted_values[lower]
    weight = position - lower
    return sorted_values[lower] * (1 - weight) + sorted_values[upper] * weight


def resolved_boundaries(rule, values) -> list[float] | None:
    numbers = sorted(
        value[1] for cell in rule['cells']
        if (value := values.get(cell)) is not None and value[0] == 'n'
    )
    if not numbers:
        return None
    low, high = numbers[0], numbers[-1]
    out = []
    for kind, value in rule['boundaries']:
        if kind == 'min':
            out.append(low)
        elif kind == 'max':
            out.append(high)
        elif kind == 'num':
            out.append(value)
        elif kind == 'percent':
            out.append(low + (high - low) * value / 100.0)
        elif kind == 'percentile':
            out.append(percentile(numbers, value))
        else:
            return None
    return out


def format_of(token: str) -> dict[str, object]:
    result = {'bold': False, 'italic': False, 'strike': False, 'underline': False, 'font': None, 'fill': None}
    if token == '-':
        return result
    for piece in token.split(';'):
        if piece in ('bold', 'italic', 'strike', 'underline'):
            result[piece] = True
        elif piece.startswith('font='):
            result['font'] = piece[5:]
        elif piece.startswith('fill='):
            result['fill'] = piece[5:]
        else:
            raise Fail(f'unparsable format token {piece}')
    return result


def in_period(period, cell: int, today: int, start: str, date1904: bool) -> bool:
    cell_day = day_from_serial(cell, date1904)
    today_day = day_from_serial(today, date1904)
    if cell_day is None or today_day is None:
        return False
    if period == 'today':
        return cell == today
    if period == 'yesterday':
        return cell == today - 1
    if period == 'tomorrow':
        return cell == today + 1
    if period == 'last7Days':
        return today - 6 <= cell <= today
    if period == 'thisMonth':
        return (cell_day.year, cell_day.month) == (today_day.year, today_day.month)
    if period == 'lastMonth':
        return (cell_day.year * 12 + cell_day.month) == (today_day.year * 12 + today_day.month) - 1
    if period == 'nextMonth':
        return (cell_day.year * 12 + cell_day.month) == (today_day.year * 12 + today_day.month) + 1
    if period == 'thisYear':
        return cell_day.year == today_day.year
    if period == 'lastYear':
        return cell_day.year == today_day.year - 1
    if period == 'nextYear':
        return cell_day.year == today_day.year + 1
    if period in ('thisWeek', 'lastWeek', 'nextWeek'):
        cell_week = week_start(cell, start, date1904)
        today_week = week_start(today, start, date1904)
        if cell_week is None or today_week is None:
            return False
        delta = {'thisWeek': 0, 'lastWeek': -7, 'nextWeek': 7}[period]
        return cell_week == today_week + delta
    quarter = {
        'firstQuarter': 1, 'secondQuarter': 2, 'thirdQuarter': 3, 'fourthQuarter': 4,
    }.get(period)
    if quarter is None:
        raise Fail(f'unhandled time period {period}')
    return cell_day.year == today_day.year and (cell_day.month - 1) // 3 + 1 == quarter


def evaluate(rules, values, ref: str, calendar) -> str:
    fired = []
    for rule in sorted((rule for rule in rules if ref in rule['cells']), key=lambda rule: rule['priority']):
        kind = rule['kind']
        value = values.get(ref)
        if kind in ('containsBlanks', 'notContainsBlanks'):
            text = text_of(value)
            if text is None:
                holds = False          # the rule's argument errors, so nothing is applied
            else:
                empty = text.strip(' ') == ''
                holds = empty if kind == 'containsBlanks' else not empty
        elif kind in ('containsErrors', 'notContainsErrors'):
            is_error = value is not None and value[0] == 'e'
            holds = is_error if kind == 'containsErrors' else not is_error
        elif kind == 'iconSet':
            holds = True               # every numeric cell is "fired"; the icon index carries the answer
            if value is None or value[0] != 'n':
                holds = False
        elif kind == 'timePeriod':
            if calendar is None:
                raise Fail(f'{ref}/{rule["priority"]}: a timePeriod rule without a calendar')
            serial = math.floor(value[1]) if value is not None and value[0] == 'n' else None
            holds = serial is not None and in_period(
                rule['period'], serial, calendar['today'], calendar['weekStartsOn'], calendar['date1904'],
            )
        else:
            raise Fail(f'{ref}/{rule["priority"]}: {kind} is outside this lane')
        if holds:
            fired.append(rule)

    def icon_of(rulelist):
        for rule in rulelist:
            if rule['kind'] != 'iconSet':
                continue
            points = resolved_boundaries(rule, values)
            cell = values.get(ref)
            if points is None or cell is None or cell[0] != 'n':
                continue
            reached = sum(1 for point in points if cell[1] >= point)
            size = int(rule['iconSet'][0])
            index = size - 1 - reached if rule['reverse'] else reached
            return f'{rule["iconSet"]}:{index}'
        return '-'

    if not fired:
        return 'DECIDED|-|0|-|0;0;0;0;-;-|-'
    paint = [
        '1' if any(rule['format']['bold'] for rule in fired) else '0',
        '1' if any(rule['format']['italic'] for rule in fired) else '0',
        '1' if any(rule['format']['strike'] for rule in fired) else '0',
        '1' if any(rule['format']['underline'] for rule in fired) else '0',
        next((rule['format']['font'] for rule in fired if rule['format']['font']), '-'),
        next((rule['format']['fill'] for rule in fired if rule['format']['fill']), '-'),
    ]
    priorities = ','.join(str(rule['priority']) for rule in fired)
    return f'DECIDED|{priorities}|0|-|{";".join(paint)}|{icon_of(fired)}'


def check_verdicts(folder: Path) -> int:
    rules = []
    for sqref, priority, kind, period, icons, reverse, bounds, token in table(folder / 'rules.tsv', RULE_FIELDS):
        cells = refs(sqref)
        require(len(cells) <= MAX_RANGE_CELLS, f'{sqref} exceeds MAX_RANGE_CELLS')
        rules.append({
            'priority': int(priority),
            'kind': kind,
            'sqref': sqref,
            'cells': cells,
            'period': None if period == '-' else period,
            'iconSet': None if icons == '-' else icons,
            'reverse': reverse == '1',
            'boundaries': boundaries(bounds),
            'format': format_of(token),
        })

    values = {}
    for ref, kind, payload in table(folder / 'values.tsv', VALUE_FIELDS):
        if kind == 'n':
            values[ref] = ('n', float(payload))
        elif kind == 't':
            values[ref] = ('t', payload)
        elif kind == 'b':
            values[ref] = ('b', payload == '1')
        else:
            values[ref] = ('e', payload)

    calendars = {}
    for name, today, date1904, start in table(folder / 'calendar.tsv', CALENDAR_FIELDS):
        calendars[name] = {
            'today': int(today),
            'date1904': date1904.lower() == 'true',
            'weekStartsOn': start,
        }
    require(calendars.get('monday', {}).get('weekStartsOn') == 'monday', 'the Monday calendar is missing')

    mismatches = []
    rows = table(folder / 'verdicts.tsv', VERDICT_FIELDS)
    for ref, calendar_name, recorded in rows:
        calendar = calendars.get(calendar_name)
        if calendar_name == 'none':
            try:
                evaluate(rules, values, ref, None)
                mismatches.append(f'{ref}: a timePeriod rule without a calendar decided')
            except Fail:
                pass
            if not recorded.startswith('UNDECIDED'):
                mismatches.append(f'{ref}: recorded {recorded!r} without a calendar')
            continue
        if calendar is None:
            mismatches.append(f'{ref}: unknown calendar {calendar_name}')
            continue
        try:
            computed = evaluate(rules, values, ref, calendar)
        except Fail as error:
            mismatches.append(str(error))
            continue
        if computed != recorded:
            mismatches.append(f'{ref}/{calendar_name}: kotlin {recorded!r} python {computed!r}')
    require(not mismatches, f'{len(mismatches)} verdict mismatches, first: {mismatches[:4]}')
    require(len(rows) == 51, f'expected 51 verdict rows, found {len(rows)}')
    return len(rows)


def verify(folder: Path, reader: bool = True) -> tuple[int, int, int, int]:
    kinds = table(folder / 'kinds.tsv', KIND_FIELDS)
    raw = check_raw(folder, kinds)
    presets = check_presets(folder)
    if reader:
        read = check_openpyxl(folder, kinds)
    else:
        read = 0
    verdicts = check_verdicts(folder)
    return raw, presets, read, verdicts


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    reader = args.require_openpyxl
    if not reader:
        try:
            import openpyxl  # noqa: F401
            reader = True
        except ImportError:
            reader = False
    raw, presets, read, verdicts = verify(args.artifacts, reader)
    print(
        f'R101 check: {raw} raw rule(s), {presets} preset(s) in enum order, {read} through openpyxl, '
        f'{verdicts} verdict(s) recomputed')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Fail as failure:
        print(f'R101 check FAILED: {failure}', file=sys.stderr)
        raise SystemExit(1)
