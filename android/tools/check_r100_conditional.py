#!/usr/bin/env python3
"""Independent verification for R100 conditional-format predicates.

Three lanes, none of them an application:

1. **raw OPC** — every `<cfRule>` in `kinds.tsv`/`rules.tsv` is looked up by `sqref` and `priority` and its
   attributes are compared one by one: `type`, the `operator` token (which differs from the type for
   `notContainsText`), `text`, `aboveAverage`/`equalAverage`/`stdDev` *including their absence* when the schema
   default applies, the presence of `dxfId`, and the fact that a rule of one of the seven new kinds carries no
   `<formula>` child;
2. **dxfs** — every `dxfId` a rule points at is resolved in `xl/styles.xml` and its properties must equal the
   format token the probe declared (`fill=RRGGBB`, `bold`, `italic`, `strike`, `underline`);
3. **openpyxl 3.1.5** — the same tables through a real reader: `rule.type`, `rule.operator`, `rule.text`,
   `rule.priority`, `rule.dxfId`, `rule.aboveAverage`, `rule.equalAverage`, `rule.stdDev`;
4. **the evaluator** — `verdicts.tsv` is recomputed in Python from `rules.tsv` and `values.tsv` by an independent
   implementation of the same decision procedure, and every one of the 31 rows must match character for character.

Lane 4 is the one that matters most: it is the only check that would catch a *plausible* evaluation (a wildcard
matcher that anchors at the start, a standard deviation divided by `n-1`, a duplicate rule that treats the text
`"12.5"` and the number `12.5` as equal) rather than a mechanical one.

Host-only. No Excel, LibreOffice, renderer or device ran: nothing here says a cell was painted.

Utility: `python3 tools/check_r100_conditional.py testing/build/r100/artifacts [--require-openpyxl]`
"""
from __future__ import annotations

import argparse
import math
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
KIND_FIELDS = 8
RULE_FIELDS = 8
VALUE_FIELDS = 3
VERDICT_FIELDS = 2
TEXT_OPERATOR = {
    'containsText': 'containsText',
    'notContainsText': 'notContains',
    'beginsWith': 'beginsWith',
    'endsWith': 'endsWith',
}
TEXT_KINDS = set(TEXT_OPERATOR)
AVERAGE_KINDS = {'aboveAverage'}
DUPLICATE_KINDS = {'duplicateValues', 'uniqueValues'}
SELF_PAINTED = {'colorScale', 'dataBar'}
MODELLED = (
    {'cellIs', 'expression', 'top10'}
    | TEXT_KINDS
    | AVERAGE_KINDS
    | DUPLICATE_KINDS
    | SELF_PAINTED
)
MAX_RANGE_CELLS = 100_000


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


def sheet_parts(folder: Path, name: str) -> dict[str, str]:
    with zipfile.ZipFile(folder / name) as archive:
        book = ElementTree.fromstring(archive.read('xl/workbook.xml'))
        rels = ElementTree.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
        targets = {item.get('Id'): item.get('Target') for item in rels}
        parts = {}
        for sheet in book.find(f'{S}sheets'):
            target = (targets[sheet.get(f'{R}id')] or '').lstrip('/')
            parts[sheet.get('name')] = target if target.startswith('xl/') else f'xl/{target}'
    return parts


def open_part(folder: Path, name: str, part: str) -> ElementTree.Element:
    with zipfile.ZipFile(folder / name) as archive:
        return ElementTree.fromstring(archive.read(part))


def dxf_tokens(folder: Path, name: str) -> list[str]:
    """The format of each `<dxf>`, encoded exactly as the probe encodes a [Format] token."""
    root = open_part(folder, name, 'xl/styles.xml')
    container = root.find(f'{S}dxfs')
    tokens = []
    for dxf in ([] if container is None else container.findall(f'{S}dxf')):
        parts = []
        font = dxf.find(f'{S}font')
        if font is not None:
            for tag in ('b', 'i', 'strike', 'u'):
                if font.find(f'{S}{tag}') is not None:
                    parts.append({'b': 'bold', 'i': 'italic', 'strike': 'strike', 'u': 'underline'}[tag])
            colour = font.find(f'{S}color')
            rgb = None if colour is None else colour.get('rgb')
            if rgb:
                parts.append(f'font={rgb[-6:].upper()}')
        fill = dxf.find(f'{S}fill')
        if fill is not None:
            pattern = fill.find(f'{S}patternFill')
            if pattern is not None:
                # Excel's convention for a solid dxf fill is fgColor for the colour and bgColor indexed=64.
                for tag in ('fgColor', 'bgColor'):
                    node = pattern.find(f'{S}{tag}')
                    rgb = None if node is None else node.get('rgb')
                    if rgb:
                        parts.append(f'fill={rgb[-6:].upper()}')
                        break
        tokens.append(';'.join(parts) if parts else '-')
    return tokens


def rule_nodes(folder: Path, name: str, part: str) -> dict[str, dict[int, ElementTree.Element]]:
    root = open_part(folder, name, part)
    blocks: dict[str, dict[int, ElementTree.Element]] = {}
    for node in root.findall(f'{S}conditionalFormatting'):
        sqref = node.get('sqref')
        rules: dict[int, ElementTree.Element] = {}
        for rule in node.findall(f'{S}cfRule'):
            priority = rule.get('priority')
            require(priority is not None and priority.isdigit(), f'{part}: a rule without an integer priority')
            rules[int(priority)] = rule
        blocks[sqref] = rules
    return blocks


def require_rule(block: dict[int, ElementTree.Element], sqref: str, priority: int) -> ElementTree.Element:
    require(sqref in block, f'no conditionalFormatting block for {sqref}')
    require(priority in block[sqref], f'{sqref}: no cfRule with priority {priority}')
    return block[sqref][priority]


def on_off(value: str | None) -> str:
    return '-' if value is None else ('1' if value in ('1', 'true', 'True') else '0')


def check_raw(folder: Path, name: str, part: str, rows: list[list[str]]) -> int:
    blocks = rule_nodes(folder, name, part)
    tokens = dxf_tokens(folder, name)
    checked = 0
    for sqref, priority, kind, text, above, equal, std_dev, token in rows:
        priority = int(priority)
        require(kind in MODELLED, f'{sqref}/{priority}: {kind} is not a modelled kind')
        node = require_rule(blocks, sqref, priority)
        require(node.get('type') == kind, f'{sqref}/{priority}: type {node.get("type")} != {kind}')
        if kind in TEXT_KINDS:
            require(node.get('operator') == TEXT_OPERATOR[kind], (
                f'{sqref}/{priority}: operator {node.get("operator")} != {TEXT_OPERATOR[kind]}'))
            require(node.get('text') == text, f'{sqref}/{priority}: text {node.get("text")} != {text}')
            require(node.findall(f'{S}formula') == [], f'{sqref}/{priority}: a text rule wrote a formula')
            require(node.get('aboveAverage') is None and node.get('equalAverage') is None
                    and node.get('stdDev') is None, f'{sqref}/{priority}: a text rule wrote an average attribute')
        else:
            require(node.get('text') is None, f'{sqref}/{priority}: a {kind} rule wrote text')
        if kind in AVERAGE_KINDS:
            require(on_off(node.get('aboveAverage')) == above, (
                f'{sqref}/{priority}: aboveAverage {node.get("aboveAverage")} != {above}'))
            require(on_off(node.get('equalAverage')) == equal, (
                f'{sqref}/{priority}: equalAverage {node.get("equalAverage")} != {equal}'))
            actual_std = node.get('stdDev') if node.get('stdDev') is not None else '-'
            require(actual_std == std_dev, f'{sqref}/{priority}: stdDev {actual_std} != {std_dev}')
            require(node.findall(f'{S}formula') == [], f'{sqref}/{priority}: an aboveAverage rule wrote a formula')
        if kind in DUPLICATE_KINDS:
            require(node.findall(f'{S}formula') == [], f'{sqref}/{priority}: a {kind} rule wrote a formula')
            require(len(list(node)) == 0, f'{sqref}/{priority}: a {kind} rule must carry attributes only')
        dxf = node.get('dxfId')
        if kind in SELF_PAINTED:
            require(token == '-', f'{sqref}/{priority}: a self-painted {kind} rule must not carry a format')
            require(dxf is None, f'{sqref}/{priority}: a self-painted {kind} rule must not point at a dxf')
        if token == '-':
            require(dxf is None, f'{sqref}/{priority}: a rule declared no format but points at dxf {dxf}')
            require(kind not in ('cellIs', 'expression', 'top10'),
                    f'{sqref}/{priority}: a {kind} rule must carry a dxf')
        else:
            require(dxf is not None and dxf.isdigit(), f'{sqref}/{priority}: a format was declared but no dxfId')
            index = int(dxf)
            require(index < len(tokens), f'{sqref}/{priority}: dxfId {index} beyond {len(tokens)} dxfs')
            require(tokens[index] == token, f'{sqref}/{priority}: dxf {index} is {tokens[index]}, expected {token}')
        checked += 1
    return checked


# ---------------------------------------------------------------------------- lane 3: openpyxl

def check_openpyxl(folder: Path, name: str, sheet: str, rows: list[list[str]]) -> int:
    try:
        import openpyxl
    except ImportError as error:  # pragma: no cover - the caller decides whether this is fatal
        raise Fail(f'openpyxl is not importable: {error}') from error
    require(openpyxl.__version__ == '3.1.5', f'expected openpyxl 3.1.5, found {openpyxl.__version__}')
    workbook = openpyxl.load_workbook(folder / name)
    require(sheet in workbook.sheetnames, f'{name} has no sheet {sheet}')
    found: dict[int, object] = {}
    spans: dict[int, str] = {}
    for block in workbook[sheet].conditional_formatting:
        for rule in block.rules:
            found[rule.priority] = rule
            spans[rule.priority] = str(block.sqref)
    require(found, f'{sheet} has no conditional formatting in {name}')
    checked = 0
    for sqref, priority, kind, text, above, equal, std_dev, token in rows:
        priority = int(priority)
        require(priority in found, f'{sheet}: openpyxl did not read priority {priority}')
        require(spans[priority] == sqref, f'{sheet}/{priority}: sqref {spans[priority]} != {sqref}')
        rule = found[priority]
        require(rule.type == kind, f'{sheet}/{priority}: openpyxl type {rule.type} != {kind}')
        if kind in TEXT_KINDS:
            require(rule.operator == TEXT_OPERATOR[kind], (
                f'{sheet}/{priority}: openpyxl operator {rule.operator} != {TEXT_OPERATOR[kind]}'))
            require(rule.text == text, f'{sheet}/{priority}: openpyxl text {rule.text!r} != {text!r}')
            require(not rule.formula, f'{sheet}/{priority}: openpyxl read a formula on a text rule')
        else:
            require(rule.text is None, f'{sheet}/{priority}: openpyxl read text on a {kind} rule')
        if kind in AVERAGE_KINDS:
            # openpyxl keeps an omitted ST_OnOff as None; the probe's `-` means exactly that.
            require(on_off(None if rule.aboveAverage is None else ('1' if rule.aboveAverage else '0')) == above,
                    f'{sheet}/{priority}: openpyxl aboveAverage {rule.aboveAverage} != {above}')
            require(on_off(None if rule.equalAverage is None else ('1' if rule.equalAverage else '0')) == equal,
                    f'{sheet}/{priority}: openpyxl equalAverage {rule.equalAverage} != {equal}')
            actual_std = '-' if rule.stdDev is None else str(rule.stdDev)
            require(actual_std == std_dev, f'{sheet}/{priority}: openpyxl stdDev {actual_std} != {std_dev}')
        if token == '-':
            require(rule.dxfId is None, f'{sheet}/{priority}: openpyxl read dxfId {rule.dxfId} where none was declared')
        else:
            require(rule.dxfId is not None, f'{sheet}/{priority}: openpyxl read no dxfId')
        checked += 1
    return checked


# ---------------------------------------------------------------------------- lane 4: the evaluator

def _have_openpyxl() -> bool:
    try:
        import openpyxl  # noqa: F401
    except ImportError:
        return False
    return True


def column_index(letters: str) -> int:
    value = 0
    for char in letters:
        value = value * 26 + (ord(char.upper()) - ord('A') + 1)
    return value


REF = re.compile(r'^([A-Za-z]{1,3})([0-9]{1,7})$')


def parse_range(text: str) -> tuple[tuple[int, int], tuple[int, int]]:
    pieces = text.split(':')
    require(len(pieces) in (1, 2), f'unsupported range {text}')
    corners = []
    for piece in pieces:
        match = REF.match(piece)
        require(match is not None, f'unsupported cell reference {piece}')
        corners.append((column_index(match.group(1)), int(match.group(2))))
    top, bottom = corners[0], corners[-1]
    return (min(top[0], bottom[0]), min(top[1], bottom[1])), (max(top[0], bottom[0]), max(top[1], bottom[1]))


def refs(sqref: str) -> list[str]:
    out = []
    for token in sqref.split(' '):
        (left, top), (right, bottom) = parse_range(token)
        for row in range(top, bottom + 1):
            for col in range(left, right + 1):
                out.append(f'{letters(col)}{row}')
    return out


def letters(index: int) -> str:
    text = ''
    while index > 0:
        index, remainder = divmod(index - 1, 26)
        text = chr(ord('A') + remainder) + text
    return text


def plain(value: float) -> str:
    """A number as Excel's general format would show it, matching the Kotlin `plainNumber`."""
    if not math.isfinite(value):
        return repr(value)
    if value == math.floor(value) and abs(value) < 1e15:
        return str(int(value))
    return repr(float(value))


def tokenise(pattern: str) -> str:
    """Excel's SEARCH grammar as a regular expression: `?` one character, `*` any run, `~` escapes."""
    out = []
    index = 0
    while index < len(pattern):
        char = pattern[index]
        if char == '~' and index + 1 < len(pattern):
            out.append(re.escape(pattern[index + 1]))
            index += 2
        elif char == '*':
            out.append('.*')
            index += 1
        elif char == '?':
            out.append('.')
            index += 1
        else:
            out.append(re.escape(char))
            index += 1
    return ''.join(out)


def contains(literal: str, text: str) -> bool:
    # `re.search` is the unanchored form of the Kotlin matcher's `fullmatch("*" + literal + "*")`.
    return re.search(tokenise(literal), text, re.IGNORECASE | re.DOTALL) is not None


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


def key_of(value) -> str | None:
    if value is None:
        return None
    kind, payload = value
    if kind == 'n':
        return 'n:' + plain(payload)
    if kind == 't':
        return 't:' + payload.lower()
    if kind == 'b':
        return 'b:' + ('true' if payload else 'false')
    return None


def format_of(token: str) -> dict[str, object]:
    result: dict[str, object] = {'bold': False, 'italic': False, 'strike': False, 'underline': False,
                                 'font': None, 'fill': None}
    if token == '-':
        return result
    for piece in token.split(';'):
        if piece == 'bold':
            result['bold'] = True
        elif piece == 'italic':
            result['italic'] = True
        elif piece == 'strike':
            result['strike'] = True
        elif piece == 'underline':
            result['underline'] = True
        elif piece.startswith('font='):
            result['font'] = piece[5:]
        elif piece.startswith('fill='):
            result['fill'] = piece[5:]
        else:
            raise Fail(f'unparsable format token {piece}')
    return result


def sigma(numbers: list[float]) -> float:
    """The population standard deviation, which is what `stdDev` is measured in here (divide by n, not n-1)."""
    mean = sum(numbers) / len(numbers)
    return math.sqrt(sum((item - mean) ** 2 for item in numbers) / len(numbers))


def evaluate(rules, values: dict[str, tuple[str, object]], ref: str) -> str:
    applicable = [rule for rule in rules if ref in rule['cells']]
    applicable.sort(key=lambda rule: rule['priority'])
    fired = []
    for rule in applicable:
        kind = rule['kind']
        value = values.get(ref)
        if kind in TEXT_KINDS:
            text = text_of(value)
            if text is None:
                raise Fail(f'{ref}/{rule["priority"]}: an error cell cannot be decided')
            if kind == 'containsText':
                holds = contains(rule['text'], text)
            elif kind == 'notContainsText':
                holds = not contains(rule['text'], text)
            elif kind == 'beginsWith':
                holds = text.lower().startswith(rule['text'].lower())
            else:
                holds = text.lower().endswith(rule['text'].lower())
        elif kind == 'aboveAverage':
            if value is not None and value[0] == 'e':
                raise Fail(f'{ref}/{rule["priority"]}: an error cell has no rank')
            numbers = []
            for cell in rule['cells']:
                current = values.get(cell)
                if current is not None and current[0] == 'n':
                    numbers.append(current[1])
            if not numbers or value is None or value[0] != 'n':
                holds = False
            else:
                mean = sum(numbers) / len(numbers)
                steps = rule['stdDev'] or 0
                band = 0.0 if steps == 0 else steps * sigma(numbers)
                inclusive = rule['equalAverage'] is True
                above = rule['aboveAverage'] is not False
                if above:
                    holds = value[1] >= mean + band if inclusive else value[1] > mean + band
                else:
                    holds = value[1] <= mean - band if inclusive else value[1] < mean - band
        elif kind in DUPLICATE_KINDS:
            key = key_of(value)
            if key is None:
                holds = False
            else:
                occurrences = sum(1 for cell in rule['cells'] if key_of(values.get(cell)) == key)
                holds = occurrences > 1 if kind == 'duplicateValues' else occurrences == 1
        else:
            raise Fail(f'{ref}/{rule["priority"]}: {kind} is outside this lane')
        if holds:
            fired.append(rule)
    if not fired:
        paint = '0;0;0;0;-;-'
        return f'DECIDED|-|0|-|{paint}'
    paint = [
        '1' if any(rule['format']['bold'] for rule in fired) else '0',
        '1' if any(rule['format']['italic'] for rule in fired) else '0',
        '1' if any(rule['format']['strike'] for rule in fired) else '0',
        '1' if any(rule['format']['underline'] for rule in fired) else '0',
        next((rule['format']['font'] for rule in fired if rule['format']['font']), '-'),
        next((rule['format']['fill'] for rule in fired if rule['format']['fill']), '-'),
    ]
    priorities = ','.join(str(rule['priority']) for rule in fired)
    return f'DECIDED|{priorities}|0|-|{";".join(paint)}'


def check_verdicts(folder: Path) -> int:
    rules = []
    for sqref, priority, kind, text, above, equal, std_dev, token in table(folder / 'rules.tsv', RULE_FIELDS):
        cells = refs(sqref)
        require(len(cells) <= MAX_RANGE_CELLS, f'{sqref} exceeds MAX_RANGE_CELLS')
        rules.append({
            'priority': int(priority),
            'kind': kind,
            'sqref': sqref,
            'cells': cells,
            'text': None if text == '-' else text,
            'aboveAverage': None if above == '-' else above == '1',
            'equalAverage': None if equal == '-' else equal == '1',
            'stdDev': None if std_dev == '-' else int(std_dev),
            'format': format_of(token),
        })
    require(len(rules) == 13, f'expected 13 verdict rules, found {len(rules)}')

    values: dict[str, tuple[str, object]] = {}
    for ref, kind, payload in table(folder / 'values.tsv', VALUE_FIELDS):
        if kind == 'n':
            values[ref] = ('n', float(payload))
        elif kind == 't':
            values[ref] = ('t', payload)
        elif kind == 'b':
            values[ref] = ('b', payload == '1')
        else:
            values[ref] = ('e', payload)

    recorded = {ref: encoded for ref, encoded in table(folder / 'verdicts.tsv', VERDICT_FIELDS)}
    scope = []
    for rule in rules:
        for cell in rule['cells']:
            if cell not in scope:
                scope.append(cell)
    require(len(recorded) == len(scope), f'{len(recorded)} verdict rows for {len(scope)} cells')
    mismatches = []
    for cell in scope:
        try:
            computed = evaluate(rules, values, cell)
        except Fail as error:
            mismatches.append(str(error))
            continue
        if recorded.get(cell) != computed:
            mismatches.append(f'{cell}: kotlin {recorded.get(cell)!r} python {computed!r}')
    require(not mismatches, f'{len(mismatches)} verdict mismatches, first: {mismatches[:4]}')
    # The four hypotheses this evaluator states are worth pinning: if any of them changes, the lane says which.
    require(contains('ap*e', 'pineapple') and not contains('a*a', 'apple'),
            'wildcard containment: the unanchored reading is broken')
    require(plain(12.5) == '12.5' and plain(1.0) == '1', 'number-to-text coercion is broken')
    require(key_of(('n', 12.5)) != key_of(('t', '12.5')), 'a number and its text must not share a duplicate key')
    require(key_of(('t', 'X')) == key_of(('t', 'x')), 'duplicate text keys must be case-insensitive')
    return len(scope)


def verify(folder: Path, reader: bool = True) -> tuple[int, int, int, int, int]:
    """Run every lane over [folder]; returns (raw kinds, raw rules, read kinds, read rules, verdicts).

    Split out of `main` so the negative controls in `tools/test_r100_conditional.py` can drive it directly.
    """
    kinds = table(folder / 'kinds.tsv', KIND_FIELDS)
    rules = table(folder / 'rules.tsv', RULE_FIELDS)
    parts = sheet_parts(folder, 'r100.xlsx')
    require('Kinds' in parts and 'Verdicts' in parts, f'unexpected sheets: {sorted(parts)}')

    raw_kinds = check_raw(folder, 'r100.xlsx', parts['Kinds'], kinds)
    raw_rules = check_raw(folder, 'r100.xlsx', parts['Verdicts'], rules)
    require(raw_kinds == 14, f'expected 14 kind rules, checked {raw_kinds}')

    if reader:
        read_kinds = check_openpyxl(folder, 'r100.xlsx', 'Kinds', kinds)
        read_rules = check_openpyxl(folder, 'r100.xlsx', 'Verdicts', rules)
    else:
        read_kinds = read_rules = 0

    verdicts = check_verdicts(folder)
    return raw_kinds, raw_rules, read_kinds, read_rules, verdicts


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)

    try:
        counts = verify(args.artifacts, reader=args.require_openpyxl or _have_openpyxl())
    except Fail as error:
        raise
    raw_kinds, raw_rules, read_kinds, read_rules, verdicts = counts
    print(
        f'R100 check: {raw_kinds} raw kind rule(s), {raw_rules} raw verdict rule(s), '
        f'{read_kinds} + {read_rules} through openpyxl, {verdicts} verdict(s) recomputed')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Fail as failure:
        print(f'R100 check FAILED: {failure}', file=sys.stderr)
        raise SystemExit(1)
